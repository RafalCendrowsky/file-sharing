package repo

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


case class User(username: String, password: Option[String] = None)

trait UserRepository {
  def get(username: String): Future[Option[User]]

  def add(user: User): Future[Boolean]

  def exists(username: String): Future[Boolean]
}

class UserRepositoryImpl @Inject()(store: KeyValueStore)(implicit ec: ExecutionContext) extends UserRepository {
  override def get(username: String): Future[Option[User]] = store.get(s"user:$username").map {
    case None => None
    case pass => Some(User(username, pass))
  }

  override def add(user: User): Future[Boolean] = {
    user match {
      case User(username, Some(password)) => store.set(s"user:$username", password)
      case _ => Future.successful(false)
    }
  }

  override def exists(username: String): Future[Boolean] = store.exists(s"user:$username")
}
