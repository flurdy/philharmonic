package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._, Argonaut._
import com.flurdy.conductor.docker._
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

object GantryRegistry {
   case class FoundGantry(gantry: ActorRef)
   case class GantryNotFound(details: ServiceDetails)
   case class FindGantry(details: ServiceDetails)
   def props()(implicit dockerClient: DockerClientApi = DockerClient, actorFactory: ActorFactory) =
      Props(classOf[GantryRegistry], dockerClient, actorFactory)
}

class GantryRegistry()(implicit val dockerClient: DockerClientApi, val actorFactory: ActorFactory) extends GantryRegistryActor

trait GantryRegistryActor extends Actor with WithLogging with WithActorFactory with WithDockerClient {
   import GantryRegistry._

   def receive = normal

   def normal: Receive = {
      case FindGantry(details) =>
         val realSender = sender
         dockerClient.findImage(details.name).map{ imageOpt =>
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
