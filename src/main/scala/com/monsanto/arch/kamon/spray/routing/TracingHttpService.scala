package com.monsanto.arch.kamon.spray.routing

import akka.actor.{Actor, ActorContext, ActorRefFactory}
import akka.io.Tcp
import kamon.Kamon
import kamon.metric.instrument.Time
import kamon.trace.Tracer
import spray.can.server.ServerSettings
import spray.http.{HttpRequest, StatusCodes, Timedout}
import spray.routing._
import spray.util.LoggingContext

import scala.util.control.NonFatal

/** A drop-in replacement for Spray routing’s HttpServiceBase that provides better Kamon metric reporting.
  *
  * @author Daniel Solano Gómez
  */
trait TracingHttpServiceBase extends Directives {
  /** Supplies the actor behaviour for executing the given route. */
  def runRoute(route: Route)
              (implicit eh: ExceptionHandler, rh: RejectionHandler, ac: ActorContext, rs: RoutingSettings,
               log: LoggingContext): Actor.Receive =
    runRoute(route, isTimeout = false)

  /** ‘Seals’ a route by wrapping it with exception handling and rejection conversion.  This also takes care of adding
    * the necessary Kamon instrumentation.
    *
    * @param route the route to seal
    * @param timeoutNanos the request timeout in nanoseconds
    * @param isTimeout when set to true, indicates that we are sealing a route for a a timeout response, in which case
    *                  `timeoutNanos` will be added to the duration to approximate the duration the client experienced
    */
  def sealRoute(route: Route, timeoutNanos: Long, isTimeout: Boolean)
               (implicit eh: ExceptionHandler, rh: RejectionHandler): Route = {
    mapRequestContext { ctx: RequestContext ⇒
      val path = ctx.request.uri.path.toString()
      val method = ctx.request.method.name
      val start = System.nanoTime()
      val tagBuilder = Map.newBuilder[String, String]
      tagBuilder += "path" → path
      tagBuilder += "method" → method
      ctx.withHttpResponseMapped { response ⇒
        val duration = System.nanoTime() - start
        tagBuilder += "status-code" → response.status.intValue.toString
        val timedOut = duration > timeoutNanos
        tagBuilder += "timed-out" → timedOut.toString
        val realDuration = if (isTimeout) duration + timeoutNanos else duration
        Kamon.metrics.histogram("spray-service-response-duration", tagBuilder.result(), Time.Nanoseconds)
          .record(realDuration)
        response
      }
    } {
      (handleExceptions(eh) & handleRejections(sealRejectionHandler(rh)))(route)
    }
  }

  /** Creates the sealed rejection handler.  First applies the given handler, then falls back to Spray’s default, and
    * finally falls back to `handleUnhandledRejections`.
    */
  def sealRejectionHandler(rh: RejectionHandler): RejectionHandler =
    rh.orElse(RejectionHandler.Default).orElse(handleUnhandledRejections)

  /** The last-chance rejection handler.  Just logs by default. */
  def handleUnhandledRejections: RejectionHandler.PF = {
    case rejections ⇒ sys.error(s"Unhandled rejections: ${rejections.mkString(", ")}")
  }

  /** The route that provides a response when the server times out. */
  def timeoutRoute: Route =
    complete(StatusCodes.InternalServerError → "The server was not able to produce a timely response to your request.")

  /** Actually does the real work of running the route, taking into consideration whether it is a timeout. */
  private def runRoute(route: Route, isTimeout: Boolean)
                      (implicit eh: ExceptionHandler, rh: RejectionHandler, ac: ActorContext, rs: RoutingSettings,
                       log: LoggingContext): Actor.Receive = {
    val sealedExceptionHandler = eh.orElse(ExceptionHandler.default)
    val requestTimeout = ServerSettings(ac.system).requestTimeout
    val sealedRoute = sealRoute(route, requestTimeout.toNanos, isTimeout)(sealedExceptionHandler, rh)
    def runSealedRoute(ctx: RequestContext): Unit = {
      try {
        sealedRoute(ctx)
      } catch {
        case NonFatal(e) ⇒
          log.error(s"An error! $e")
          val errorRoute = sealedExceptionHandler(e)
          errorRoute(ctx)
      }
    }

    {
      case request: HttpRequest ⇒
        val ctx = RequestContext(request, ac.sender(), request.uri.path).withDefaultSender(ac.self)
        runSealedRoute(ctx)
      case ctx: RequestContext ⇒ runSealedRoute(ctx)
      case Tcp.Connected(remote, local) ⇒
        ac.sender() ! Tcp.Register(ac.self)
      case x: Tcp.ConnectionClosed ⇒
      case Timedout(request) ⇒ runRoute(timeoutRoute, isTimeout = true)(eh, rh, ac, rs, log)(request)
    }
  }
}

/** Use this where you would use the `HttpService` from Spray routing. */
object TracingHttpService extends TracingHttpServiceBase

/** Use this where you would use the `HttpService` from Spray routing. */
trait TracingHttpService extends TracingHttpServiceBase {
  implicit def actorRefFactory: ActorRefFactory
}

/** Use this where you would use the `HttpServiceActor` from Spray routing. */
abstract class TracingHttpServiceActor extends Actor with TracingHttpService {
  override def actorRefFactory = context
}
