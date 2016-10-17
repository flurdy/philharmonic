package com.flurdy.conductor

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import com.flurdy.sander.actor.{ActorFactory, WithActorFactory}

object Director {
   case class StartStackOrService(stackOrServiceName: String)
   case class StopStackOrService(stackOrServiceName: String)
   case class StackOrServiceFound(stackOrServiceName: String)
   case class StackOrServiceNotFound(stackOrServiceName: String)
   def props()(implicit actorFactory: ActorFactory) = Props(classOf[Director], actorFactory)
}

class Director()(implicit val actorFactory: ActorFactory) extends DirectorActor

trait DirectorActor extends Actor with WithLogging with WithActorFactory {
   import Director._
   import StackRegistry._
   import ServiceRegistry._
   import Stack._
   import context.dispatcher

   implicit val timeout = Timeout(90 seconds)

   val serviceRegistry = actorFactory.actorOf(ServiceRegistry.props(), "service-registry")
   val stackRegistry   = actorFactory.actorOf(StackRegistry.props(serviceRegistry), "stack-registry")

   override def receive = normal

   def normal: Receive = {
      case StartStackOrService(stackOrServiceName) =>
         log.debug(s"Start a stack or service: $stackOrServiceName")
         stackRegistry ! FindAndStartStack(stackOrServiceName, sender)

      case StopStackOrService(stackOrServiceName) =>
         log.debug(s"Stop a stack or service: $stackOrServiceName")
         stackRegistry ! FindAndStopStack(stackOrServiceName, sender)

      case StackToStartNotFound(possibleServiceName, initiator) =>
         log.debug(s"Not a stack: $possibleServiceName will try to start as a service")
         serviceRegistry ! FindAndStartServices(Seq(possibleServiceName), initiator)

      case StackFound(stackName, initiator) =>
         log.debug(s"Stack found: $stackName")
         initiator ! Right( StackOrServiceFound(stackName) )

      case StackToStopNotFound(possibleServiceName, initiator) =>
         log.debug(s"Not a stack: $possibleServiceName, will try to stop as a service")
         serviceRegistry ! FindAndStopService(possibleServiceName, initiator)

      case StackNotRunning(stackName) =>
         log.debug(s"Stack is not running: $stackName")

      case ServiceNotFound(serviceName, initiator) =>
         log.warning(s"Service not found: $serviceName")
         initiator ! Left(StackOrServiceNotFound(serviceName))

      case ServiceFound(serviceName, initiator) =>
         log.debug(s"Service found: $serviceName")
         initiator ! Right( StackOrServiceFound(serviceName) )

      case ServicesStarted(_) =>
        log.info("Services started")

      case ServicesStopped =>
        log.info("Services stopped")

   }

}
