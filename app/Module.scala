import com.google.inject.AbstractModule
import models.{S3Client, S3ClientImpl}
import play.api.{Configuration, Environment}

class Module(environment: Environment, configuration: Configuration)
  extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[S3Client]).to(classOf[S3ClientImpl])
  }
}