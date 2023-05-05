package controllers

import akka.stream.Materializer
import akka.stream.alpakka.s3.{MultipartUploadResult, S3Exception}
import akka.stream.scaladsl.{Keep, Sink}
import models.S3Client
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FileController @Inject()(
  cc: ControllerComponents,
  materializer: Materializer,
  s3Client: S3Client
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def upload: Action[MultipartFormData[MultipartUploadResult]] =
    Action(parse.multipartFormData(handleAWSUploadResult, maxLength = 2 * 1024 * 1024)) { implicit request =>
      val maybeUploadResult = request.body.file("file").map {
          case FilePart(_, _, _,  multipartUploadResult, _, _) =>
            multipartUploadResult
      }
      maybeUploadResult.fold(
        InternalServerError("Something went wrong!")
      ) (uploadResult =>
        Ok(Json.toJson(Map("status" -> "success", "key" -> uploadResult.getKey)))
      )
  }

  def download(key: String): Action[AnyContent] = Action.async { implicit request =>
    val s3ObjectSource = s3Client.getObject(key)

    val contentLengthFuture = s3ObjectSource.toMat(Sink.head)(Keep.left).run()(materializer).map(_.getContentLength)

    contentLengthFuture.map { contentLength =>
      Result(
        header = ResponseHeader(OK, Map(CONTENT_LENGTH -> contentLength.toString)),
        body = HttpEntity.Streamed(s3ObjectSource, None, Some("application/octet-stream"))
      )
    }.recover {
      case _: S3Exception =>
        NotFound(Json.obj("status" -> NOT_FOUND, "detail" -> s"Object with key $key not found"))
      case ex: Throwable =>
        throw ex
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

  private def getFileExtension(filename: String): Option[String] =
    filename.split('.').lastOption.map("." + _)
}
