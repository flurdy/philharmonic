package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,PoisonPill,Props}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

case class ServiceDetails(name: String)

object Service {
   case class StartService(servicesToStart: Seq[String], initiator: ActorRef)
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
   var gantry:    Option[ActorRef] = None
   var initiator: Option[ActorRef] = None

   override def receive = normal

   def normal: Receive = {

      case StartService(servicesToStart, initiator) =>
         log.debug(s"Start ${details.name}")
         this.initiator = Some(initiator)
         gantryRegistry ! FindGantry(details)

      case FoundGantry(gantry) =>
         this.gantry = Some(gantry)
         context.become(startingGantry)
         gantry ! RunImage

      case StopService(services, initiator) =>
         log.warning(s"Service already stopped: ${details.name}")

      case GantryNotFound(details) =>
         log.warning(s"Gantry not found ${details.name}")
   }

   def startingGantry: Receive = {

      case StartService(_, _) =>
         log.warning(s"Service already starting: ${details.name}")

      case ImageRunning(image) =>
         log.debug("Image running")
         context.become(runningGantry)
         initiator.fold{
            log.error("Intitiator not set")
         }{ initiatorFound =>
            serviceRegistry ! ServiceStarted(details.name, Seq.empty, initiatorFound)
         }

      case StopService(services, initiator) =>
         log.debug("Stop starting image in the future")
         context.system.scheduler.scheduleOnce(200 milliseconds, self, StopService(services, initiator))
   }

   def runningGantry: Receive = {

      case StartService(_, _) =>
         log.warning(s"Service already running: ${details.name}")

      case StopService(services, initiator) =>
         log.debug(s"Stop ${details.name}")
         gantry.fold {
            log.warning(s"No gantry found ${details.name}")
         }{ gantry =>
            context.become(stoppingGantry)

            gantry ! StopImage
         }
   }

   def stoppingGantry: Receive = {
      case StartService(_, _) =>
         log.warning(s"Service stopping: ${details.name}")

      case ImageStopped(image) =>
         log.debug("Image stopped")
         context.become(normal)
         initiator.fold{
            log.error("Intitiator not set")
         }{ initiatorFound =>
            serviceRegistry ! ServiceStopped(details.name, self, Map.empty, initiatorFound)
         }

      case StopService(services, initiator) =>
         log.warning(s"Service stopping: ${details.name}")

   }
}
