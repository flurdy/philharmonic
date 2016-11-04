package com.flurdy.conductor

import akka.actor._
import akka.pattern.ask
import akka.testkit._
import akka.util.Timeout
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import ServiceRegistry._
import Director._
import Stack._
import Service._
import scala.concurrent.duration._
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class ServiceRegistrySpec extends TestKit(ActorSystem("ServiceRegistrySpec"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with ScalaFutures
                          with BeforeAndAfterAll {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   trait Setup {
      val probeFactory   = new ProbeFactory()
      val gantryRegistry = probeFactory.first
      val service        = probeFactory.second
      val initiator      = TestProbe("spec-initiator")
      val sender         = TestProbe("spec-sender")
      val director       = TestProbe("spec-director")
      val serviceRegistry = system.actorOf(ServiceRegistry.props()(actorFactory = probeFactory))
   }

   "FindAndStartServices" should {

      "not find non existant services" in new Setup {

         director.send( serviceRegistry, FindAndStartServices(Seq("my-non-existant-service"), initiator.ref))

         director.expectMsg( ServiceNotFound("my-non-existant-service", initiator.ref) )

         director.expectNoMsg(1.second)
      }

      "find and start my service" in new Setup {

         director.send( serviceRegistry, FindAndStartServices(Seq("my-service"), initiator.ref))

         director.expectMsg( ServiceFound("my-service", initiator.ref) )

         service.expectMsg( StartService(Seq.empty, initiator.ref) )
         director.expectNoMsg(1.second)
      }

      "not start my service if already running" in new Setup {
         director.send( serviceRegistry, FindAndStartServices(Seq("my-service"), initiator.ref))
         director.expectMsg( ServiceFound("my-service", initiator.ref) )
         service.expectMsg( StartService(Seq.empty, initiator.ref) )

         director.send(serviceRegistry, ServiceStarted("my-service", Seq.empty, initiator.ref))

         director.expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         director.send( serviceRegistry, FindAndStartServices(Seq("my-service"), initiator.ref))

         director.expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )
         initiator.expectNoMsg(1.second)
         director.expectMsg( ServiceFound("my-service", initiator.ref) )
         service.expectNoMsg(1.second)
         director.expectNoMsg(1.second)
      }

      "start the next service if my service is already running" in new Setup {

         serviceRegistry ! FindAndStartServices(Seq("my-service","my-database"), initiator.ref)

         expectMsg( ServiceFound("my-service", initiator.ref) )

         service.expectMsg( StartService(Seq("my-database"), initiator.ref) )

         serviceRegistry ! ServiceStarted("my-service", Seq("my-database"), initiator.ref)

         expectMsg( ServiceFound("my-database", initiator.ref) )

         probeFactory.unprobed.size shouldBe 0
         probeFactory.probed.size shouldBe 3

         val myOtherService = probeFactory.probed.head

         myOtherService.expectMsg( StartService(Seq.empty, initiator.ref) )

         expectNoMsg(1.second)

      }
   }

   "ServiceStarted" should {

      "start next service" in new Setup {

         serviceRegistry ! ServiceStarted("my-other-service", Seq("my-service"), initiator.ref)

         expectMsg( ServiceFound("my-service", initiator.ref) )

         service.expectMsg( StartService(Seq.empty, initiator.ref) )

         expectNoMsg(1.second)
      }

      "mark all services started" in new Setup {

         serviceRegistry ! ServiceStarted("my-service", Seq.empty, self)

         expectMsg( ServicesStarted(Map()) )

         expectNoMsg(1.second)
      }
   }

   "FindAndStopService" should {

      "not find non existant services" in new Setup {

         serviceRegistry ! FindAndStopService("my-non-existant-service", initiator.ref)

         expectMsg( ServiceNotFound("my-non-existant-service", initiator.ref) )
      }

      "find and stop my service" in new Setup {

         serviceRegistry ! new FindAndStartServices("my-service", initiator.ref)
         service.expectMsg( StartService(Seq.empty, initiator.ref) )
         expectMsg( ServiceFound("my-service", initiator.ref) )

         serviceRegistry ! FindAndStopService("my-service", initiator.ref)

         expectMsg( ServiceFound("my-service", initiator.ref) )
         service.expectMsg( StopService(Map(), initiator.ref) )
         expectNoMsg(1.second)
      }

      "stop a non running service" in new Setup {

         director.send( serviceRegistry, new FindAndStartServices("my-service", initiator.ref))
         service.expectMsg( StartService(Seq.empty, initiator.ref) )
         director.expectMsg( ServiceFound("my-service", initiator.ref) )

         director.send( serviceRegistry, ServiceStarted("my-service", Seq.empty, initiator.ref))
         director.expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         director.send( serviceRegistry, FindAndStopService("my-service", initiator.ref))
         service.expectMsg( StopService(Map(), initiator.ref) )
         director.expectMsg( ServiceFound("my-service", initiator.ref) )

         serviceRegistry ! ServiceStopped("my-service", service.ref, Map(), initiator.ref)
         initiator.expectMsg( ServicesStopped )

         director.send( serviceRegistry, FindAndStopService("my-service", initiator.ref))

         director.expectMsg( ServicesStopped )
         director.expectMsg( ServiceFound("my-service", initiator.ref) )

      }
   }

   "ServiceStopped" should {

      "stop a service" in new Setup {
         val deathWatch = TestProbe()

         director.send(serviceRegistry, FindAndStartServices(Seq("my-service"), initiator.ref))

         director.expectMsg( ServiceFound("my-service", initiator.ref) )

         service.expectMsg( StartService(Seq.empty, initiator.ref) )

         director.send( serviceRegistry, ServiceStarted("my-service", Seq.empty, initiator.ref))

         director.expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         director.send( serviceRegistry, FindAndStopService("my-service", initiator.ref))

         service.expectMsg( StopService(Map(), initiator.ref) )

         deathWatch watch service.ref

         director.send( serviceRegistry, ServiceStopped("my-service", service.ref, Map(), initiator.ref))

         initiator.expectMsg( ServicesStopped )

         deathWatch.expectTerminated(service.ref)

      }

      "mark all services stopped" in new Setup {

         serviceRegistry ! ServiceStopped("my-service", service.ref, Map(), initiator.ref)

         initiator.expectMsg( ServicesStopped )

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

   "StopAllServices" should {
      "stop all services" in new Setup {
         println("===INIT REF"+initiator.ref)
         director.send(serviceRegistry, new FindAndStartServices("my-service", initiator.ref))
         service.expectMsg( StartService(Seq.empty, initiator.ref) )
         director.expectMsg( ServiceFound("my-service", initiator.ref) )
         director.send( serviceRegistry, ServiceStarted("my-service", Seq.empty, initiator.ref))
         director.expectMsg( ServicesStarted(Map("my-service" -> service.ref)) )

         director.send( serviceRegistry, StopAllServices(initiator.ref))

         service.expectMsg( StopService(Map.empty, initiator.ref) )
         director.send( serviceRegistry, ServiceStopped("my-service", service.ref, Map.empty, initiator.ref))
         initiator.expectMsg(AllServicesStopped)
      }
   }
}
