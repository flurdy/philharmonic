package com.flurdy.conductor

import akka.actor.{ActorContext,ActorSystem,Props}
import akka.testkit.TestProbe

class ProbeFactory(implicit system: ActorSystem) extends ActorFactory {

  val first = TestProbe()
  val second = TestProbe()
  var unprobed = List(first,second)
  var probed: List[TestProbe] = Nil

  override def newActor(props: Props)(implicit context: ActorContext) = {
      val probe = unprobed match {
           case head::tail =>
              unprobed = tail
              head
           case Nil => TestProbe()
         }
      probed = probe :: probed
      probe.ref
   }

  override def newActor(props: Props, name: String)(implicit context: ActorContext) = newActor(props)

}
