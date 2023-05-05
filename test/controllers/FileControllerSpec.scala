package controllers

import akka.Done
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Error
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET}
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.stream.alpakka.s3.{ListBucketResultContents, ObjectMetadata, S3Exception}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import models.S3Client
import org.mockito.MockitoSugar.when
import org.openqa.selenium.InvalidArgumentException
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers.{CONTENT_LENGTH, route, status, writeableOf_AnyContentAsEmpty}
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FileControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with ScalaFutures {

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
      // WITH
      val header: HttpHeader =HttpHeader.parse(CONTENT_LENGTH, "100") match {
        case HttpHeader.ParsingResult.Ok(h, _) => h
        case Error(_) => throw new InvalidArgumentException("Invalid header")
      }

      val source: Source[ByteString, Future[ObjectMetadata]] =
        Source.single(ByteString("test")).mapMaterializedValue(_ => Future.successful(ObjectMetadata(List(header))))

      when(s3Client.download(key)) thenReturn source

      // WHEN
      val request = FakeRequest(GET.value, s"/api/v1/files/$key")
      val result = route(app, request).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe OK.intValue
        completed.header.headers.get(CONTENT_LENGTH) mustBe Some("100")
        completed.body.contentType mustBe Some("application/octet-stream")
      }
    }

    "return an appropriate error when the file does not exist" in {
      // WITH
      val source: Source[ByteString, Future[ObjectMetadata]] =
        Source.single(ByteString("test")).mapMaterializedValue(_ => Future.failed(S3Exception("File not found", NotFound)))

      when(s3Client.download(key)) thenReturn source

      // WHEN
      val request = FakeRequest(GET.value, s"/api/v1/files/$key")
      val result = route(app, request).get

      // THEN
      implicit val timeout: Timeout = Timeout(300.millis)
      status(result) mustBe NotFound.intValue
    }

    "delete a file" in {
      // WITH
      when(s3Client.delete(key)) thenReturn Source.single(Done)

      // WHEN
      val request = FakeRequest(DELETE.value, s"/api/v1/files/$key")
      val result = route(app, request).get

      // THEN
      whenReady(result) {
        _.header.status mustBe OK.intValue
      }
    }

    "list available files' keys and sizes" in {
      // WITH
      val expectedValues = Seq(("file1", 100L), ("file2", 200L), ("file3", 300L))

      val objectSummaries = expectedValues.map {
        case (key, size) => ListBucketResultContents("", key, "", size, null, "")
      }

      when(s3Client.list) thenReturn Source(objectSummaries)

      // WHEN
      val request = FakeRequest(GET.value, "/api/v1/files")
      val result = route(app, request).get

      // THEN
      whenReady(result) { completed =>

        completed.header.status mustBe OK.intValue
        completed.body.contentType mustBe Some("application/json")

        whenReady(completed.body.consumeData(app.materializer)) { data =>
          // We expect a JSON array of objects with the files' keys and sizes
          val responseJson = Json.parse(data.utf8String)
          responseJson mustBe a[JsArray]
          responseJson.as[JsArray].value.map { json =>
            val key = (json \ "key").as[String]
            val size = (json \ "size").as[String]
            (key, size.toLong)
          } must contain theSameElementsAs expectedValues
        }
      }

    }

  }

}
