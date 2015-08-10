package com.monsanto.arch.kamon.spray.can.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.Logging
import com.monsanto.arch.kamon.spray.can.KamonHttpSettings
import kamon.Kamon
import spray.can.Http
import spray.can.server.Stats

/** Performs the actual job of monitoring a Spray can server by periodically sending it `GetStats` messages and placing
  * the resulting statistics in a metrics instance.
  *
  * @param proxied the actor which initiated the bind.  The monitor will forward the initial `Http.Bound` or
  *                `Http.CommandFailed` events to this actor.
  * @param settings the settings for the extension
  *
  * @author Daniel Solano Gómez
  */
class SprayMonitor(proxied: ActorRef, settings: KamonHttpSettings) extends Actor {
  private[this] val log = Logging(this)

  /** Starts out in the `binding` state. */
  override def receive = binding

  /** In this state, the monitor is waiting for the server to bind.   If it gets the `Http.Bound` message, it will
    * forward the message to the proxied actor and move into the `bound` state.  Otherwise, if it gets a
    * `Htttp.CommandFailed` event, it forwards the event and terminates.
    */
  def binding: Receive = {
    case x @ Http.Bound(address) ⇒
      proxied.forward(x)
      context.become(bound(address))
    case x: Http.CommandFailed ⇒
      proxied.forward(x)
      context.stop(self)
  }

  /** In this state, the monitor begins periodically getting metrics from the server until it terminates. */
  def bound(address: InetSocketAddress): Receive = {
    import context.dispatcher

    val httpListener = sender()
    context.watch(httpListener)
    val updateTask = context.system.scheduler.schedule(settings.refreshInterval, settings.refreshInterval,
      httpListener, Http.GetStats)
    val metricsName = s"${address.getHostName}:${address.getPort}"
    val metrics = Kamon.metrics.entity(SprayServerMetrics, metricsName)
    log.info(s"Creating monitor for server $address")

    {
      case Terminated(x) ⇒
        log.info(s"Terminating monitor for server $address")
        updateTask.cancel()
        Kamon.metrics.removeEntity(metricsName, SprayServerMetrics.category)
        context.stop(self)
      case s: Stats ⇒ metrics.stats = s
    }
  }
}

object SprayMonitor {
  def props(proxied: ActorRef, settings: KamonHttpSettings): Props = Props(new SprayMonitor(proxied, settings))
}

