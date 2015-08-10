package com.monsanto.arch.kamon.spray.can.server

import akka.actor.{Terminated, ActorRef, ActorNotFound, ActorSystem}
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import kamon.Kamon
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.can.Http

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SprayProxySpec(_system: ActorSystem) extends TestKit(_system) with Matchers with WordSpecLike with BeforeAndAfterAll with ImplicitSender with BeforeAndAfterEach {
  def this() = this(ActorSystem("kamon-spray-proxy-spec"))

  val listenerProbe = TestProbe()
  var sprayProxy: ActorRef = _


  override protected def beforeEach(): Unit = {
    sprayProxy = system.actorOf(SprayProxy.props(TestSettings), "test-proxy")
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    try super.afterEach()
    finally {
      watch(sprayProxy)
      system.stop(sprayProxy)
      expectMsgType[Terminated]
      IO(Http) ! Http.CloseAll
      expectMsg(Http.ClosedAll)
    }
  }

  override protected def beforeAll(): Unit = {
    Kamon.start()
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    try super.afterAll()
    finally {
      TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
      Kamon.shutdown()
    }
  }

  "the Kamon HTTP manager" should {
    "act just like IO(Http)" when {
      "creating a server" in {
        sprayProxy ! Http.Bind(listenerProbe.ref, interface = "localhost", port = 0)
        // should get bound message
        expectMsgType[Http.Bound]
        // bound message should appear to come from Spray
        val listener = lastSender
        listener.path.name should startWith("listener-")
        listener.path.parent.name shouldBe "IO-HTTP"
        // we are able to successfully unbind
        listener ! Http.Unbind
        expectMsg(Http.Unbound)
        expectNoMsg(100.milliseconds)
      }

      "failing to create a server" in {
        IO(Http) ! Http.Bind(listenerProbe.ref, interface = "localhost", port = 0)
        val bound = expectMsgType[Http.Bound]
        // now try to bind a new server to the same port
        val bind = Http.Bind(listenerProbe.ref, interface = "localhost", port = bound.localAddress.getPort)
        sprayProxy ! bind
        // should get bound message
        val failedCommand = expectMsgType[Http.CommandFailed]
        failedCommand.cmd shouldBe bind
        // failure message should appear to come from Spray
        val listener = lastSender
        listener.path.name should startWith("listener-")
        listener.path.parent.name shouldBe "IO-HTTP"
      }
    }

    "create a new monitor for a successful bind" in {
      sprayProxy ! Http.Bind(listenerProbe.ref, interface = "localhost", port = 0)
      expectMsgType[Http.Bound]
      val eventualMonitor = system.actorSelection(sprayProxy.path / "monitor-0").resolveOne(100.milliseconds)
      Await.ready(eventualMonitor, 100.millis)
      eventualMonitor.value should matchPattern { case Some(Success(_)) ⇒ }
    }

    "destroy the monitor after an unsuccessful bind" in {
      IO(Http) ! Http.Bind(listenerProbe.ref, interface = "localhost", port = 0)
      val bound = expectMsgType[Http.Bound]
      // now try to bind a new server to the same port
      val bind = Http.Bind(listenerProbe.ref, interface = "localhost", port = bound.localAddress.getPort)

      sprayProxy ! bind
      expectMsgType[Http.CommandFailed]
      val eventualMonitor = system.actorSelection(sprayProxy.path / "monitor-0").resolveOne(100.milliseconds)
      Await.ready(eventualMonitor, 100.millis)
      eventualMonitor.value should matchPattern { case Some(Failure(ActorNotFound(_))) ⇒ }
    }
  }
}
