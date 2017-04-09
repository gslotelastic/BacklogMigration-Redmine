package com.nulabinc.r2b.exporter.modules

import com.google.inject.name.Names
import com.nulabinc.r2b.redmine.conf.RedmineConfig
import com.nulabinc.r2b.redmine.modules.RedmineDefaultModule

/**
  * @author uchida
  */
class RedmineModule(apiConfig: RedmineConfig, backlogProjectKey: String) extends RedmineDefaultModule(apiConfig) {

  override def configure() = {
    super.configure()
    bind(classOf[String]).annotatedWith(Names.named("backlogProjectKey")).toInstance(backlogProjectKey)
  }

}
