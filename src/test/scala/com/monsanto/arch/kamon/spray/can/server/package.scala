package com.monsanto.arch.kamon.spray.can

import com.typesafe.config.ConfigFactory

package object server {
  val TestSettings: KamonHttpSettings = {
    new KamonHttpSettings(ConfigFactory.load())
  }
}
