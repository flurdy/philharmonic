package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import StackRegistry._
import ServiceRegistry._
import Service._
import GantryRegistry._
import Gantry._
import com.flurdy.conductor.docker.DockerImage
import scala.concurrent.duration._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class ServiceSpec extends TestKit(ActorSystem("ServiceSpec"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with BeforeAndAfterAll {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   trait Setup {
      val probeFactory = new ProbeFactory()
      lazy val initiator       = TestProbe()
      lazy val gantry          = TestProbe()
      lazy val gantryRegistry  = TestProbe()
      lazy val serviceRegistry = TestProbe()
      val details = ServiceDetails(name="my-service")
      val image = DockerImage("my-service")
      val service = system.actorOf(
         Service.props(details, serviceRegistry.ref, gantryRegistry.ref)(actorFactory = probeFactory))
   }

   "StartService" should {

      "start service" in new Setup {

         service ! StartService(Seq.empty)

         gantryRegistry.expectMsg( FindGantry(details) )

      }
   }

   "FoundGantry" should {

      "find and run gantry image" in new Setup {

         service ! FoundGantry(gantry.ref)

         gantry.expectMsg( RunImage )

      }
   }

   "ImageRunning" should {

      "report service started" in new Setup {

         service ! ImageRunning(image)

         serviceRegistry.expectMsg( ServiceStarted("my-service", Seq.empty, serviceRegistry.ref)  )

      }
   }

   "StopService" should {

      "stop service" in new Setup {

         service ! ImageRunning(image)

         service ! StopService(Map.empty, initiator.ref)

         expectMsg( ServiceStopped("my-service", service, Map.empty, initiator.ref) )

      }
   }
}
