package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}

case class ServiceDetails(name: String)

object Service {
   case class StartService(servicesToStart: Seq[String],
                           initiator: ActorRef)
   case class StopService(services: Map[String, ActorRef], initiator: ActorRef)
   def props(details: ServiceDetails) = Props(classOf[Service], details)
}

class Service(val details: ServiceDetails) extends ServiceActor

trait ServiceActor extends Actor with WithLogging {
   import Service._
   import ServiceRegistry._

   def details: ServiceDetails

   override def receive = normal

   def normal: Receive = {
      case StartService(servicesToStart, initiator) => {
         log.debug(s"Start ${details.name}")

         // TODO Speak to docker gantry

         sender ! ServiceStarted(details.name, servicesToStart, initiator)
      }
      case StopService(services, initiator) => {
         log.debug(s"Stop ${details.name}")

         // TODO Speak to docker gantry

         sender ! ServiceStopped(details.name, self, services, initiator)
      }
   }
}
