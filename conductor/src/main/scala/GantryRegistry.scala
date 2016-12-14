package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
import com.flurdy.conductor.docker._
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

object GantryRegistry {
   case class FoundGantry(gantry: ActorRef)
   case class GantryNotFound(details: ServiceDetails)
   case class FindGantry(details: ServiceDetails)
   def props()(implicit dockerClient: DockerClientApi = DockerClient, actorFactory: ActorFactory, featureToggles: FeatureToggles) =
      Props(classOf[GantryRegistry], dockerClient, actorFactory, featureToggles)
}

class GantryRegistry()(implicit val dockerClient: DockerClientApi, val actorFactory: ActorFactory, val featureToggles: FeatureToggles) extends GantryRegistryActor

trait GantryRegistryActor extends Actor with WithLogging with WithActorFactory with WithDockerClient with WithFeatureToggles {
   import GantryRegistry._

   def receive = normal

   def normal: Receive = {
      case FindGantry(details) =>
         val realSender = sender
         if(featureToggles.isDockerStubbed) {
            val gantry = actorFactory.actorOf(Gantry.props(DockerImage(details.name,"latest")))
            realSender ! FoundGantry(gantry)
         } else dockerClient.findImage(details.name).map{ imageOpt =>
            imageOpt.fold{
               log.debug(s"Gantry image not found: ${details.name}")
               realSender ! GantryNotFound(details)
            }{ image =>
               log.debug(s"Gantry image found: ${details.name}")
               val gantry = actorFactory.actorOf(Gantry.props(image))
               realSender ! FoundGantry(gantry)
            }
         }
   }
}
