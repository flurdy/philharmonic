package com.flurdy.conductor

import akka.actor._
import akka.testkit._
// import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import StackRegistry._
import ServiceRegistry._
import Stack._
import Service._
import GantryRegistry._
import Gantry._
import scala.concurrent.duration._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class StackSpec extends TestKit(ActorSystem("StackSpec"))
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
      lazy val stackRegistry   = TestProbe()
      lazy val serviceRegistry = TestProbe()
      lazy val service         = probeFactory.first
      val details = StackDetails(name="my-stack", Seq("my-service"))
      val stack = system.actorOf(
         Stack.props(details, stackRegistry.ref,  serviceRegistry.ref, initiator.ref))
   }

   "StartStack" should {

      "start stack" in new Setup {

         stack ! StartStack

         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-service"), stack) )

      }
   }

   "ServiceNotFound" should {

      "propagete message" in new Setup {

         stack ! StartStack

         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-service"), stack) )

         stack ! ServiceNotFound("my-service", initiator.ref)

         stackRegistry.expectMsg(  ServiceNotFound("my-service", initiator.ref) )

      }
   }

   "ServicesStarted" should {

      "propagate message" in new Setup {

         stack ! StartStack

         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-service"), stack) )

         stack ! ServicesStarted(Map("my-service" -> service.ref))

         stackRegistry.expectMsg(  StackStarted("my-stack", stack, initiator.ref) )

      }
   }

   "StopStack" should {

      "stop services" in new Setup {

         stack ! StartStack

         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-service"), stack) )

         stack ! ServicesStarted(Map("my-service" -> service.ref))

         stackRegistry.expectMsg(  StackStarted("my-stack", stack, initiator.ref) )

         stack ! StopStack

         serviceRegistry.expectMsg( StopServices( Map("my-service" -> service.ref), stack) )

      }
   }

   "ServicesStopped" should {

      "report stack stopped" in new Setup {

         stack ! StartStack
         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-service"), stack) )

         stack ! ServicesStarted(Map("my-service" -> service.ref))
         stackRegistry.expectMsg(  StackStarted("my-stack", stack, initiator.ref) )

         stack ! StopStack
         serviceRegistry.expectMsg( StopServices( Map("my-service" -> service.ref), stack) )

         stack ! ServicesStopped

         stackRegistry.expectMsg( StackStopped("my-stack", stack, initiator.ref) )
      }
   }
}
