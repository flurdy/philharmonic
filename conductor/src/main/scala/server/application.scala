package com.flurdy.conductor.server

import akka.actor.{ActorSystem,Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.flurdy.conductor._
import com.flurdy.sander.server._
import com.flurdy.sander.actor.ActorFactory
import com.typesafe.config.ConfigFactory

class ApplicationDaemon() extends AbstractApplicationDaemon {
  def application = new Application
}

class Application() extends ReferenceApplication
with ConductorService with WithLoggingSystem {

   val applicationName = "conductor"
   implicit override val actorSystem = ActorSystem(s"$applicationName-system")
   implicit val materializer = ActorMaterializer()
   implicit val actorFactory = ActorFactory
   val conductorConfig = ConfigFactory.load.getConfig("com.flurdy.philharmonic.conductor")
   override val director = actorSystem.actorOf(Director.props(conductorConfig), "director")

   def startApplication() = {
      log.info(s"Starting $applicationName")
      Http().bindAndHandle(route, "0.0.0.0", 8080)
   }

   def stopApplication() = {
      log.info(s"Stopping $applicationName")
      actorSystem.shutdown()
   }

}

object ServiceApplication extends ServiceApplication{
    def createApplication()  = new ApplicationDaemon()
}
