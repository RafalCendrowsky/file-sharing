package files

import akka.http.scaladsl.model.IllegalUriException
import akka.stream.alpakka.s3.S3Exception
import akka.stream.scaladsl.{FileIO, Keep, Sink, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import auth.AuthenticatedAction
import play.api.http.HttpEntity
import play.api.libs.json.{JsObject, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import java.nio.file.{Files, Path}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class FileController @Inject()(
  cc: ControllerComponents,
  s3Client: S3Client,
  authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext, materializer: Materializer) extends AbstractController(cc) {

  def upload: Action[MultipartFormData[Source[ByteString, _]]] =
    authenticatedAction.async(parse.multipartFormData(handleFilePart, maxLength = 2 * 1024 * 1024)) { implicit request =>
      request.body.file("file").fold(
        Future.successful(BadRequest("Missing file"))
      ) {
        case FilePart(_, filename, _, source, _, _) =>
          source.runWith(s3Client.multipartUpload(request.user, filename)).map {
            result => Ok(Json.toJson(Map("status" -> "success", "key" -> result.getKey)))
          }
      }
    }

  def download(key: String): Action[AnyContent] = authenticatedAction.async { implicit request =>
    val s3ObjectSource = s3Client.download(request.user, key)

    val contentLengthFuture = s3ObjectSource.toMat(Sink.head)(Keep.left).run().map(_.getContentLength)

    contentLengthFuture.map { contentLength =>
      Result(
        header = ResponseHeader(OK, Map(CONTENT_LENGTH -> contentLength.toString)),
        body = HttpEntity.Streamed(s3ObjectSource, None, Some("application/octet-stream"))
      )
    }.recover {
      recoverS3Result
    }
  }

  def delete(key: String): Action[AnyContent] = authenticatedAction.async { implicit request =>
    s3Client.delete(request.user, key).run().map { _ =>
      Ok(Json.obj("status" -> "success"))
    }.recover {
      recoverS3Result
    }
  }

  def list: Action[AnyContent] = authenticatedAction.async { implicit request =>
    s3Client.list(request.user).runFold(Seq.empty[JsObject]) { (acc, summary) =>
      acc :+ Json.obj("key" -> summary.getKey, "size" -> summary.getSize.toString)
    }.map { data =>
      Ok(Json.toJson(data))
    }.recover {
      recoverS3Result
    }
  }

  private def handleFilePart: FilePartHandler[Source[ByteString, _]] = {
    case FileInfo(_, fileName, contentType, _) =>
      val tempFile: Path = Files.createTempFile("prefix-", fileName)
      val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(tempFile)
      Accumulator(sink).map { case IOResult(_, Success(_)) =>
        FilePart("file", fileName, contentType, FileIO.fromPath(tempFile))
      }
  }

  private def recoverS3Result: PartialFunction[Throwable, Result] = {
    case ex: S3Exception =>
      val status = ex.statusCode.intValue()
      Status(status)(Json.obj("status" -> status, "detail" -> ex.getMessage))
    case ex: IllegalUriException =>
      BadRequest(Json.obj("status" -> BAD_REQUEST, "detail" -> ex.getMessage))
    case ex: Throwable =>
      throw ex
  }
}
