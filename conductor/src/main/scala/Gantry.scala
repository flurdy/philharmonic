package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.flurdy.conductor.docker._
import com.flurdy.sander.primitives._


object Gantry {
   case object RunImage
   case object StopImage
   case class ImageRunning(image: DockerImage)
   case class ImageStopped(image: DockerImage)
   def props(image: DockerImage)(implicit dockerClient: DockerClientApi = DockerClient) =
         Props(classOf[Gantry], image, dockerClient)
}

class Gantry(val image: DockerImage)(implicit val dockerClient: DockerClientApi) extends GantryActor

trait GantryActor extends Actor with WithLogging with WithDockerClient {
   import Gantry._

   def image: DockerImage

   def receive = normal

   def createContainerIfNeeded(): Future[DockerContainer] =
      image.findContainer().flatMap {
         case Some(c) => Future.successful(c)
         case _ =>
            for {
               _    <- image.createContainer()
               cOpt <- image.findContainer()
               c    <- cOpt.future
            } yield c
      }

   def startContainerIfNeeded(container: DockerContainer): Future[DockerContainer] =
      container.isRunning() flatMap {
         case true  => Future.successful(container)
         case false => container.start()
      }

   def normal: Receive = {
      case RunImage =>
         log.debug("run image! "+ image.name)
         val realSender = sender
         for{
            created <- createContainerIfNeeded()
            started <- startContainerIfNeeded(created)
         } {
            realSender ! ImageRunning( image.copy( container = Some(started) ) )
         }
   }
}
