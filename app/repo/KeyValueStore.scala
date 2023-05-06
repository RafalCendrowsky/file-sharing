package repo

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import redis.RedisClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait KeyValueStore {
  def get(key: String): Future[Option[String]]

  def set(key: String, value: String, expireSec: Option[Long] = None): Future[Boolean]

  def exists(key: String): Future[Boolean]
}

@Singleton
class RedisStore @Inject()(implicit system: ActorSystem, ec: ExecutionContext) extends KeyValueStore {
  private val host = ConfigFactory.load().getString("redis.host")
  private val port = ConfigFactory.load().getInt("redis.port")
  private val redis = RedisClient(host, port)

  def get(key: String): Future[Option[String]] = redis.get(key) map {
    case Some(p) => Some(p.utf8String)
    case _ => None
  }


  def set(key: String, value: String, expireSec: Option[Long] = None): Future[Boolean] =
    redis.set(key, value, expireSec)

  override def exists(key: String): Future[Boolean] = redis.exists(key)
}
