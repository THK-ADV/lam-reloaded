package modules

import com.typesafe.config.{ConfigFactory, Config}
import play.api.{Play, Configuration}

trait ConfigurationModule {
  def lwmConfig: Configuration
}

trait ConfigurationModuleImpl extends ConfigurationModule {
  override def lwmConfig: Configuration = Configuration(ConfigFactory.defaultApplication().resolve())
}
