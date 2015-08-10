package com.monsanto.arch.kamon.spray.can

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.monsanto.arch.kamon.spray.can.server.SprayServerMetrics
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Entity
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.can.Http
import spray.http.HttpResponse
import spray.httpx.RequestBuilding.Get
import spray.routing.HttpServiceActor

/** Integration test for the Kamon spray extension.
  *
  * @author Daniel Solano Gómez
  */
class KamonHttpSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  import KamonHttpSpec._

  def this() = this(ActorSystem("kamon-http-spec"))

  override protected def beforeAll(): Unit = {
    Kamon.start()
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(_system, verifySystemShutdown = true)
    Kamon.shutdown()
    super.afterAll()
  }

  "The KamonHttp extension should work" in {
    val service = system.actorOf(Props(new TestServiceActor))
    IO(KamonHttp) ! Http.Bind(service, interface = "localhost", port = 0)
    val bound = expectMsgType[Http.Bound]
    val httpListener = lastSender
    val entity = Entity(s"${bound.localAddress.getHostName}:${bound.localAddress.getPort}", SprayServerMetrics.category)

    IO(KamonHttp) ! Get(s"http://localhost:${bound.localAddress.getPort}/")
    val response = expectMsgType[HttpResponse]

    response.entity.asString shouldBe ResponseText

    // wait for metrics to update
    Thread.sleep(RefreshIntervalMillis * 3)
    val maybeMetrics = Kamon.metrics.find(entity)
    maybeMetrics shouldBe defined
    val stats = maybeMetrics.get.asInstanceOf[SprayServerMetrics].stats

    stats.uptime.toNanos should be > 0L
    stats.maxOpenConnections shouldBe 1
    stats.maxOpenRequests shouldBe 1
    stats.totalConnections shouldBe 1
    stats.totalRequests shouldBe 1
    stats.openConnections should be <= 1L
    stats.openRequests shouldBe 0

    httpListener ! Http.Unbind
    expectMsg(Http.Unbound)
  }
}

object KamonHttpSpec {
  val RefreshIntervalMillis = new KamonHttpSettings(ConfigFactory.load()).refreshInterval.toMillis
  val ResponseText = "Hello"

  class TestServiceActor extends HttpServiceActor {
    override def receive = runRoute {
      complete {
        Thread.sleep(RefreshIntervalMillis)
        ResponseText
      }
    }
  }
}
