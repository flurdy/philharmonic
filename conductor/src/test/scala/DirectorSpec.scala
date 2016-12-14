package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.Matchers.any
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import StackRegistry._
import ServiceRegistry._
import Director._
import Stack._
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
      lazy val serviceRegistry = probeFactory.first
      lazy val configMock = mock[Config]
   }

   trait StackSetup extends Setup {
      val config = ConfigFactory.empty()
                     .withValue("stacks.enabled",
                                 ConfigValueFactory.fromAnyRef("true"))
      val featureToggles = new DefaultFeatureToggles(config)
      val director = system.actorOf(Director.props()(probeFactory, featureToggles))
   }

   trait ServiceOnlySetup extends Setup {
      val config = ConfigFactory.empty()
                     .withValue("stacks.enabled",
                                 ConfigValueFactory.fromAnyRef("false"))
      val featureToggles = new DefaultFeatureToggles(config)
      val director = system.actorOf(Director.props()(probeFactory, featureToggles))
   }

   "StartStackOrService" should {

      "start stack given stack feature is enabled" in new StackSetup {

         director ! StartStackOrService("my-stack")

         stackRegistry.expectMsg( FindAndStartStack("my-stack", self) )
      }

      "start stack given stack feature is disabled" in new ServiceOnlySetup {

         director ! StartStackOrService("my-service")

         stackRegistry.expectNoMsg
         serviceRegistry.expectMsg(FindAndStartServices(Seq("my-service"), self))
      }
   }

   "StopStackOrService" should {

      "stop stack given stack feature is enabled" in new StackSetup {

         director ! StopStackOrService("my-stack")

         stackRegistry.expectMsg( FindAndStopStack("my-stack", self) )

      }

      "stop stack given stack feature is disnabled" in new ServiceOnlySetup {

         director ! StopStackOrService("my-service")

         stackRegistry.expectNoMsg
         serviceRegistry.expectMsg( FindAndStopService("my-service", self) )
      }
   }

   "StackNotFound given stack feature is enabled" should {

      "try to start service instead" in new StackSetup {

         director ! StackToStartNotFound("my-stack", initiator.ref)

         serviceRegistry.expectMsg( FindAndStartServices(Seq("my-stack"), initiator.ref) )

      }
   }

   "StackFound given stack feature is enabled" should {

      "return happy case" in new StackSetup {

         director ! StackFound("my-stack", initiator.ref)

         initiator.expectMsg( Right( StackOrServiceFound("my-stack") ) )

      }
   }

   "StackToStopNotFound given stack feature is enabled" should {

      "try to stop service instead" in new StackSetup {

         director ! StackToStopNotFound("my-stack", initiator.ref)

         serviceRegistry.expectMsg( FindAndStopService("my-stack", initiator.ref) )

      }
   }

   "StackNotRunning given stack feature is enabled" should {

      "accept and log" in new StackSetup {

         director ! StackNotRunning("my-stack")

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "ServiceNotFound" should {

      "accept and warn" in new StackSetup {

         director ! ServiceNotFound("my-stack", self)

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()
         expectMsg(Left(StackOrServiceNotFound("my-stack")))

      }
   }

   "ServiceFound" should {

      "return happy case" in new StackSetup {

         director ! ServiceFound("my-stack", initiator.ref)

         initiator.expectMsg( Right( StackOrServiceFound("my-stack") ) )

      }
   }

   "ServicesStarted" should {

      "accept and log" in new StackSetup {

         director ! ServicesStarted( Map("my-stack" -> TestProbe().ref))

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "ServicesStopped" should {

      "accept and log" in new StackSetup {

         director ! ServicesStopped

         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()

      }
   }

   "StopAllServices" should {

      "stop all services" in new StackSetup {

         director ! StopAllServices

         stackRegistry.expectMsg(StopAllStacks)
         serviceRegistry.expectNoMsg()
      }
   }

   "AllStacksStopped" should {

      "then stop all services" in new StackSetup {

         director ! StopAllServices

         stackRegistry.expectMsg(StopAllStacks)
         expectMsg(Right(StoppingAllServices))

         director ! AllStacksStopped
         serviceRegistry.expectMsg(StopAllServices(director))
         director ! AllServicesStopped
         serviceRegistry.expectNoMsg()
         stackRegistry.expectNoMsg()
      }
   }
}
