package com.monsanto.arch.kamon.spray.can.server

import akka.actor.{Actor, Props}
import akka.io.IO
import com.monsanto.arch.kamon.spray.can.KamonHttpSettings
import spray.can.Http

/** Transparently proxies the Spray server extension in order to create monitors on each bind.
  *
  * @param settings the configuration for the extension
  *
  * @author Daniel Solano Gómez
  */
class SprayProxy(settings: KamonHttpSettings) extends Actor {
  import context.system

  private val ioActor = IO(Http)
  private[this] val monitorCounter = Iterator.from(0)

  /** Forwards all messages to Spray, except for Bind requests which are then routed through a monitor instance. */
  override def receive = {
    case x: Http.Bind ⇒
      val proxied = sender()
      val monitor = context.actorOf(SprayMonitor.props(proxied, settings), s"monitor-${monitorCounter.next()}")
      ioActor.tell(x, monitor)
    case x ⇒ ioActor.forward(x)
  }
}

object SprayProxy {
  def props(settings: KamonHttpSettings): Props = Props(new SprayProxy(settings))
}
