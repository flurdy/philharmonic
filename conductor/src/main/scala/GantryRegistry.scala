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
   def props(dockerClient: DockerClientApi = DockerClient)(implicit actorFactory: ActorFactory) = {
      Props(classOf[GantryRegistry], dockerClient, actorFactory)
   }
}

class GantryRegistry(val dockerClient: DockerClientApi)(implicit val actorFactory: ActorFactory) extends GantryRegistryActor

trait GantryRegistryActor extends Actor with WithLogging with WithActorFactory with WithDockerClient {
   import GantryRegistry._

   def receive = normal

   def normal: Receive = {
      case FindGantry(details) =>
         dockerClient.findImage(details.name).fold{
            sender ! GantryNotFound(details)
         }{ image =>
            val gantry = actorFactory.actorOf(Gantry.props(image))
            sender ! FoundGantry(gantry)
         }
   }
}
