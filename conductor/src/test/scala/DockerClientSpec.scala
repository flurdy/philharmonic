package com.flurdy.conductor.docker


import com.google.common.collect.ImmutableList
import com.spotify.docker.client.{DefaultDockerClient, DockerClient => SpotifyDockerClient}
import com.spotify.docker.client.messages.Image
import org.mockito.Mockito.when
import org.mockito.Matchers.{any,eq => eqTo}
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConversions._

class DockerClientSpec extends WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with BeforeAndAfterAll {

   override def afterAll = {
   }

   trait Setup {
      def dockerClient: DockerClientApi
      val imageName = "flurdy/dreamfactory"
   }

   trait MockSpotifyClient {
      val spotifyClientMock = mock[SpotifyDockerClient]
      val dockerClient = new DockerClientApi{
         override val client = spotifyClientMock
      }
   }

   trait RealSpotifyClient {
      val dockerClient = new DockerClientApi{
         override val client = DefaultDockerClient.fromEnv().build()
      }
   }

   "findImage" should {

      "find a docker image" in new Setup with MockSpotifyClient {
         val imageMock = mock[Image]
         when(spotifyClientMock.listImages(any[SpotifyDockerClient.ListImagesParam])).thenReturn(List(imageMock))
         val repoTags = new ImmutableList.Builder().add(s"$imageName:latest").build()
         when(imageMock.repoTags()).thenReturn(repoTags)

         val imageFound = dockerClient.findImage(imageName)

         imageFound shouldBe Some(DockerImage(imageName,"latest"))
      }
   }
}
