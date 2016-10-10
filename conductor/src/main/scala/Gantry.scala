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
         case Some(c) =>
            log.debug("Container exists")
            Future.successful(c)
         case _ =>
            log.debug("Creating container")
            for {
               _    <- image.createContainer()
               cOpt <- image.findContainer()
               c    <- cOpt.future
            } yield c
      }

   def startContainerIfNeeded(container: DockerContainer): Future[DockerContainer] =
      container.isRunning() flatMap {
         case true  =>
            log.debug("Container is running")
            Future.successful(container)
         case false =>
            log.debug("Starting container")
            container.start()
      }

   def stopContainerIfNeeded(containerOpt: Option[DockerContainer]): Future[Unit] =
      containerOpt.fold {
         Future.successful( () )
      }{ container =>
         container.isRunning().flatMap {
            case true  => container.stop().map( _ => () )
            case false => Future.successful( () )
         }
      }

   def normal: Receive = {

      case RunImage =>
         log.debug("Run image! "+ image.name)
         val realSender = sender
         for{
            created <- createContainerIfNeeded()
            started <- startContainerIfNeeded(created)
         } {
            realSender ! ImageRunning( image.copy( container = Some(started) ) )
         }

      case StopImage =>
         log.debug(s"Stop image ${image.name}")
         val initiator = sender
         for {
            container <- image.findContainer()
            _         <- stopContainerIfNeeded(container)
         } {
            initiator ! ImageStopped( image.copy( container = container ) )
         }
   }
}
