package com.flurdy.conductor.server

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import com.flurdy.conductor._
// import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
// import spray.json.DefaultJsonProtocol._

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
                  director ! StartStackOrService(serviceName)
                  s"please start: $serviceName"
               }
            } ~
            path("stop"){
               complete(s"please stop: $serviceName")
            }
         }
      }
   }
}
