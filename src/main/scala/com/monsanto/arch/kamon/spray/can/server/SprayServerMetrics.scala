package com.monsanto.arch.kamon.spray.can.server

import java.util.concurrent.atomic.AtomicReference

import kamon.com.monsanto.arch.kamon.spray.can.counterKey
import kamon.metric._
import kamon.metric.instrument._
import spray.can.server.Stats

import scala.concurrent.duration.DurationInt

/** A simple entity recorder used to publish the metrics available directly from Spray. */
class SprayServerMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  private[this] val _stats = new AtomicReference[Stats](new Stats(0.nanoseconds, 0, 0, 0, 0, 0, 0, 0))
  private val connections = counter("connections")
  private val openConnections = histogram("open-connections")
  private val requests = counter("requests")
  private val openRequests = histogram("open-requests")
  private val requestTimeouts = counter("request-timeouts")

  override def collect(collectionContext: CollectionContext): EntitySnapshot = {
    val currentStats = stats
    val parentSnapshot = super.collect(collectionContext)
    val metrics = parentSnapshot.metrics ++ Map(
      counterKey("uptime", Time.Nanoseconds) → CounterSnapshot(currentStats.uptime.toNanos),
      counterKey("max-open-connections") → CounterSnapshot(currentStats.maxOpenConnections),
      counterKey("max-open-requests") → CounterSnapshot(currentStats.maxOpenRequests)
    )
    new DefaultEntitySnapshot(metrics)
  }

  /** Returns the currently stored stats. */
  def stats: Stats = _stats.get

  /** Atomically updates the stats. */
  def stats_=(newStats: Stats): Unit = {
    val oldStats = _stats.getAndSet(newStats)
    openConnections.record(newStats.openConnections)
    openRequests.record(newStats.openRequests)
    connections.increment(newStats.totalConnections - oldStats.totalConnections)
    requests.increment(newStats.totalRequests - oldStats.totalRequests)
    requestTimeouts.increment(newStats.requestTimeouts - oldStats.requestTimeouts)
  }
}

object SprayServerMetrics extends EntityRecorderFactory[SprayServerMetrics] {
  override def category = "spray-can-server"

  override def createRecorder(instrumentFactory: InstrumentFactory): SprayServerMetrics = new SprayServerMetrics(instrumentFactory)
}

