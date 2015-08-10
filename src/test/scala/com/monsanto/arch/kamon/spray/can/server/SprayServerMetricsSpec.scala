package com.monsanto.arch.kamon.spray.can.server

import akka.actor.Cancellable
import kamon.com.monsanto.arch.kamon.spray.can.counterKey
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument._
import kamon.metric.{EntitySnapshot, MetricKey}
import org.scalatest.{Matchers, WordSpec}
import spray.can.server.Stats

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class SprayServerMetricsSpec extends WordSpec with Matchers {
  "spray server metrics" when {
    "freshly created" should produceSnapshotsWith {
      def snapshot(): EntitySnapshot = {
        val metrics = createMetrics()
        metrics.collect(CollectionContext(2048))
      }

      "zero connections" in {
        snapshot().metrics(counterKey("connections")) should have ('count (0))
      }

      "zero max open connections" in {
        snapshot().metrics(counterKey("max-open-connections")) should have ('count (0))
      }

      "zero requests" in {
        snapshot().metrics(counterKey("requests")) should have ('count (0))
      }

      "zero max open requests" in {
        snapshot().metrics(counterKey("max-open-requests")) should have ('count (0))
      }

      "zero request timeouts" in {
        snapshot().metrics(counterKey("request-timeouts")) should have ('count (0))
      }

      "zero uptime" in {
        snapshot().metrics(counterKey("uptime", Time.Nanoseconds)) should have ('count (0))
      }

      "no open connections records" in {
        snapshot().metrics(histogramKey("open-connections")) should have ('empty (true))
      }

      "no open request records" in {
        snapshot().metrics(histogramKey("open-requests")) should have ('empty (true))
      }
    }

    "updated once" should produceSnapshotsWithTheCorrect {
      val stats = Stats(100.seconds, 203L, 32L, 37L, 54L, 16L, 29L, 2L)

      def snapshot(): EntitySnapshot = {
        val metrics = createMetrics()
        metrics.stats = stats
        metrics.collect(CollectionContext(2048))
      }

      "number of connections" in {
        snapshot().metrics(counterKey("connections")) should have ('count (stats.totalConnections))
      }

      "number of max open connections" in {
        snapshot().metrics(counterKey("max-open-connections")) should have ('count (stats.maxOpenConnections))
      }

      "number of requests" in {
        snapshot().metrics(counterKey("requests")) should have ('count (stats.totalRequests))
      }

      "number of max open requests" in {
        snapshot().metrics(counterKey("max-open-requests")) should have ('count (stats.maxOpenRequests))
      }

      "number of request timeouts" in {
        snapshot().metrics(counterKey("request-timeouts")) should have ('count (stats.requestTimeouts))
      }

      "uptime" in {
        snapshot().metrics(counterKey("uptime", Time.Nanoseconds)) should have ('count (stats.uptime.toNanos))
      }

      "open connections records" in {
        snapshot().metrics(histogramKey("open-connections")) should have (
          'empty (false),
          'min (stats.openConnections),
          'max (stats.openConnections),
          'numberOfMeasurements (1L),
          'sum (stats.openConnections)
        )
      }

      "open requests records" in {
        snapshot().metrics(histogramKey("open-requests")) should have (
          'empty (false),
          'min (stats.openRequests),
          'max (stats.openRequests),
          'numberOfMeasurements (1L),
          'sum (stats.openRequests)
        )
      }
    }

    "updated twice" should produceSnapshotsWithTheCorrect {
      val stats1 = Stats(100.seconds, 203L, 32L, 37L, 54L, 16L, 29L, 2L)
      val stats2 = Stats(200.seconds, 412L, 25L, 41L, 78L, 9L, 29L, 5L)

      def snapshot(): EntitySnapshot = {
        val metrics = createMetrics()
        metrics.stats = stats1
        metrics.stats = stats2
        metrics.collect(CollectionContext(2048))
      }

      "number of connections" in {
        snapshot().metrics(counterKey("connections")) should have ('count (stats2.totalConnections))
      }

      "number of max open connections" in {
        snapshot().metrics(counterKey("max-open-connections")) should have ('count (stats2.maxOpenConnections))
      }

      "number of requests" in {
        snapshot().metrics(counterKey("requests")) should have ('count (stats2.totalRequests))
      }

      "number of max open requests" in {
        snapshot().metrics(counterKey("max-open-requests")) should have ('count (stats2.maxOpenRequests))
      }

      "number of request timeouts" in {
        snapshot().metrics(counterKey("request-timeouts")) should have ('count (stats2.requestTimeouts))
      }

      "uptime" in {
        snapshot().metrics(counterKey("uptime", Time.Nanoseconds)) should have ('count (stats2.uptime.toNanos))
      }

      "open connections records" in {
        snapshot().metrics(histogramKey("open-connections")) should have (
          'empty (false),
          'min (stats2.openConnections),
          'max (stats1.openConnections),
          'numberOfMeasurements (2L),
          'sum (stats1.openConnections + stats2.openConnections)
        )
      }

      "open requests records" in {
        snapshot().metrics(histogramKey("open-requests")) should have (
          'empty (false),
          'min (stats2.openRequests),
          'max (stats1.openRequests),
          'numberOfMeasurements (2L),
          'sum (stats1.openRequests + stats2.openRequests)
        )
      }
    }

    "updated after a snapshot" should produceSnapshotsWithTheCorrect {
      val stats1 = Stats(100.seconds, 203L, 32L, 37L, 54L, 16L, 29L, 2L)
      val stats2 = Stats(200.seconds, 412L, 25L, 41L, 78L, 9L, 29L, 5L)

      def snapshot(): EntitySnapshot = {
        val metrics = createMetrics()
        metrics.stats = stats1
        metrics.collect(CollectionContext(2048))
        metrics.stats = stats2
        metrics.collect(CollectionContext(2048))
      }

      "number of connections" in {
        snapshot().metrics(counterKey("connections")) should have (
          'count (stats2.totalConnections - stats1.totalConnections)
        )
      }

      "number of max open connections" in {
        snapshot().metrics(counterKey("max-open-connections")) should have ('count (stats2.maxOpenConnections))
      }

      "number of requests" in {
        snapshot().metrics(counterKey("requests")) should have (
          'count (stats2.totalRequests - stats1.totalRequests)
        )
      }

      "number of max open requests" in {
        snapshot().metrics(counterKey("max-open-requests")) should have ('count (stats2.maxOpenRequests))
      }

      "number of request timeouts" in {
        snapshot().metrics(counterKey("request-timeouts")) should have (
          'count (stats2.requestTimeouts - stats1.requestTimeouts)
        )
      }

      "uptime" in {
        snapshot().metrics(counterKey("uptime", Time.Nanoseconds)) should have ('count (stats2.uptime.toNanos))
      }

      "open connections records" in {
        snapshot().metrics(histogramKey("open-connections")) should have (
          'empty (false),
          'min (stats2.openConnections),
          'max (stats2.openConnections),
          'numberOfMeasurements (1L),
          'sum (stats2.openConnections)
        )
      }

      "open requests records" in {
        snapshot().metrics(histogramKey("open-requests")) should have (
          'empty (false),
          'min (stats2.openRequests),
          'max (stats2.openRequests),
          'numberOfMeasurements (1L),
          'sum (stats2.openRequests)
        )
      }
    }
  }

  /** Used for constructing new metrics instances. */
  private def createMetrics(): SprayServerMetrics = {
    val instrumentSettings = InstrumentSettings(DynamicRange(1, 60 * 60 * 1000000000, 3), None)
    val defaultInstrumentSettings = DefaultInstrumentSettings(instrumentSettings, instrumentSettings, instrumentSettings)
    val refreshScheduler = new RefreshScheduler {
      override def schedule(interval: FiniteDuration, refresh: () => Unit): Cancellable = ???
    }
    val instrumentFactory = InstrumentFactory(Map.empty, defaultInstrumentSettings, refreshScheduler)
    new SprayServerMetrics(instrumentFactory)
  }

  /** Allows creation of Histogram keys. */
  private def histogramKey(name: String): MetricKey = {
    val keyClass = Class.forName("kamon.metric.HistogramKey")
    val keyConstructor = keyClass.getDeclaredConstructor(classOf[String], classOf[UnitOfMeasurement])
    keyConstructor.newInstance(name, UnitOfMeasurement.Unknown).asInstanceOf[MetricKey]
  }

  def produceSnapshotsWith = afterWord("produce snapshots with")

  def produceSnapshotsWithTheCorrect = afterWord("produce snapshots with the correct")
}
