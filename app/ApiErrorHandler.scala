import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import javax.inject.{Inject, Provider}
import scala.concurrent._


class ApiErrorHandler @Inject()(
  environment: Environment,
  configuration: Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router]
) extends DefaultHttpErrorHandler(environment,
  configuration,
  sourceMapper.sourceMapper,
  Some(router.get())) {


  override def onClientError(
    request: RequestHeader,
    statusCode: Int,
    message: String
  ): Future[Result] = {
    Future.successful {
      statusCode match {

        case _ if statusCode >= 400 && statusCode < 500 =>
          Status(statusCode)(Json.obj("status" -> statusCode, "detail" -> message))
        case _ =>
          throw new IllegalArgumentException(
            s"onClientError invoked with non client error status code $statusCode: $message")
      }
    }
  }

  override protected def onDevServerError(
    request: RequestHeader,
    exception: UsefulException
  ): Future[Result] = {
    Future.successful(
      InternalServerError(Json.obj("exception" -> exception.toString)))
  }

  override protected def onProdServerError(
    request: RequestHeader,
    exception: UsefulException
  ): Future[Result] = {
    Future.successful(InternalServerError)
  }
}
