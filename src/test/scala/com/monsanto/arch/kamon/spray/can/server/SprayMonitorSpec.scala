package com.monsanto.arch.kamon.spray.can.server

import java.net.InetSocketAddress

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import kamon.Kamon
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}
import spray.can.Http
import spray.can.server.Stats

import scala.concurrent.duration.DurationInt

class SprayMonitorSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers
    with BeforeAndAfterAll with BeforeAndAfterEach with ImplicitSender {
  def this() = this(ActorSystem("spray-monitor-spec"))

  var httpListener: TestProbe = _
  var commanderProbe: TestProbe = _
  var monitor: ActorRef = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    ignoreNoMsg()
    commanderProbe = TestProbe()
    monitor = system.actorOf(SprayMonitor.props(commanderProbe.ref, TestSettings), "test-monitor")
    httpListener = TestProbe()
    httpListener.ignoreMsg { case Http.GetStats ⇒ true }
  }

  override protected def afterEach(): Unit = {
    try super.afterEach()
    finally {
      watch(monitor)
      system.stop(monitor)
      expectMsgPF() {
        case x: Terminated ⇒ x.actor == monitor
      }
    }
  }

  override protected def beforeAll(): Unit = {
    Kamon.start()
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
    Kamon.shutdown()
    super.afterAll()
  }

  "a Spray monitor" when {
    val bound = Http.Bound(new InetSocketAddress("localhost", 80))
    val metricsName = "localhost:80"

    "waiting for a binding" when {
      "a Http.CommandFailed message arrives" should {
        val listener = TestProbe().ref
        val bind = Http.Bind(listener, "localhost")
        val commandFailed = Http.CommandFailed(bind)

        "forward the message transparently" in {
          monitor.tell(commandFailed, httpListener.ref)
          commanderProbe.expectMsg(commandFailed)
          commanderProbe.lastSender shouldBe httpListener.ref
        }

        "shut itself down" in {
          ignoreMsg { case Http.CommandFailed(_) ⇒ true }
          watch(monitor)
          monitor.tell(commandFailed, httpListener.ref)
          val terminated = expectMsgType[Terminated]
          terminated.actor shouldBe monitor
        }
      }

      "a Http.Bound message arrives" should {
        "forward the message" in {
          monitor.tell(bound, httpListener.ref)
          commanderProbe.expectMsg(bound)
          commanderProbe.lastSender shouldBe httpListener.ref
        }

        "schedule GetStats messages" in {
          val interval = TestSettings.refreshInterval
          httpListener.ignoreNoMsg()
          monitor.tell(bound, httpListener.ref)
          within(interval * 4) {
            httpListener.expectMsg(Http.GetStats)
            httpListener.expectMsg(Http.GetStats)
            httpListener.expectMsg(Http.GetStats)
          }
        }

        "register the metrics" in {
          monitor.tell(bound, httpListener.ref)
          // force processing a message
          monitor ! Identify(1)
          expectMsgType[ActorIdentity]
          val maybeMetrics = Kamon.metrics.find(metricsName, SprayServerMetrics.category)
          maybeMetrics shouldBe defined
        }
      }
    }

    "bound to a server" when {
      "its listener terminates it"  should {
        "terminate" in {
          monitor.tell(bound, httpListener.ref)
          watch(monitor)
          system.stop(httpListener.ref)
          expectMsgType[Terminated].actor shouldBe monitor

        }

        "remove the server metrics from Kamon" in {
          monitor.tell(bound, httpListener.ref)
          watch(monitor)
          system.stop(httpListener.ref)
          expectMsgType[Terminated]
          val maybeMetrics = Kamon.metrics.find(metricsName, SprayServerMetrics.category)
          maybeMetrics shouldBe None
        }
      }

      "it receives a Status message" should {
        "update the metrics" in {
          monitor.tell(bound, httpListener.ref)
          val stats = new Stats(42.milliseconds, 42, 42, 42, 42, 42, 42, 42)
          monitor.tell(stats, httpListener.ref)
          // force processing a message
          monitor ! Identify(1)
          expectMsgType[ActorIdentity]
          val maybeMetrics = Kamon.metrics.find(metricsName, SprayServerMetrics.category)
          maybeMetrics shouldBe defined
          val typedMetrics = maybeMetrics.get.asInstanceOf[SprayServerMetrics]
          typedMetrics.stats shouldBe stats
        }
      }
    }
  }
}
