package com.flurdy.conductor.docker

import com.spotify.docker.client.{DefaultDockerClient, DockerClient => SpotifyDockerClient}
import SpotifyDockerClient.ListImagesParam
import scala.collection.JavaConversions._
import com.flurdy.sander.primitives._

trait WithDockerClient {
   def dockerClient: DockerClientApi
}

case class DockerImage(name: String, version: String = "latest")

object DockerClient extends DockerClientApi{
   override lazy val client = DefaultDockerClient.fromEnv().build()
}

trait DockerClientApi {

   def client: SpotifyDockerClient

   def findImage(name: String): Option[DockerImage] = {
      client.listImages(ListImagesParam.byName(name)).headOption.flatMap{ imageFound =>
         if(imageFound.repoTags().exists( _ == s"$name:latest"))
            Some(DockerImage(name,"latest"))
         else None
      }
   }
}
