package files

import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{ListBucketResultContents, MultipartUploadResult, ObjectMetadata}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

trait S3Client {
  def multipartUpload(user: String, key: String): Sink[ByteString, Future[MultipartUploadResult]]

  def download(user: String, key: String): Source[ByteString, Future[ObjectMetadata]]

  def delete(user: String, key: String): Source[Done, NotUsed]

  def list(user: String): Source[ListBucketResultContents, NotUsed]
}

class S3ClientImpl extends S3Client {
  private val bucket = ConfigFactory.load().getString("s3.bucket")

  override def multipartUpload(user: String, key: String): Sink[ByteString, Future[MultipartUploadResult]] =
    S3.multipartUpload(bucket, s"$user/$key")

  override def download(user: String, key: String): Source[ByteString, Future[ObjectMetadata]] =
    S3.getObject(bucket, s"$user/$key")

  override def delete(user: String, key: String): Source[Done, NotUsed] =
    S3.deleteObject(bucket, s"$user/$key")

  override def list(user: String): Source[ListBucketResultContents, NotUsed] = S3.listBucket(bucket, Some(s"$user"))
}
