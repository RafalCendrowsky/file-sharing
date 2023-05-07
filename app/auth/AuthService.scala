package auth

import akka.actor.ActorSystem
import org.mindrot.jbcrypt.BCrypt
import repo.{User, UserRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait AuthService {
  def authenticate(username: String, password: String): Future[Option[User]]

  def register(username: String, password: String): Future[Option[User]]

  def changePassword(username: String, password: String): Future[Option[User]]
}

class AuthServiceImpl @Inject()(userRepo: UserRepository)(
  implicit
  system: ActorSystem,
  ec: ExecutionContext
) extends AuthService {

  override def authenticate(username: String, password: String): Future[Option[User]] = {
    userRepo.get(username).map {
      case Some(User(_, Some(p))) if BCrypt.checkpw(password, p) => Some(User(username))
      case _ => None
    }
  }

  override def register(username: String, password: String): Future[Option[User]] = {
    userRepo.exists(username).flatMap {
      case true => Future.successful(None)
      case false => addEncrypted(username, password)
    }
  }

  override def changePassword(username: String, password: String): Future[Option[User]] =
    userRepo.get(username).flatMap { userOpt =>
      userOpt flatMap (_.password) map {
        case pass if BCrypt.checkpw(password, pass) => Future.successful(None)
        case _ => addEncrypted(username, password)
      } getOrElse Future.successful(None)
    }

  private def addEncrypted(username: String, password: String): Future[Option[User]] = {
    val passwordEncrypted = BCrypt.hashpw(password, BCrypt.gensalt())
    userRepo.add(User(username, Some(passwordEncrypted))).map {
      case true => Some(User(username))
      case _ => None
    }
  }

}