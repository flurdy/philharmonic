package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}

object StackRegistry {
   case class FindAndStartStack(stackName: String)
   case class StackNotFound(stackName: String)
   case class StackStarted(stackName: String)
   case class StackStopped(stackName: String)
   def props(serviceRegistry: ActorRef) = Props(classOf[StackRegistry], serviceRegistry)
}

class StackRegistry(val serviceRegistry: ActorRef) extends StackRegistryActor

trait StackRegistryActor extends Actor with WithLogging {
   import Director._
   import StackRegistry._
   import Stack._

   def serviceRegistry: ActorRef
   override def receive = normal

   val myService  = "my-service"
   val myDatabase = "my-database"
   val myStack = StackDetails("mystack", Seq(myService,myDatabase))
   val stacks = Map("mystack" -> myStack)

   def normal: Receive = {
      case FindAndStartStack(stackName) => {
         log.debug(s"Finding $stackName")
         stacks.get(stackName) match {
            case Some(details) =>
               val stack = context.actorOf(
                  Stack.props(details, self, serviceRegistry), s"stack-$stackName")
               stack ! StartStack
            case _ =>
               sender ! StackNotFound(stackName)
         }
      }
      case StackStarted(stackName) => {
         log.info(s"Started $stackName")
      }
      case StackStopped(stackName) => {
         log.info(s"Stopped $stackName")
      }
   }
}
