package com.flurdy.conductor

import akka.actor._
import akka.http._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.testkit._
import akka.util.Timeout
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import ServiceRegistry._
import Director._
import Stack._
import Service._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.{Stack => MutableStack}
import scala.util.Random
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}
import com.flurdy.conductor.server._

class ApplicationSpec extends TestKit(ActorSystem("ApplicationSpec"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with ScalaFutures
                          with BeforeAndAfterAll
                          with BeforeAndAfterEach {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   override def beforeAll = {
   }

   override def afterEach = {
      applications.pop().stopApplication()
   }

   val applications: MutableStack[Application] = MutableStack()

   trait Setup {
      var application = new Application()
      applications.push(application)
      application.featureToggles.enableDockerStubbing()
      val port = Random.nextInt(1000) + 32000
      application.startApplication(port)
      implicit val patienceConfig =
           PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
      implicit val meterializer = ActorMaterializer()
   }

   "service/x/stop" should {

      "respond ok give valid service" in new Setup {

         val request = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-service/stop")
         )

         whenReady( Http().singleRequest(request) ){ responseFound =>
            responseFound.status.intValue shouldBe 204
         }
      }

      "respond not found give unknown service" in new Setup {

         val request = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-unknown-service/stop")
         )

         whenReady( Http().singleRequest(request) ){ responseFound =>
            responseFound.status.intValue shouldBe 404
         }
      }
   }

   "service/x/start" should {

      "respond ok give valid service" in new Setup {

         val request1 = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-service/start")
         )

         whenReady( Http().singleRequest(request1) ){ responseFound =>
            responseFound.status.intValue shouldBe 204
         }

         val request2 = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-service/stop")
         )

         whenReady(  Http().singleRequest(request2) ){ responseFound =>
            responseFound.status.intValue shouldBe 204
         }
      }

      "respond not found give unknown service" in new Setup {

         val request = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-unknown-service/start")
         )

         whenReady( Http().singleRequest(request) ){ responseFound =>
            responseFound.status.intValue shouldBe 404
         }
      }
   }

   "services/stop" should {

      "stop all services given none running" in new Setup {
         val request = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/services/stop")
         )

         whenReady( Http().singleRequest(request) ){ responseFound =>
            responseFound.status.intValue shouldBe 204
         }
      }

      "stop all services given one running" in new Setup {

         val request1 = HttpRequest (
            method = POST,
            uri = Uri(s"http://localhost:$port/service/my-service/start")
         )

         whenReady( Http().singleRequest(request1) ){ responseFound =>
            responseFound.status.intValue shouldBe 204

            val request2 = HttpRequest (
               method = POST,
               uri = Uri(s"http://localhost:$port/services/stop")
            )

            whenReady( Http().singleRequest(request2) ){ responseFound =>
               responseFound.status.intValue shouldBe 204
            }
         }
      }
   }
}
