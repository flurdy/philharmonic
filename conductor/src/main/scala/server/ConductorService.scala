package com.flurdy.conductor.server

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import StatusCodes._
import Directives._
import com.flurdy.conductor._
import Director._

trait ConductorService {

   def log: LoggingAdapter
   def director: ActorRef
   implicit val timeout = Timeout(120 seconds)

   val myExceptionHandler = ExceptionHandler {
     case _: ArithmeticException =>
       extractUri { uri =>
         log.warning(s"Request to $uri could not be handled")
         complete(HttpResponse(InternalServerError, entity = "DOH!"))
       }
   }

   val route: Route =
    handleExceptions(myExceptionHandler) {
      pathPrefix("service" / Segment) { serviceName =>
         post {
            path("start"){
               complete{
                  log.info(s"Received request to start $serviceName")
                  ask(director, StartStackOrService(serviceName.toLowerCase))
                     .mapTo[Either[StackOrServiceNotFound,StackOrServiceFound]]
                     .map( r => r.fold[StatusCode]( _ => NotFound, _ => NoContent) )
                     .recover{ case _ => InternalServerError }
               }
            } ~
            path("stop"){
               complete{
                  log.info(s"Received request to stop $serviceName")
                  ask(director, StopStackOrService(serviceName.toLowerCase))
                     .mapTo[Either[StackOrServiceNotFound,StackOrServiceFound]]
                     .map( r => r.fold[StatusCode]( _ => NotFound, _ => NoContent) )
                     .recover{ case _ => InternalServerError }
               }
            }
         }
      }
   }
}
