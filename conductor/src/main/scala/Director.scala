package com.flurdy.conductor

import akka.actor.{Actor,ActorSystem,Props}
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

object Director {
   case class StartStackOrService(stackOrServiceName: String)
   case class StopStackOrService(stackOrServiceName: String)
   def props()(implicit actorFactory: ActorFactory) = Props(classOf[Director], actorFactory)
}

class Director()(implicit val actorFactory: ActorFactory) extends DirectorActor

trait DirectorActor extends Actor with WithLogging with WithActorFactory {
   import Director._
   import StackRegistry._
   import ServiceRegistry._
   import Stack._

   val serviceRegistry = actorFactory.actorOf(ServiceRegistry.props(), "service-registry")
   val stackRegistry   = actorFactory.actorOf(StackRegistry.props(serviceRegistry), "stack-registry")

   override def receive = normal

   def normal: Receive = {
      case StartStackOrService(stackOrServiceName) => {
         log.debug(s"Start a stack or service: $stackOrServiceName")
         stackRegistry ! FindAndStartStack(stackOrServiceName, self)
      }
      case StopStackOrService(stackOrServiceName) => {
         log.debug(s"Stop a stack or service: $stackOrServiceName")
         stackRegistry ! FindAndStopStack(stackOrServiceName)
      }
      case StackNotFound(possibleServiceName) => {
         log.debug(s"Not a stack: $possibleServiceName")
         serviceRegistry ! new FindAndStartServices(possibleServiceName, self)
      }
      case StackToStopNotFound(possibleServiceName) => {
         log.debug(s"Not a stack: $possibleServiceName")
         serviceRegistry ! new FindAndStopService(possibleServiceName, self)
      }
      case StackNotRunning(stackName) => {
         log.debug(s"Stack is not running: $stackName")
      }
      case ServiceNotFound(serviceName) => {
         log.warning(s"Service not found: $serviceName")
      }
      case ServicesStarted(_) => {
        log.info("Services started")
      }
      case ServicesStopped => {
        log.info("Services stopped")
      }
   }

}
