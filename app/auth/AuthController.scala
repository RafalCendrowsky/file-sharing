package auth

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class AuthController @Inject()(
  cc: ControllerComponents,
  authService: AuthService,
  authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def register: Action[AnyContent] = Action.async { implicit request =>
    val usernameOpt = request.body.asJson.flatMap(_.as[JsObject].value.get("username")).map(_.as[String])
    val passwordOpt = request.body.asJson.flatMap(_.as[JsObject].value.get("password")).map(_.as[String])
    (usernameOpt, passwordOpt) match {
      case (Some(username), Some(password)) =>
        authService.register(username, password).map {
          case Some(_) =>
            Ok(Json.obj("status" -> OK, "detail" -> "User registered successfully"))
          case None =>
            BadRequest(Json.obj("status" -> BAD_REQUEST, "detail" -> "User already exists"))
        }
      case _ =>
        Future.successful(
          BadRequest(Json.obj("status" -> BAD_REQUEST, "detail" -> "Invalid username or password")))
    }
  }

  def current: Action[AnyContent] = authenticatedAction.async { implicit request =>
    Future.successful(Ok(Json.obj("status" -> OK, "data" -> Json.obj("user" -> request.user))))
  }
}
