package modules

import auth.AuthService
import com.google.inject.AbstractModule
import files.StorageClient
import org.mockito.MockitoSugar.mock
import repo.KeyValueStore

class TestModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[StorageClient]).toInstance(mock[StorageClient])
    bind(classOf[AuthService]).toInstance(mock[AuthService])
    bind(classOf[KeyValueStore]).toInstance(mock[KeyValueStore])
  }

}
