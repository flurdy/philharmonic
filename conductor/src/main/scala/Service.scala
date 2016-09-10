package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
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

      case StartService(servicesToStart) =>
         log.debug(s"Start ${details.name}")
         gantryRegistry ! FindGantry(details)

      case FoundGantry(gantry) =>
         this.gantry = Some(gantry)
         context.become(startingGantry)
         gantry ! RunImage

      case StopService(services, initiator) =>
         log.warning(s"Service already stopped: ${details.name}")

   }

   def startingGantry: Receive = {

      case StartService(servicesToStart) =>
         log.warning(s"Service already starting: ${details.name}")

      case ImageRunning(image) =>
         context.become(runningGantry)
         serviceRegistry ! ServiceStarted(details.name, Seq.empty, serviceRegistry)

      case StopService(services, initiator) =>
         // TODO: schedule stop msg

   }

   def runningGantry: Receive = {

      case StartService(servicesToStart) =>
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
      case StartService(servicesToStart) =>
         log.warning(s"Service stopping: ${details.name}")

      case ImageStopped(image) =>
         context.become(normal)
         // serviceRegistry ! ServiceStopped(details.name, Seq.empty, serviceRegistry)
         serviceRegistry ! ServiceStopped(details.name, self, Map.empty, serviceRegistry)

      case StopService(services, initiator) =>
         log.warning(s"Service stopping: ${details.name}")

   }
}
