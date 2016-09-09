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
      val dockerClientMock = mock[DockerClientApi]
      val gantry = system.actorOf(Gantry.props(image)(dockerClientMock), "gantry-under-test")
   }

   "RunImage" should {

      "try to run image" in new Setup {

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         when( dockerClientMock.isContainerRunning( eqTo(container) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( false ) )

         when( dockerClientMock.startContainer( eqTo("container-id") )(any[ExecutionContext]) )
            .thenReturn( Future.successful( () ) )

         when( dockerClientMock.findContainer( eqTo(image) )(any[ExecutionContext]) )
            .thenReturn( Future.successful( Some(container) ) )

         gantry ! RunImage

         val expected = image.copy(container = Some(container))
         expectMsg( ImageRunning( expected ) )

      }
   }
}
