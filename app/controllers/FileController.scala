package controllers

import akka.http.scaladsl.model.IllegalUriException
import akka.stream.Materializer
import akka.stream.alpakka.s3.{MultipartUploadResult, S3Exception}
import akka.stream.scaladsl.{Keep, Sink}
import models.S3Client
import play.api.http.HttpEntity
import play.api.libs.json.{JsObject, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FileController @Inject()(
  cc: ControllerComponents,
  s3Client: S3Client
)(implicit ec: ExecutionContext, materializer: Materializer) extends AbstractController(cc) {

  def upload: Action[MultipartFormData[MultipartUploadResult]] =
    Action(parse.multipartFormData(handleAWSUploadResult, maxLength = 2 * 1024 * 1024)) { implicit request =>
      val maybeUploadResult = request.body.file("file").map {
        case FilePart(_, _, _, multipartUploadResult, _, _) =>
          multipartUploadResult
      }
      maybeUploadResult.fold(
        InternalServerError("Something went wrong!")
      )(uploadResult =>
        Ok(Json.toJson(Map("status" -> "success", "key" -> uploadResult.getKey)))
      )
    }

  def download(key: String): Action[AnyContent] = Action.async { implicit request =>
    val s3ObjectSource = s3Client.download(key)

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

  def delete(key: String): Action[AnyContent] = Action.async { implicit request =>
    s3Client.delete(key).run().map { _ =>
      Ok(Json.obj("status" -> "success"))
    }.recover {
      recoverS3Result
    }
  }

  def list: Action[AnyContent] = Action.async { implicit request =>
    s3Client.list.runFold(Seq.empty[JsObject]) { (acc, summary) =>
      acc :+ Json.obj("key" -> summary.getKey, "size" -> summary.getSize.toString)
    }.map { data =>
      Ok(Json.toJson(data))
    }.recover {
      recoverS3Result
    }
  }

  private def handleAWSUploadResult: FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, fileName, contentType, _) =>
      val key = s"${UUID.randomUUID()}${getFileExtension(fileName).getOrElse("")}"
      val accumulator = Accumulator(s3Client.multipartUpload(key))

      accumulator map {
        multipartUploadResult =>
          FilePart(partName, fileName, contentType, multipartUploadResult)
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

  private def getFileExtension(filename: String): Option[String] =
    filename.split('.').lastOption.map("." + _)
}
