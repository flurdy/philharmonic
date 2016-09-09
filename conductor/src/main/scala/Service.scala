package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._
import Argonaut._
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

case class ServiceDetails(name: String)

object Service {
   case class StartService(servicesToStart: Seq[String])
   case class StopService(services: Map[String, ActorRef], initiator: ActorRef)
   def props(details: ServiceDetails, serviceRegistry: ActorRef,
             gantryRegistry: ActorRef)(implicit actorFactory: ActorFactory) =
         Props(classOf[Service], details, serviceRegistry, gantryRegistry, actorFactory)
}

class Service(val details: ServiceDetails,
              val serviceRegistry: ActorRef,
              val gantryRegistry: ActorRef)
              (implicit val actorFactory: ActorFactory) extends ServiceActor

trait ServiceActor extends Actor with WithLogging with WithActorFactory{
   import Service._
   import ServiceRegistry._
   import GantryRegistry._
   import Gantry._


   def details: ServiceDetails
   def serviceRegistry: ActorRef
   def gantryRegistry: ActorRef
   var gantry: Option[ActorRef] = None

   override def receive = normal

   def normal: Receive = {
      case StartService(servicesToStart) => {
         log.debug(s"Start ${details.name}")
         gantryRegistry ! FindGantry(details)
      }
      case FoundGantry(gantry) =>
         this.gantry = Some(gantry)
         gantry ! RunImage
      case ImageRunning(image) =>
         context.become(runningGantry)
         serviceRegistry ! ServiceStarted(details.name, Seq.empty, serviceRegistry)
   }

   def runningGantry: Receive = {
      case StopService(services, initiator) => {
         log.debug(s"Stop ${details.name}")

         // TODO Speak to docker gantry

         sender ! ServiceStopped(details.name, self, services, initiator)
      }
   }
}
