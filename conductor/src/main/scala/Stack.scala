package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}

case class StackDetails(name: String, services: Seq[String])

object Stack {
   case class StartStack(initiator: ActorRef)
   case object StopStack
   case class ServicesStarted(services: Map[String, ActorRef])
   case class StartServices(services: Seq[String])
   case object ServicesStopped
   def props(details: StackDetails, stackRegistry: ActorRef, serviceRegistry: ActorRef, initiator: ActorRef) = Props(classOf[Stack], details, stackRegistry, serviceRegistry, initiator)
}

class Stack(val details: StackDetails,
            val stackRegistry: ActorRef,
            val serviceRegistry: ActorRef,
            val initiator: ActorRef) extends StackActor

trait StackActor extends Actor with WithLogging {
   import Stack._
   import StackRegistry._
   import ServiceRegistry._

   def details: StackDetails
   def stackRegistry: ActorRef
   def serviceRegistry: ActorRef
   def initiator: ActorRef

   var runningServices: Map[String, ActorRef] = Map.empty

   override def receive = normal

   def normal: Receive = {
      case StartStack => {
         log.debug(s"Start ${details.name}")
         context.become(startingServices)
         serviceRegistry ! FindAndStartServices(details.services, self)
      }
   }

   def startingServices: Receive = {
      case ServiceNotFound(serviceName, initiator) => {
         log.warning(s"Services $serviceName not found")
         stackRegistry ! ServiceNotFound(serviceName, initiator)
      }
      case ServicesStarted(services) => {
         log.debug(s"Services of ${details.name} started")
         this.runningServices = services
         context.become(servicesRunning)
         stackRegistry ! StackStarted(details.name, self, initiator)
      }
   }

   def servicesRunning: Receive = {
      case StopStack => {
         log.debug(s"Stop ${details.name}")
         context.become(stoppingServices)
         serviceRegistry ! StopServices(runningServices, self)
      }
   }

   def stoppingServices: Receive = {
     case ServicesStopped => {
        log.debug(s"Services of ${details.name} stopped")
        this.runningServices = Map.empty
        context.become(normal)
        stackRegistry ! StackStopped(details.name, self)
      }
   }
}
