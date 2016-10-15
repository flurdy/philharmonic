package com.flurdy.conductor.server

import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit._
import akka.http.scaladsl.server._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}
import com.flurdy.conductor._
import Director._


class ConductorServiceSpec extends WordSpecLike
                           with Matchers
                           with MockitoSugar
                           with ScalatestRouteTest
                           {

   class DirectorStub() extends Actor {
      override def receive: Receive = {
         case StartStackOrService("my-service") =>
            sender ! Right(StackOrServiceFound("my-service"))

         case StartStackOrService("unknown-service") =>
            sender ! Left(StackOrServiceNotFound("unknownservice"))
            sender ! Right(StackOrServiceFound("my-service"))

         case StopStackOrService("my-service") =>
            sender ! Right(StackOrServiceFound("my-service"))

         case StopStackOrService("unknown-service") =>
            sender ! Left(StackOrServiceNotFound("unknownservice"))
            sender ! Right(StackOrServiceFound("my-service"))
      }
   }

   object DirectorStub {
      def props = Props(new DirectorStub())
   }

   trait Setup {
      val mockLog = mock[LoggingAdapter]
      val service = new ConductorService {
         override val log = mockLog
         override val director = system.actorOf(DirectorStub.props)
      }
   }

   "[POST] /service/{service}/start" should {

      "find a known service" in new Setup {

         Post("/service/my-service/start") ~> service.route ~> check {
            status shouldBe StatusCodes.NoContent
         }
      }

      "not find an unknown service" in new Setup {
         Post("/service/unknown-service/start") ~> service.route ~> check {
            status shouldBe StatusCodes.NotFound
            handled shouldBe true
         }
      }
   }

   "[POST] /wrongpath/service/{service}/start" should {
      "not find an unknown service" in new Setup {
         Post("/wrongpath/service/my-service/start") ~> Route.seal(service.route) ~> check {
            status shouldBe StatusCodes.NotFound
         }
      }
   }

   "[POST] /service/{service}/stop" should {

      "find a known service" in new Setup {

         Post("/service/my-service/stop") ~> service.route ~> check {
            status shouldBe StatusCodes.NoContent
         }
      }

      "not find an unknown service" in new Setup {
         Post("/service/unknown-service/stop") ~> service.route ~> check {
            status shouldBe StatusCodes.NotFound
            handled shouldBe true
         }
      }
   }

   "[POST] /wrongpath/service/{service}/stop" should {
      "not find an unknown service" in new Setup {
         Post("/wrongpath/service/my-service/stop") ~> Route.seal(service.route) ~> check {
            status shouldBe StatusCodes.NotFound
         }
      }
   }
}
