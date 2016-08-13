package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
import argonaut._
import Argonaut._
import com.flurdy.conductor.docker._


object Gantry {
   case object RunImage
   case object ImageRunning
   def props(image: DockerImage) = Props(classOf[Gantry], image)
}

class Gantry(val image: DockerImage) extends GantryActor

trait GantryActor extends Actor with WithLogging {
   import Gantry._
   def image: DockerImage

   // def DockerImage
   def receive = normal


   def normal: Receive = {
      case RunImage =>
         log.debug("running image!")
   }

}