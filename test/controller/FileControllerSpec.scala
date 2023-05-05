package controller

import akka.Done
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Error
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET}
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.stream.alpakka.s3.{ObjectMetadata, S3Exception}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import models.S3Client
import org.mockito.MockitoSugar.when
import org.openqa.selenium.InvalidArgumentException
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.test.Helpers.{contentType, route, status, writeableOf_AnyContentAsEmpty}
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class FileControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  override def fakeApplication(): Application = {
    import modules.TestModule
    import play.api.inject.guice.GuiceApplicationBuilder
    new GuiceApplicationBuilder()
      .overrides(new TestModule)
      .build()
  }

  "File controller" must {

    // This is a mock of the S3Client trait
    val s3Client = inject[S3Client]

    val key = "test-file.txt"

    "download a file" in {
      val header: HttpHeader =HttpHeader.parse(CONTENT_LENGTH, "100") match {
        case HttpHeader.ParsingResult.Ok(h, _) => h
        case Error(_) => throw new InvalidArgumentException("Invalid header")
      }

      val source: Source[ByteString, Future[ObjectMetadata]] =
        Source.single(ByteString("test")).mapMaterializedValue(_ => Future.successful(ObjectMetadata(List(header))))

      // Mock file download
      when (s3Client.download(key)) thenReturn source

      val request = FakeRequest(GET.value, s"/api/v1/files/$key")
      val result = route(app, request).get
      implicit val timeout: Timeout = Timeout(300.millis)

      status(result) mustBe OK.intValue
      Await.result(result, timeout.duration)
        .header.headers.get("Content-Length") mustBe Some("100")
      contentType(result) mustBe Some("application/octet-stream")
    }

    "return an appropriate error when the file does not exist" in {
      val source: Source[ByteString, Future[ObjectMetadata]] =
        Source.single(ByteString("test")).mapMaterializedValue(_ => Future.failed(S3Exception("File not found", NotFound)))

      // Mock file download
      when (s3Client.download(key)) thenReturn source

      val request = FakeRequest(GET.value, s"/api/v1/files/$key")
      val result = route(app, request).get
      implicit val timeout: Timeout = Timeout(300.millis)

      status(result) mustBe NotFound.intValue
    }

    "delete a file" in {
      // Mock file deletion
      when (s3Client.delete(key)) thenReturn Source.single(Done)

      val request = FakeRequest(DELETE.value, s"/api/v1/files/$key")
      val result = route(app, request).get
      implicit val timeout: Timeout = Timeout(300.millis)

      status(result) mustBe OK.intValue
    }

  }

}
