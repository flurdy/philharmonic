package com.flurdy.conductor

import akka.actor.{Actor,ActorSystem,Props}

object Director {
   case class StartStackOrService(stackOrServiceName: String)
  //  case class ServiceNotFound(ServiceName: String)
  //  case object ServicesStarted
   def props() = Props(classOf[Director])
}

class Director extends DirectorActor

trait DirectorActor extends Actor with WithLogging {
   import Director._
   import StackRegistry._
   import ServiceRegistry._
   import Stack._

   val serviceRegistry = context.actorOf(ServiceRegistry.props(), "service-registry")
   val stackRegistry   = context.actorOf(StackRegistry.props(serviceRegistry), "stack-registry")

   override def receive = normal

   def normal: Receive = {
      case StartStackOrService(stackOrServiceName) => {
         log.debug(s"Start a stack or service: $stackOrServiceName")
         stackRegistry ! FindAndStartStack(stackOrServiceName)
      }
      case StackNotFound(possibleServiceName) => {
         log.debug(s"Not a stack: $possibleServiceName")
         serviceRegistry ! new FindAndStartServices(possibleServiceName, self)
      }
      case ServiceNotFound(serviceName) => {
         log.warning(s"Service not found: $serviceName")
      }
      case ServicesStarted => {
        log.info("Services started")
      }
   }

}
