package com.flurdy.conductor

import akka.actor._
import akka.testkit._
// import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import StackRegistry._
import ServiceRegistry._
import Director._
import Stack._
// import Service._
// import GantryRegistry._
// import Gantry._
import scala.concurrent.duration._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class DirectorSpec extends TestKit(ActorSystem("DirectorSpec"))
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
      lazy val stackRegistry   = probeFactory.second
      lazy val serviceRegistry =  probeFactory.first
      val director = system.actorOf(Director.props()(probeFactory))
   }

   "StartStackOrService" should {

      "start stack" in new Setup {

         director ! StartStackOrService("my-stack")

         stackRegistry.expectMsg( FindAndStartStack("my-stack", director) )

      }
   }

   "StopStackOrService" should {

      "stop stack" in new Setup {

         director ! StopStackOrService("my-stack")

         stackRegistry.expectMsg( FindAndStopStack("my-stack") )

      }
   }

   "StackNotFound" should {

      "try to start service instead" in new Setup {

         director ! StackNotFound("my-stack")

         serviceRegistry.expectMsg( new FindAndStartServices("my-stack", director) )

      }
   }

   "StackToStopNotFound" should {

      "try to stop service instead" in new Setup {

         director ! StackToStopNotFound("my-stack")

         serviceRegistry.expectMsg( new FindAndStopService("my-stack", director) )

      }
   }

   "StackNotRunning" should {

      "accept and log" in new Setup {

         director ! StackNotRunning("my-stack")

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "ServiceNotFound" should {

      "accept and warn" in new Setup {

         director ! ServiceNotFound("my-stack")

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "ServicesStarted" should {

      "accept and log" in new Setup {

         director ! ServicesStarted( Map("my-stack" -> TestProbe().ref))

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "ServicesStopped" should {

      "accept and log" in new Setup {

         director ! ServicesStopped

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }
}
