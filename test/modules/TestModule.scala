package modules

import com.google.inject.AbstractModule
import models.S3Client
import org.mockito.MockitoSugar.mock

class TestModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[S3Client]).toInstance(mock[S3Client])
  }

}
