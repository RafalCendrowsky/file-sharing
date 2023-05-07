import auth.AuthService
import com.google.inject.AbstractModule
import files.{S3Client, StorageClient}
import play.api.{Configuration, Environment}
import repo._

class Module(environment: Environment, configuration: Configuration)
  extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[StorageClient]).to(classOf[S3Client])
    bind(classOf[AuthService]).to(classOf[auth.AuthServiceImpl])
    bind(classOf[KeyValueStore]).to(classOf[RedisStore])
    bind(classOf[UserRepository]).to(classOf[repo.UserRepositoryImpl])
    bind(classOf[FileShareRepository]).to(classOf[FileShareRepositoryImpl])
  }
}