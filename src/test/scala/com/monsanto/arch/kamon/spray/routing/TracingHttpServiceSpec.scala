package com.monsanto.arch.kamon.spray.routing

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.Tcp
import akka.testkit.{TestKit, TestProbe}
import kamon.Kamon
import kamon.metric.SingleInstrumentEntityRecorder
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpecLike, Matchers}
import spray.http._
import spray.httpx.RequestBuilding
import spray.routing.Rejection

import scala.concurrent.duration.DurationInt

class TracingHttpServiceSpec(_system: ActorSystem) extends TestKit(_system) with FreeSpecLike with BeforeAndAfterEach with BeforeAndAfterAll {
  import Matchers._
  import RequestBuilding.{Get, Post}
  import TracingHttpServiceSpec._

  def this() = this(ActorSystem(classOf[TracingHttpServiceSpec].getSimpleName))

  val subService = system.actorOf(Props(new SubServiceActor))
  val testService = system.actorOf(Props(new RootServiceActor(subService)))

  "the TracingHttpService" - {
    "responds like an HttpService when" - {
      "a TCP connection is established" in {
        val probe = TestProbe()
        probe.send(testService, Tcp.Connected(
          InetSocketAddress.createUnresolved("remote.example.com", 27358),
          InetSocketAddress.createUnresolved("host.example.com", 443)))
        probe.expectMsg(Tcp.Register(testService))
      }
      "a TCP connection is closed" in {
        val probe = TestProbe()
        probe.send(testService, Tcp.Closed)
        probe.expectNoMsg(250.milliseconds)
      }
    }

    "records metrics" - {
      def measurementExistsFor(path: String, timedOut: Boolean = false, statusCode: StatusCode = StatusCodes.OK,
                                method: HttpMethod = HttpMethods.GET): Unit = {
        import SingleInstrumentEntityRecorder._

        val tags = Map(
          "path" -> path,
          "method" -> method.value,
          "status-code" -> statusCode.intValue.toString,
          "timed-out" -> timedOut.toString
        )

        val entity = Kamon.metrics.find("spray-service-response-duration", Histogram, tags)
        entity shouldBe defined
        val collectionContext = Kamon.metrics.buildDefaultCollectionContext
        val entitySnapshot = entity.get.collect(collectionContext)
        val snapshot = entitySnapshot.histogram(SingleInstrumentEntityRecorder.Histogram).get
        snapshot.numberOfMeasurements shouldBe 1
      }

      def performRequest(request: Any, responseContent: String, responseStatus: StatusCode = StatusCodes.OK): Unit = {
        val probe = TestProbe()
        probe.send(testService, request)
        val response = probe.expectMsgType[HttpResponse]
        response shouldBe
          HttpResponse(
            status = responseStatus,
            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseContent))
      }


      "for a normal request" in {
        performRequest(Get("/"), "hello!")
        measurementExistsFor("/")
      }

      "when a request times out" - {
        "for the response that timed out" in {
          performRequest(Get("/timeout"), "Timed out!")
          measurementExistsFor("/timeout", timedOut = true)
        }

        "for the timeout response" in {
          import StatusCodes.InternalServerError

          performRequest(Timedout(Get("/timeout")),
            "The server was not able to produce a timely response to your request.",
            InternalServerError)
          measurementExistsFor("/timeout", statusCode = InternalServerError)
        }
      }

      "for an unhandled rejection" in {
        import StatusCodes.InternalServerError

        performRequest(Post("/reject-me"), "There was an internal server error.",
          responseStatus = InternalServerError)
        measurementExistsFor("/reject-me", statusCode = InternalServerError, method = HttpMethods.POST)
      }

      "when a response is generated via a subservice" - {
        "with a regular response" in {
          performRequest(Get("/sub"), "I am the sub.")
          measurementExistsFor("/sub")
        }

        "when the response times out" - {
          "for the response that timed out" in {
            performRequest(Get("/sub/timeout"), "Timed out!")
            measurementExistsFor("/sub/timeout", timedOut = true)
          }

          "for the timeout response" in {
            import StatusCodes.InternalServerError

            performRequest(Timedout(Get("/sub/timeout")),
              "The server was not able to produce a timely response to your request.",
              InternalServerError)
            measurementExistsFor("/sub/timeout", statusCode = InternalServerError)
          }
        }
      }
    }
  }


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Kamon.start()
  }


  override protected def afterEach(): Unit = {
    Kamon.shutdown()
    super.afterEach()
  }

  override protected def afterAll(): Unit = {
    shutdown(system)
    super.afterAll()
  }
}

object TracingHttpServiceSpec {
  class RootServiceActor(sub: ActorRef) extends TracingHttpServiceActor {
    override def receive =
      runRoute {
        get {
          pathEndOrSingleSlash {
            complete("hello!")
          } ~
          path("timeout") {
            detach(actorRefFactory.dispatcher) {
              complete {
                Thread.sleep(500)
                "Timed out!"
              }
            }
          }
        } ~
        pathPrefix("sub") {
          sub ! _
        } ~
        post {
          path("reject-me") {
            reject(new Rejection {})
          }
        }
      }
  }
  
  class SubServiceActor extends TracingHttpServiceActor {
    override def receive =
      runRoute {
        get {
          pathEndOrSingleSlash {
            complete("I am the sub.")
          } ~
          path("timeout") {
            detach(actorRefFactory.dispatcher) {
              complete {
                Thread.sleep(500)
                "Timed out!"
              }
            }
          }
        }
      }
  }
}
