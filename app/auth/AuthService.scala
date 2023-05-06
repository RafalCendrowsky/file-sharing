package auth

import akka.actor.ActorSystem
import org.mindrot.jbcrypt.BCrypt
import repo.KeyValueStore

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class User(username: String, password: String)

trait AuthService {
  def authenticate(username: String, password: String): Future[Option[User]]

  def register(username: String, password: String): Future[Option[User]]
}

class AuthServiceImpl @Inject()(store: KeyValueStore)(
  implicit
  system: ActorSystem,
  ec: ExecutionContext
) extends AuthService {

  override def authenticate(username: String, password: String): Future[Option[User]] = {
    store.get(username).map {
      case Some(p) if BCrypt.checkpw(password, p) => Some(User(username, p))
      case _ => None
    }
  }

  override def register(username: String, password: String): Future[Option[User]] = {
    store.exists(username).flatMap {
      case true => Future.successful(None)
      case false =>
        val passwordEncrypted = BCrypt.hashpw(password, BCrypt.gensalt())
        store.set(username, passwordEncrypted).map { _ =>
          Some(User(username, passwordEncrypted))
        }
    }
  }
}