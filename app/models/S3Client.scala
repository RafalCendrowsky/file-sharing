package models

import akka.stream.alpakka.s3.{MultipartUploadResult, ObjectMetadata}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

trait S3Client {
  def multipartUpload(key: String): Sink[ByteString, Future[MultipartUploadResult]]
  def getObject(key: String): Source[ByteString, Future[ObjectMetadata]]
}

class S3ClientImpl extends S3Client {
  private val bucket = ConfigFactory.load().getString("s3.bucket")

  override def multipartUpload(key: String): Sink[ByteString, Future[MultipartUploadResult]] = S3.multipartUpload(bucket, key)

  override def getObject(key: String): Source[ByteString, Future[ObjectMetadata]] = S3.getObject(bucket, key)
}
