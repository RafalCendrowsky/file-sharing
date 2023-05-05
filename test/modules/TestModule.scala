package modules

import auth.AuthService
import com.google.inject.AbstractModule
import files.S3Client
import org.mockito.MockitoSugar.mock

class TestModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[S3Client]).toInstance(mock[S3Client])
    bind(classOf[AuthService]).toInstance(mock[AuthService])
  }

}
