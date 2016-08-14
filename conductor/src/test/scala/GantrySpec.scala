package com.flurdy.conductor

import akka.actor._
import akka.testkit._
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import Gantry._
import scala.concurrent.duration._
import com.flurdy.conductor.docker._
// import com.flurdy.sander.actor.{ActorFactory,ProbeFactory}

class GantrySpec extends TestKit(ActorSystem("DirectorSpec"))
                          with ImplicitSender
                          with WordSpecLike
                          with Matchers
                          with MockitoSugar
                          with BeforeAndAfterAll {

   override def afterAll = {
      TestKit.shutdownActorSystem(system)
   }

   trait Setup {
      val image = DockerImage("my-service")
      val gantry = system.actorOf(Gantry.props(image))
   }

   "RunImage" should {

      "try to run image" in new Setup {

         gantry ! RunImage

         // TODO

      }
   }
}
