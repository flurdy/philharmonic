package com.flurdy.conductor

import com.typesafe.config.{Config, ConfigFactory}


trait FeatureToggles extends WithConfig {

   private def isFeatureEnabled(property: String) = config.hasPath(s"${property}.enabled") &&
                                             config.getBoolean(s"${property}.enabled")

   lazy val isStacksEnabled  = isFeatureEnabled("stacks")

   var isDockerStubbed = isFeatureEnabled("docker.stubbed")

   def enableDockerStubbing() = {
      isDockerStubbed = true
   }
}

class DefaultFeatureToggles(val config: Config) extends FeatureToggles {
   def this() = this(ConfigFactory.empty())
}
