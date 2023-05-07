package auth

import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import org.mockito.MockitoSugar.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.UNAUTHORIZED
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.{AUTHORIZATION, GET, HOST, POST, route, writeableOf_AnyContentAsEmpty}
import play.api.test.{FakeHeaders, FakeRequest, Injecting}

import scala.concurrent.Future

class AuthControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with ScalaFutures {

  override def fakeApplication(): Application = {
    import modules.TestModule
    import play.api.inject.guice.GuiceApplicationBuilder
    new GuiceApplicationBuilder()
      .overrides(new TestModule)
      .build()
  }

  "Auth controller" must {

    val authService = inject[AuthService]

    val headers = FakeHeaders(Seq(AUTHORIZATION -> "Basic dGVzdDp0ZXN0", HOST -> "localhost"))

    val username = "test"
    val password = "test"
    val user = User(username, password)

    "authenticate a user" in {
      // WITH
      when(authService.authenticate(username, password)) thenReturn Future.successful(Some(user))

      // WHEN
      val result = route(app, FakeRequest(GET, "/api/v1/auth/current", headers, AnyContentAsEmpty)).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe OK.intValue
        whenReady(completed.body.consumeData(app.materializer)) { data =>
          val responseJson = Json.parse(data.utf8String)
          responseJson.as[JsObject].value.get("data").map {
            _.as[JsObject].value.get("user").map(_.as[String])
          } mustBe Some(Some(username))
        }
      }
    }

    "return unauthorized if user is not authenticated" in {
      // WITH
      when(authService.authenticate(username, password)) thenReturn Future.successful(None)

      // WHEN
      val result = route(app, FakeRequest(GET, "/api/v1/auth/current", headers, AnyContentAsEmpty)).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe UNAUTHORIZED.intValue()
      }
    }

    "register a new user" in {
      // WITH
      when(authService.register(username, password)) thenReturn Future.successful(Some(user))

      // WHEN
      val result = route(app,
        FakeRequest(
          POST,
          "/api/v1/auth/register",
          headers,
          Json.obj("username" -> username, "password" -> password))).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe OK.intValue
      }
    }

    "return appropriate status if user already exists when registering" in {
      // WITH
      when(authService.register(username, password)) thenReturn Future.successful(None)

      // WHEN
      val result = route(app,
        FakeRequest(
          POST,
          "/api/v1/auth/register",
          headers,
          Json.obj("username" -> username, "password" -> password))).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe BadRequest.intValue
      }
    }

    "change user password" in {
      // WITH
      when(authService.authenticate(username, password)) thenReturn Future.successful(Some(user))
      when(authService.changePassword(username, password)) thenReturn Future.successful(Some(user))

      // WHEN
      val result = route(app,
        FakeRequest(
          POST,
          "/api/v1/auth/changepass",
          headers,
          Json.obj("username" -> username, "password" -> password))).get

      // THEN
      whenReady(result) { completed =>
        completed.header.status mustBe OK.intValue
      }
    }
  }
}
