package com.flurdy.conductor.docker

import com.spotify.docker.client.{DefaultDockerClient, DockerClient => SpotifyDockerClient}
import com.spotify.docker.client.messages.{Container,ContainerConfig}
import SpotifyDockerClient.{ListImagesParam,ListContainersParam}
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext,Future}
import com.flurdy.sander.primitives._

trait WithDockerClient {
   implicit def dockerClient: DockerClientApi
}

object DockerImage {
   type ContainerId = String
}

import DockerImage._

case class DockerContainer(id: DockerImage.ContainerId, imageName: String){
   require( id != null )

   def start()(implicit dockerClient: DockerClientApi, context: ExecutionContext) =
      dockerClient.startContainer(id).map( _ => this )

   def stop()(implicit dockerClient: DockerClientApi, context: ExecutionContext) =
      dockerClient.stopContainer(id).map( _ => this )

   def isRunning()(implicit dockerClient: DockerClientApi, context: ExecutionContext) = dockerClient.isContainerRunning(this)

}

case class DockerImage(name: String, version: String = "latest", container: Option[DockerContainer] = None){

   val nameAndVersion = s"$name:$version"

   def isRunning()(implicit dockerClient: DockerClientApi, context: ExecutionContext) = dockerClient.isImageRunning(this)

   def hasContainer()(implicit dockerClient: DockerClientApi, context: ExecutionContext) = dockerClient.hasContainer(this)

   def createContainer()(implicit dockerClient: DockerClientApi, context: ExecutionContext) = dockerClient.createContainer(this)

   def findContainer()(implicit dockerClient: DockerClientApi, context: ExecutionContext) = dockerClient.findContainer(this)

}

object DockerClient extends DockerClientApi{
   override lazy val client = DefaultDockerClient.fromEnv().build()
}

trait DockerClientApi {

   def client: SpotifyDockerClient

   def findImage(name: String)(implicit context: ExecutionContext): Future[Option[DockerImage]] =
      Future {
         client.listImages(ListImagesParam.byName(name)).headOption.flatMap{ imageFound =>
            if(imageFound.repoTags().exists( _ == s"$name:latest"))
               Some(DockerImage(name,"latest"))
            else None
         }
      }

   private def findImageContainer(image: DockerImage)(implicit context: ExecutionContext): Future[List[Container]] =
      Future {
         client.listContainers(ListContainersParam.allContainers(true))
               .filter( c => c.image == image.name || c.image == image.nameAndVersion )
               .toList
      }

   def isImageRunning(image: DockerImage)(implicit context: ExecutionContext): Future[Boolean] =
      findImageContainer(image)
         .map{ containers =>
            containers.filter(_.status.startsWith("Up")).nonEmpty
         }


   def isContainerRunning(container: DockerContainer)(implicit context: ExecutionContext): Future[Boolean] =
      for{
         imageOpt   <- findImage(container.imageName)
         image      <- imageOpt.future
         containers <- findImageContainer(image)
      } yield containers.filter(_.status.startsWith("Up")).nonEmpty

   def hasContainer(image: DockerImage)(implicit context: ExecutionContext): Future[Boolean] =
      findImageContainer(image).map( _.nonEmpty )

   def findContainer(image: DockerImage)(implicit context: ExecutionContext): Future[Option[DockerContainer]] =
      findImageContainer(image)
            .map{ containers =>
               containers.headOption
                         .map( c => DockerContainer(c.id, image.name) )
            }

   def createContainer(image: DockerImage)(implicit context: ExecutionContext): Future[Option[DockerContainer]] =
      findImage(image.name).map { imageOpt =>
         imageOpt.map { i =>
            val config = ContainerConfig.builder()
                                    .image(i.nameAndVersion)
                                    .build()
            val containerId = client.createContainer(config).id
            DockerContainer( containerId, i.name)
         }
      }

   def startContainer(id: DockerImage.ContainerId)(implicit context: ExecutionContext): Future[Unit] = Future( client.startContainer(id) )

   def stopContainer(id: DockerImage.ContainerId)(implicit context: ExecutionContext): Future[Unit] = Future ( client.stopContainer(id,-1) )

   // val info = docker.inspectContainer("containerID");
}
