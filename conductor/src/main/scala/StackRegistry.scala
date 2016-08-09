package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,PoisonPill,Props}

object StackRegistry {
   case class FindAndStartStack(stackName: String)
   case class FindAndStopStack(stackName: String)
   case class StackNotFound(stackName: String)
   case class StackNotRunning(stackName: String)
   case class StackStarted(stackName: String, stack: ActorRef)
   case class StackStopped(stackName: String, stack: ActorRef)
   def props(serviceRegistry: ActorRef) = Props(classOf[StackRegistry], serviceRegistry)
}

class StackRegistry(val serviceRegistry: ActorRef) extends StackRegistryActor

trait StackRegistryActor extends Actor with WithLogging {
   import StackRegistry._
   import Stack._

   def serviceRegistry: ActorRef
   override def receive = normal

   val myService  = "my-service"
   val myDatabase = "my-database"
   val myStack = StackDetails("mystack", Seq(myService,myDatabase))
   val stacks = Map("mystack" -> myStack)
   var stacksRunning: Map[String, ActorRef] = Map.empty

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
      case FindAndStopStack(stackName) => {
         log.debug(s"Finding $stackName")
         stacks.get(stackName) match {
            case Some(stack) =>
               stacksRunning.get(stackName) match {
                  case Some(runningStack) =>
                     runningStack ! StopStack
                  case _ =>
                     sender ! StackNotRunning(stackName)
               }
            case _ =>
               sender ! StackNotFound(stackName)
         }
      }
      case StackStarted(stackName, stack) => {
         log.info(s"Started $stackName")
         stacksRunning = stacksRunning + (stackName -> stack)
      }
      case StackStopped(stackName, stack) => {
         log.info(s"Stopped $stackName")
         stack ! PoisonPill
      }
   }
}
