package auth

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import redis.RedisClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class User(username: String, password: String)

trait AuthService {
  def authenticate(username: String, password: String): Future[Option[User]]

  def register(username: String, password: String): Future[Option[User]]
}

@Singleton
class AuthServiceImpl @Inject()(implicit system: ActorSystem, ec: ExecutionContext) extends AuthService {

  private val host = ConfigFactory.load().getString("redis.host")
  private val port = ConfigFactory.load().getInt("redis.port")
  private val redis = RedisClient(host, port)
  private val log = LoggerFactory.getLogger(this.getClass)

  override def authenticate(username: String, password: String): Future[Option[User]] = {
    redis.get(username).map {
      case Some(p) if p.utf8String == password => Some(User(username, password))
      case _ => None
    }
  }

  override def register(username: String, password: String): Future[Option[User]] = {
    redis.exists(username).flatMap {
      case true => Future.successful(None)
      case false => redis.set(username, password).map { _ =>
        Some(User(username, password))
      }
    }
  }
}