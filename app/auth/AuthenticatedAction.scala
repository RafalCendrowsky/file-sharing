package auth

import play.api.http.Status.{FORBIDDEN, UNAUTHORIZED}
import play.api.libs.json.Json
import play.api.mvc.Results.{Forbidden, Unauthorized}
import play.api.mvc._

import java.util.Base64
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class AuthenticatedRequest[A](user: String, request: Request[A]) extends WrappedRequest(request)

class AuthenticatedAction @Inject()(parser: BodyParsers.Default, authService: AuthService)(implicit ec: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  override def parser: BodyParser[AnyContent] = parser

  override protected def executionContext: ExecutionContext = ec

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(header) if header.startsWith("Basic ") =>
        val base64Credentials = header.substring("Basic ".length).trim
        try {
          val (username, password) = decodeCredentials(base64Credentials)
          authService.authenticate(username, password).flatMap {
            case Some(user) => block(AuthenticatedRequest(user.username, request))
            case None => Future.successful(
              Unauthorized(Json.obj("status" -> UNAUTHORIZED, "detail" -> "Invalid credentials")))
          }
        } catch {
          case _: IllegalArgumentException => Future.successful(
            Forbidden(Json.obj("status" -> FORBIDDEN, "detail" -> "Invalid authorization header")
            ))
        }
      case _ => Future.successful(
        Unauthorized(Json.obj("status" -> UNAUTHORIZED, "detail" -> "Authorization header is required")))
    }
  }

  private def decodeCredentials(base64String: String): (String, String) = {
    val decodedCredentials = new String(Base64.getDecoder.decode(base64String.getBytes))
    val credentials = decodedCredentials.split(":", 2)
    if (credentials.length != 2) {
      throw new IllegalArgumentException("Invalid authorization header")
    }
    (credentials(0), credentials(1))
  }
}