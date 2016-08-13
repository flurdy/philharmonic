package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import StackRegistry._
import Stack._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

import scala.concurrent.duration._

class StackRegistrySpec extends TestKit(ActorSystem("StackRegistrySpec"))
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
      lazy val stack = probeFactory.first
      val serviceRegistry = TestProbe()
      val stackRegistry = system.actorOf(StackRegistry.props(serviceRegistry.ref, actorFactory = probeFactory))
   }

   "FindAndStartStack" should {

      "not find an unknown stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-unknown-stack", self)

         expectMsg( StackNotFound("my-unknown-stack") )
      }

      "find and start stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", self)

         stack.expectMsg( StartStack )
      }
   }

   // "FindAndStopStack" should {
   //
   //    "not find an unknown stack" in new Setup {
   //
   //       stackRegistry ! FindAndStopStack("my-stack")
   //
   //       fail()
   //    }
   //
   //    "find and stop stack" in new Setup {
   //
   //       stackRegistry ! StackStarted("my-stack", stack.ref)
   //
   //       fail()
   //    }
   // }

   // "StackStarted" should {
   //
   //    "" in new Setup {
   //
   //       stackRegistry ! StackStopped("my-stack", stack.ref)
   //
   //       fail()
   //    }
   // }
   //
   // "StackStopped" should {
   //
   //    "" in new Setup {
   //       fail()
   //    }
   // }

}
