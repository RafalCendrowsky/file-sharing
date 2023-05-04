package controllers

import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import com.typesafe.config.ConfigFactory
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class UploadController @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def upload: Action[MultipartFormData[MultipartUploadResult]] =
    Action(parse.multipartFormData(handleAWSUploadResult)) { implicit request =>
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

  private def handleAWSUploadResult: FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, fileName, contentType, _) =>
      val key = s"${UUID.randomUUID()}${getFileExtension(fileName).getOrElse("")}"
      val bucket = ConfigFactory.load().getString("s3.bucket")
      val accumulator = Accumulator(S3.multipartUpload(bucket, key))

      accumulator map {
        multipartUploadResult =>
          FilePart(partName, fileName, contentType, multipartUploadResult)
      }
  }

  private def getFileExtension(filename: String): Option[String] =
    filename.split('.').lastOption.map("." + _)
}
