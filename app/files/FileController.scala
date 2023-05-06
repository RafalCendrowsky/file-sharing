package files

import akka.http.scaladsl.model.IllegalUriException
import akka.stream.alpakka.s3.S3Exception
import akka.stream.scaladsl.{FileIO, Keep, Sink}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import auth.AuthenticatedAction
import play.api.http.HttpEntity
import play.api.libs.json.{JsObject, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import repo.KeyValueStore

import java.nio.file.{Files, Path}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.io.FileOperationException
import scala.util.Success

class FileController @Inject()(
  cc: ControllerComponents,
  storageClient: StorageClient,
  store: KeyValueStore,
  authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext, materializer: Materializer) extends AbstractController(cc) {

  def upload: Action[MultipartFormData[Path]] = authenticatedAction.async(
    parse.multipartFormData(handleFilePart, maxLength = 2 * 1024 * 1024)
  ) { implicit request =>
    request.body.file("file").fold(
      Future.successful(BadRequest(Json.obj("status" -> BAD_REQUEST, "detail" -> "Missing file")))
    ) {
      case FilePart(_, filename, _, path, _, _) =>
        FileIO.fromPath(path).runWith(storageClient.multipartUpload(request.user, filename)).map {
          result => {
            Ok(Json.obj("status" -> OK, "key" -> result.getKey))
          }
        }
    }
  }

  def share(key: String): Action[AnyContent] = authenticatedAction.async { implicit request =>
    val user = request.user
    storageClient.list(user).runFold(Seq.empty[String]) { (acc, summary) =>
      acc :+ summary.getKey.split("/").last
    } map {
      case keys if keys.contains(key) =>
        val shareKey = UUID.randomUUID().toString
        store.set(shareKey, s"$user/$key", Some(60 * 60 * 24))
        Ok(Json.obj("status" -> "success", "key" -> shareKey, "detail" -> "File available for 24 hours"))
      case _ => NotFound(Json.obj("status" -> NOT_FOUND, "message" -> "File not found"))
    }
  }

  def download(key: String): Action[AnyContent] = authenticatedAction.async { implicit request =>
    download(request.user, key)
  }

  def downloadShared(key: String): Action[AnyContent] = Action.async { implicit request =>
    store.get(key).flatMap {
      case Some(value) =>
        val user = value.split("/").head
        val fileKey = value.split("/").last
        download(user, fileKey)
      case _ => Future.successful(
        NotFound(Json.obj("status" -> NOT_FOUND, "message" -> "File not found")))
    }
  }

  def delete(key: String): Action[AnyContent] = authenticatedAction.async { implicit request =>
    storageClient.delete(request.user, key).run().map { _ =>
      Ok(Json.obj("status" -> "success"))
    }.recover {
      recoverResult
    }
  }

  def list: Action[AnyContent] = authenticatedAction.async { implicit request =>
    storageClient.list(request.user).runFold(Seq.empty[JsObject]) { (acc, summary) =>
      acc :+ Json.obj("key" -> summary.getKey, "size" -> summary.getSize.toString)
    }.map { data =>
      Ok(Json.toJson(data))
    }.recover {
      recoverResult
    }
  }

  private def download(user: String, key: String): Future[Result] = {
    val objectSource = storageClient.download(user, key)

    val contentLengthFuture = objectSource.toMat(Sink.head)(Keep.left).run().map(_.getContentLength)

    contentLengthFuture.map { contentLength =>
      Result(
        header = ResponseHeader(OK, Map(CONTENT_LENGTH -> contentLength.toString)),
        body = HttpEntity.Streamed(objectSource, None, Some("application/octet-stream"))
      )
    }.recover {
      recoverResult
    }
  }

  private def handleFilePart: FilePartHandler[Path] = {
    case FileInfo(_, fileName, contentType, _) =>
      val tempFile: Path = Files.createTempFile("prefix-", fileName)
      val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(tempFile)
      Accumulator(sink).map {
        case IOResult(_, Success(_)) => FilePart("file", fileName, contentType, tempFile)
        case _ => throw FileOperationException(s"Failed to write file $fileName to temp file $tempFile")
      }
  }

  private def recoverResult: PartialFunction[Throwable, Result] = {
    case ex: S3Exception =>
      val status = ex.statusCode.intValue()
      Status(status)(Json.obj("status" -> status, "detail" -> ex.getMessage))
    case ex: IllegalUriException =>
      BadRequest(Json.obj("status" -> BAD_REQUEST, "detail" -> ex.getMessage))
    case ex: Throwable =>
      throw ex
  }
}
