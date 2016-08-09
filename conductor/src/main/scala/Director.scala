package com.flurdy.conductor

import akka.actor.{Actor,ActorSystem,Props}

object Director {
   case class StartStackOrService(serviceName: String)
   def props(actorSystem: ActorSystem) = Props(classOf[Director], actorSystem)
}

class Director(val actorSystem: ActorSystem) extends DirectorActor

trait DirectorActor extends Actor with WithLogging {
   import Director._

   override def receive = normal

   def normal: Receive = {
      case StartStackOrService(serviceName) => {
         log.info(s"May start: $serviceName")
      }
   }

}
