package models

import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{MultipartUploadResult, ObjectMetadata}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

trait S3Client {
  def multipartUpload(key: String): Sink[ByteString, Future[MultipartUploadResult]]

  def download(key: String): Source[ByteString, Future[ObjectMetadata]]

  def delete(key: String): Source[Done, NotUsed]
}

class S3ClientImpl extends S3Client {
  private val bucket = ConfigFactory.load().getString("s3.bucket")

  override def multipartUpload(key: String): Sink[ByteString, Future[MultipartUploadResult]] = S3.multipartUpload(bucket, key)

  override def download(key: String): Source[ByteString, Future[ObjectMetadata]] = S3.getObject(bucket, key)

  override def delete(key: String): Source[Done, NotUsed] = S3.deleteObject(bucket, key)
}
