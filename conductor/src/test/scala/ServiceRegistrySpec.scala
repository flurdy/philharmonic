package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
// import org.mockito.Mockito._
import org.scalatest._
// import org.scalatest.concurrent._
import org.scalatest.mock.MockitoSugar
import ServiceRegistry._
import Stack.ServicesStarted
import Service.StartService

import scala.concurrent.duration._

class ServiceRegistrySpec extends TestKit(ActorSystem("MyTestSpec"))
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
      val serviceRegistry = system.actorOf(ServiceRegistry.props(actorFactory = probeFactory))
   }

   "FindAndStartServices" should {

      "not find non existant services" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-non-existant-service", self)

         expectMsg( ServiceNotFound("my-non-existant-service") )

      }

      "find and start my service" in new Setup {

         val service = probeFactory.first

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

      }

      "not start my service if already running" in new Setup {

         val myService      = probeFactory.first
         val myOtherService = probeFactory.second

         serviceRegistry ! new FindAndStartServices("my-service", self)

         myService.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map("my-service" -> myService.ref)) )

         serviceRegistry ! new FindAndStartServices("my-service", self)

         myOtherService.expectNoMsg(1.second)

         myService.expectNoMsg(1.second)

         expectMsg( ServicesStarted(Map("my-service" -> myService.ref)) )

         expectNoMsg(1.second)

      }

      "start the next service if my service is already running" in new Setup {

         val myService      = probeFactory.first
         val myOtherService = probeFactory.second

         serviceRegistry ! FindAndStartServices(Seq("my-service","my-database"), self)

         myService.expectMsg( StartService(Seq("my-database"), self) )

         serviceRegistry ! ServiceStarted("my-service", Seq("my-database"), self)

         myService.expectNoMsg(1.second)

         expectNoMsg(1.second)

         myOtherService.expectMsg( StartService(Seq.empty, self) )

      }
   }

   "ServiceStarted" should {

      "start next service" in new Setup {

         val myService = probeFactory.first
         val myOtherService = probeFactory.second

         serviceRegistry ! ServiceStarted("my-other-service", Seq("my-service"), self)

         myService.expectMsg( StartService(Seq.empty, self) )

      }

      "mark all services started" in new Setup {

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map()) )

      }
   }
}
