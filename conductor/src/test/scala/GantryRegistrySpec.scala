package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import GantryRegistry._
import scala.concurrent.duration._
import com.flurdy.conductor.docker.{DockerClientApi,DockerImage}
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class GantryRegistrySpec extends TestKit(ActorSystem("GantryRegistry"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with BeforeAndAfterAll {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   trait Setup {
      implicit val probeFactory = new ProbeFactory()
      val gantry = probeFactory.first
      val details = ServiceDetails(name="flurdy/dreamfactory")
      val dockerClientMock = mock[DockerClientApi]
      val gantryRegistry = system.actorOf(GantryRegistry.props(dockerClientMock))
   }

   "FindGantry" should {

      "find a docker image" in new Setup {

         when(dockerClientMock.findImage("flurdy/dreamfactory")).thenReturn(Some(DockerImage("flurdy/dreamfactory","latest")))

         gantryRegistry ! FindGantry(details)

         expectMsg( FoundGantry(gantry.ref) )

      }

      "not find an unknown docker image" in new Setup {

         when(dockerClientMock.findImage("flurdy/dreamfactory")).thenReturn(None)

         gantryRegistry ! FindGantry(details)

         expectMsg( GantryNotFound(details) )

      }
   }
}
