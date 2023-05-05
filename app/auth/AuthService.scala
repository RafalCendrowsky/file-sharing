package auth

import scala.concurrent.Future

case class User(username: String, password: String)

trait AuthService {
  def authenticate(username: String, password: String): Future[Option[User]]
}

class AuthServiceImpl extends AuthService {

  private val users = Seq(User("user1", "password1"), User("user2", "password2"))

  override def authenticate(username: String, password: String): Future[Option[User]] = {
    Future.successful(users.find(user => user.username == username && user.password == password))
  }
}