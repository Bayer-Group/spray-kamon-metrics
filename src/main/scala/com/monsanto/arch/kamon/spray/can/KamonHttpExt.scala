package com.monsanto.arch.kamon.spray.can

import akka.actor.{ActorRef, ExtendedActorSystem}
import akka.io.IO
import com.monsanto.arch.kamon.spray.can.server.SprayProxy

/** Performs the actual work of instantiating the `KamonHttp` extension.
  *
  * @author Daniel Solano Gómez
  */
class KamonHttpExt(system: ExtendedActorSystem) extends IO.Extension {
  override def manager: ActorRef = {
    val settings = new KamonHttpSettings(system.settings.config)
    system.actorOf(SprayProxy.props(settings))
  }
}
