package com.monsanto.arch.kamon.spray.can

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.FiniteDuration

/** Container for the settings of the `KamonHttp` extension.  All of the configuration is placed under the path
  * `spray.can.server`.  Refer to the `reference.conf` for rmore details.
  *
  * @param config the configuration to use
  *
  * @author Daniel Solano Gómez
  */
class KamonHttpSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "spray.can.kamon")
  /** Scoped configuration. */
  private val kamonConfig = config.getConfig("spray.can.kamon")

  /** The interval between each request to Spray for its statistics. */
  val refreshInterval: FiniteDuration = FiniteDuration(
    kamonConfig.getDuration("refresh-interval", TimeUnit.MILLISECONDS),
    TimeUnit.MILLISECONDS)
}
