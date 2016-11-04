package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import Director._
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
      val probeFactory    = new ProbeFactory()
      lazy val stack      = probeFactory.first
      lazy val stack2     = probeFactory.second
      val serviceRegistry = TestProbe()
      val initiator       = TestProbe()
      val stackRegistry   = system.actorOf(StackRegistry.props(serviceRegistry.ref)(actorFactory = probeFactory))
   }

   "FindAndStartStack" should {

      "not find an unknown stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-unknown-stack", initiator.ref)

         expectMsg( StackToStartNotFound("my-unknown-stack", initiator.ref) )

         initiator.expectNoMsg()

      }

      "find and start stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         stack.expectMsg( StartStack )
      }

      "find but not start an already running stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         stack.expectMsg( StartStack )

         stackRegistry ! StackStarted("my-stack", stack.ref, initiator.ref)

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         stack.expectNoMsg()
      }
   }

   "FindAndStopStack" should {

      "not find an unknown stack" in new Setup {

         stackRegistry ! FindAndStopStack("my-unknown-stack", initiator.ref)

         expectMsg( StackToStopNotFound("my-unknown-stack", initiator.ref) )

         initiator.expectNoMsg()

      }

      "find and not stop non running stack" in new Setup {

         stackRegistry ! FindAndStopStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         expectMsg( StackNotRunning("my-stack") )

         stack.expectNoMsg()

      }

      "find and stop running stack" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         stack.expectMsg( StartStack )

         stackRegistry ! StackStarted("my-stack", stack.ref, initiator.ref)

         stackRegistry ! FindAndStopStack("my-stack", initiator.ref)

         expectMsg( StackFound("my-stack", initiator.ref) )

         stack.expectMsg( StopStack )

         expectNoMsg()

         stack.expectNoMsg()
      }
   }

   "StackStarted" should {

      "marks stack as started" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectMsg( StartStack )


         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectNoMsg()
      }

      "marks stack as not started if not sent" in new Setup {

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectMsg( StartStack )


         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         stack.expectNoMsg()
         stack2.expectMsg( StartStack )
         expectMsg( StackFound("my-stack", initiator.ref) )
      }
   }

   "StackStopped" should {

      "remove stack status as running" in new Setup {
         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectMsg( StartStack )
         stackRegistry ! StackStarted("my-stack", stack.ref, initiator.ref)
         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectNoMsg()

         stackRegistry ! StackStopped("my-stack", stack.ref, initiator.ref)

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack2.expectMsg( StartStack )
      }

      "kill stopped stack actor" in new Setup {
         val deathWatch = TestProbe()

         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectMsg( StartStack )
         stackRegistry ! StackStarted("my-stack", stack.ref, initiator.ref)
         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         expectMsg( StackFound("my-stack", initiator.ref) )
         stack.expectNoMsg()

         deathWatch watch stack.ref

         stackRegistry ! StackStopped("my-stack", stack.ref, initiator.ref)

         deathWatch.expectTerminated(stack.ref)
      }
   }

   "StopAllStacks" should {

      "do something" in new Setup {
         stackRegistry ! FindAndStartStack("my-stack", initiator.ref)
         stack.expectMsg( StartStack )
         stackRegistry ! StackStarted("my-stack", stack.ref, initiator.ref)

         stackRegistry ! StopAllStacks

         stack.expectMsg( StopStack )
         stackRegistry ! StackStopped("my-stack", stack.ref, initiator.ref)
         initiator.expectMsg(AllStacksStopped)
      }
   }
}
