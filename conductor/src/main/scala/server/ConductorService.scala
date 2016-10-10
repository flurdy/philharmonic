package com.flurdy.conductor.server

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import com.flurdy.conductor._

trait ConductorService {
   import Director._

   def log: LoggingAdapter
   def director: ActorRef

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
                  log.info(s"Starting $serviceName")
                  director ! StartStackOrService(serviceName.toLowerCase)
                  NoContent
               }
            } ~
            path("stop"){
               complete{
                  log.info(s"Stopping $serviceName")
                  director ! StopStackOrService(serviceName.toLowerCase)
                  NoContent
               }
            }
         }
      }
   }
}
