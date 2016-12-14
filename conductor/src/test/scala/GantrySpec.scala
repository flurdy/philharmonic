package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import Gantry._
import org.mockito.Mockito.{never,verify,when}
import org.mockito.Matchers.{any,eq => eqTo}
import com.flurdy.conductor.docker._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class GantrySpec extends TestKit(ActorSystem("GantrySpec"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with BeforeAndAfterAll {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   trait Setup {
      val image = DockerImage("my-service")
      val container = DockerContainer("container-id"," my-service")
      val imageWithContainer = image.copy(container = Some(container) )
      val dockerClientMock = mock[DockerClientApi]
      implicit val featureToggles: FeatureToggles = new DefaultFeatureToggles()
      val gantry = system.actorOf(Gantry.props(image)(dockerClientMock, featureToggles))
   }

   "RunImage" should {

      "return running when starting container given container was not running" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( false ) )

         when( dockerClientMock.startContainer( eqTo("container-id") )(any[ExecutionContext]) )
            .thenReturn( Future.successful( () ) )

         gantry ! RunImage

         val expected = image.copy(container = Some(container))
         expectMsg( ImageRunning( expected ) )

      }

      "return running when starting container given container did not exist" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( None) )

         when( dockerClientMock.createContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( false ) )

         when( dockerClientMock.startContainer( eqTo("container-id") )(any[ExecutionContext]) )
            .thenReturn( Future.successful( () ) )

         gantry ! RunImage

         val expected = image.copy(container = Some(container))
         expectMsg( ImageRunning( expected ) )

      }

      "return running given container is running" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container)) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( true ) )

         gantry ! RunImage

         val expected = image.copy(container = Some(container))
         expectMsg( ImageRunning( expected ) )

      }
   }

   "StopImage" should {

      "return stopped given container is not running" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( false ) )

         gantry ! StopImage

         expectMsg(  ImageStopped(imageWithContainer) )
      }

      "return stopped given container does not exist" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( None ) )

         gantry ! StopImage

         expectMsg(  ImageStopped(image) )
      }

      "return stopped when stopping container given container was running" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( true ) )

         when( dockerClientMock.stopContainer( eqTo("container-id") )(any[ExecutionContext]) )
            .thenReturn( Future.successful( () ) )

         gantry ! StopImage

         expectMsg(  ImageStopped(imageWithContainer) )
      }
   }
}
