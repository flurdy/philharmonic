package com.flurdy.conductor

import akka.actor.{ActorContext,ActorRef,ActorSystem,Props}
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

trait WithActorFactory {
   def actorFactory: ActorFactory
}

trait ActorFactory {

  def newActor(props: Props)(implicit context: ActorContext) = context.actorOf( props)

  def newActor(props: Props, name: String)(implicit context: ActorContext) = context.actorOf( props, name)
}

object ActorFactory extends ActorFactory
