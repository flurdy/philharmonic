package com.flurdy.conductor

import akka.actor.{ActorContext,ActorSystem}
import akka.event.{Logging,LogSource}

trait WithLogging extends WithLoggingSystem {
   def context: ActorContext
   def actorSystem = context.system
}

trait WithLoggingSystem {
   def actorSystem: ActorSystem
   implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
      def genString(o: AnyRef): String = o.getClass.getName
      override def getClazz(o: AnyRef): Class[_] = o.getClass
   }
   lazy val log = Logging(actorSystem, this)
}
