package repo

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class FileShare(key: String, fileName: String)

trait FileShareRepository {
  def get(key: String): Future[Option[FileShare]]

  def add(fileShare: FileShare, expire: Option[Long]): Future[Boolean]
}

class FileShareRepositoryImpl @Inject()(store: KeyValueStore)(implicit ec: ExecutionContext)
  extends FileShareRepository {
  def get(key: String): Future[Option[FileShare]] =
    store.get(s"share:$key").map(_.map(FileShare(key, _)))

  override def add(fileShare: FileShare, expire: Option[Long]): Future[Boolean] =
    store.set(s"share:${fileShare.key}", fileShare.fileName, expire)
}
