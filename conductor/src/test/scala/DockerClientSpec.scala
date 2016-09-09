package com.flurdy.conductor.docker

import com.google.common.collect.ImmutableList
import com.spotify.docker.client.{DefaultDockerClient, DockerClient => SpotifyDockerClient}
import com.spotify.docker.client.messages.{Container,ContainerConfig,ContainerCreation,Image}
import org.mockito.Mockito.{never,verify,when}
import org.mockito.Matchers.{any,eq => eqTo}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.flurdy.sander.primitives._

class DockerClientSpec extends WordSpecLike
                          with Matchers
                          with ScalaFutures
                          with MockitoSugar {

   trait Setup {
      def dockerClient: DockerClientApi
      val imageName = "flurdy/dreamfactory"
      val spotifyClientMock = mock[SpotifyDockerClient]
   }

   trait MockSpotifyClient {
      def spotifyClientMock: SpotifyDockerClient
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

         whenReady( dockerClient.findImage(imageName), timeout(2.seconds) ){ imageFound =>

            imageFound shouldBe Some(DockerImage(imageName,"latest"))
         }
      }
   }

   "isImageRunning" should {

      "find a container given image is running and up" in new Setup with MockSpotifyClient {

         val image = DockerImage(imageName)
         val containerMock = mock[Container]
         when(containerMock.image()).thenReturn(imageName)
         when(containerMock.status()).thenReturn("Up")
         when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

         whenReady( dockerClient.isImageRunning(image), timeout(2.seconds) )( _ shouldBe true )

      }

      "find a container given image is running and down" in new Setup with MockSpotifyClient {

         val image = DockerImage(imageName)
         val containerMock = mock[Container]
         when(containerMock.image()).thenReturn(imageName)
         when(containerMock.status()).thenReturn("Down")
         when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

         whenReady( dockerClient.isImageRunning(image), timeout(2.seconds) )( _ shouldBe false )

      }

      "not find a container given unknown image name" in new Setup with MockSpotifyClient {

         val image = DockerImage("notKnown")
         val containerMock = mock[Container]
         when(containerMock.image()).thenReturn(imageName)
         when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

         whenReady( dockerClient.isImageRunning(image), timeout(2.seconds) )( _ shouldBe false )

      }
   }

   "findContainer" should {
      "find a existing container" in new Setup with MockSpotifyClient {

         val image = DockerImage(imageName)
         val containerMock = mock[Container]
         when(containerMock.image()).thenReturn(imageName)
         when(containerMock.id()).thenReturn("container-id")
         when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

         whenReady( dockerClient.findContainer(image), timeout(2.seconds) ){ container =>

            container.map(_.imageName) shouldBe Some(imageName)
         }
      }

      "not find a missing container" in new Setup with MockSpotifyClient {

         val image = DockerImage("notKnown")
         val containerMock = mock[Container]
         when(containerMock.image()).thenReturn(imageName)
         when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

         whenReady( dockerClient.findContainer(image), timeout(2.seconds) ){ container =>

            container shouldBe None
         }
      }
   }

   "createContainer" should {
      "create a container" in new Setup with MockSpotifyClient {

         val image = DockerImage("phpmyadmin/phpmyadmin")
         val imageMock = mock[Image]
         when(spotifyClientMock.listImages(any[SpotifyDockerClient.ListImagesParam])).thenReturn(List(imageMock))
         val repoTags = new ImmutableList.Builder().add(s"phpmyadmin/phpmyadmin:latest").build()
         when(imageMock.repoTags()).thenReturn(repoTags)
         val containerMock = mock[ContainerCreation]
         when(spotifyClientMock.createContainer(any[ContainerConfig])).thenReturn(containerMock)
         when(containerMock.id()).thenReturn("container-id")

         whenReady(dockerClient.createContainer(image)){  container =>
            container.map(_.imageName) shouldBe Some("phpmyadmin/phpmyadmin")
         }

      }

      "not create a container given an unknown image" in new Setup with MockSpotifyClient {

         val image = DockerImage("notmyimage")
         when(spotifyClientMock.listImages(any[SpotifyDockerClient.ListImagesParam])).thenReturn(List.empty)

         whenReady(dockerClient.createContainer(image)){ container =>

            container shouldBe None
         }

         verify(spotifyClientMock,never()).createContainer(any[ContainerConfig])
       }
     }

     "startContainer" should {
        "start a container" in new Setup with MockSpotifyClient {

           val image = DockerImage("phpmyadmin/phpmyadmin")
           val containerMock = mock[Container]
           when(containerMock.image()).thenReturn(image.name)
           when(containerMock.id()).thenReturn("container-id")
           when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

           whenReady( dockerClient.startContainer("container-id") ){ containerOpt =>

              verify(spotifyClientMock).startContainer("container-id")
           }
        }
     }

     "stopContainer" should {
        "stop a container" in new Setup with MockSpotifyClient {

           val image = DockerImage("phpmyadmin/phpmyadmin")
           val containerMock = mock[Container]
           when(containerMock.image()).thenReturn(image.name)
           when(containerMock.id()).thenReturn("container-id")
           when(spotifyClientMock.listContainers(any[SpotifyDockerClient.ListContainersParam])).thenReturn(List(containerMock))

           whenReady( dockerClient.findContainer(image), timeout(2.seconds) ){ startedContainer =>

             startedContainer shouldBe defined

             whenReady( dockerClient.stopContainer(startedContainer.get.id) ){ _ =>

                verify( spotifyClientMock ).stopContainer(startedContainer.get.id,-1)
             }
          }
        }
    }
}
