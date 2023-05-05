package controller

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Error
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.alpakka.s3.ObjectMetadata
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import models.S3Client
import org.mockito.MockitoSugar.when
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.test.Helpers.{contentType, route, status, writeableOf_AnyContentAsEmpty}
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class FileControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  override def fakeApplication(): Application = {
    import modules.TestModule
    import play.api.inject.guice.GuiceApplicationBuilder
    new GuiceApplicationBuilder()
      .overrides(new TestModule)
      .build()
  }

  "File controller" must {

    "download a file" in {
      val s3Client = inject[S3Client]
      val ec = inject[ExecutionContext]

      val key = "test-file.txt"
      val headerVal: HttpHeader = HttpHeader.parse("Content-Length", "100") match {
        case HttpHeader.ParsingResult.Ok(h, _) => h
        case Error(error) => throw new RuntimeException(error.summary)
      }
      val source: Source[ByteString, Future[ObjectMetadata]] =
        Source(List(ByteString("test"))).mapMaterializedValue(_ => Future.successful(ObjectMetadata(List(headerVal))))

      when (s3Client.getObject(key)) thenReturn source

      val request = FakeRequest(GET.value, s"/api/v1/files/$key")
      val result = route(app, request).get
      implicit val timeout: Timeout = Timeout(300.millis)

      status(result) mustBe OK.intValue
      Await.result(result, timeout.duration)
        .header.headers.get("Content-Length") mustBe Some("100")
      contentType(result) mustBe Some("application/octet-stream")
    }

  }

}
