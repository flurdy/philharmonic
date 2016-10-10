package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import ServiceRegistry._
import Stack.{ServicesStarted,ServicesStopped}
import Service.{StartService,StopService}
import scala.concurrent.duration._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class ServiceRegistrySpec extends TestKit(ActorSystem("ServiceRegistrySpec"))
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
      lazy val gantryRegistry = probeFactory.first
      lazy val service = probeFactory.second
      val serviceRegistry = system.actorOf(ServiceRegistry.props()(actorFactory = probeFactory))
   }

   "FindAndStartServices" should {

      "not find non existant services" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-non-existant-service", self)

         expectMsg( ServiceNotFound("my-non-existant-service") )

      }

      "find and start my service" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

      }

      "not start my service if already running" in new Setup {

         val myOtherService = probeFactory.second

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         serviceRegistry ! new FindAndStartServices("my-service", self)

         myOtherService.expectNoMsg(1.second)

         service.expectNoMsg(1.second)

         expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         expectNoMsg(1.second)

      }

      "start the next service if my service is already running" in new Setup {

         serviceRegistry ! FindAndStartServices(Seq("my-service","my-database"), self)

         service.expectMsg( StartService(Seq("my-database"), self) )

         serviceRegistry ! ServiceStarted("my-service", Seq("my-database"), self)

         service.expectNoMsg(1.second)

         expectNoMsg(1.second)

         val myOtherService = probeFactory.probed.head

         myOtherService.expectMsg( StartService(Seq.empty, self) )

      }
   }

   "ServiceStarted" should {

      "start next service" in new Setup {

         serviceRegistry ! ServiceStarted("my-other-service", Seq("my-service"), self)

         service.expectMsg( StartService(Seq.empty, self) )

      }

      "mark all services started" in new Setup {

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map()) )

      }
   }

   "FindAndStopService" should {

      "not find non existant services" in new Setup {

         serviceRegistry ! FindAndStopService("my-non-existant-service", self)

         expectMsg( ServiceNotFound("my-non-existant-service") )
      }

      "find and stop my service" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! FindAndStopService("my-service", self)

         expectNoMsg(1.second)

         service.expectMsg( StopService(Map(), self) )

         expectNoMsg(1.second)

      }

      "stop a non running service" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         serviceRegistry ! FindAndStopService("my-service", self)

         service.expectMsg( StopService(Map(), self) )

         serviceRegistry ! ServiceStopped("my-service", service.ref, Map(), self)

         expectMsg( ServicesStopped )

         serviceRegistry ! FindAndStopService("my-service", self)

         expectMsg( ServicesStopped )

      }
   }

   "ServiceStopped" should {

      "stop a service" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         serviceRegistry ! FindAndStopService("my-service", self)

         service.expectMsg( StopService(Map(), self) )

         serviceRegistry ! ServiceStopped("my-service", service.ref, Map(), self)

         expectMsg( ServicesStopped )

         // service.expectMsg( PoisonPill )

      }

      "mark all services stopped" in new Setup {

         serviceRegistry ! ServiceStopped("my-service", service.ref, Map(), self)

         expectMsg( ServicesStopped )

      }
   }

   "StopServices" should {

      "stop services" in new Setup {
         val database = TestProbe()
         probeFactory.unprobed = probeFactory.unprobed ::: List(database)

         serviceRegistry ! new FindAndStartServices("my-service", self)

         service.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! new FindAndStartServices("my-database", self)

         database.expectMsg( StartService(Seq.empty, self) )

         serviceRegistry ! StopServices(Map("my-service" -> service.ref, "my-database" -> database.ref), self)

         service.expectMsg( StopService(Map("my-database" -> database.ref), self) )

      }
   }
}
