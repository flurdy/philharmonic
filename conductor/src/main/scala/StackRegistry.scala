package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,PoisonPill,Props}
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

object StackRegistry {
   case class FindAndStartStack(   stackName: String, initiator: ActorRef)
   case class FindAndStopStack(    stackName: String, initiator: ActorRef)
   case class StackToStartNotFound(stackName: String, initiator: ActorRef)
   case class StackFound(          stackName: String, initiator: ActorRef)
   case class StackToStopNotFound( stackName: String, initiator: ActorRef)
   case class StackNotRunning(     stackName: String)
   case class StackStarted(        stackName: String, stack: ActorRef, initiator: ActorRef)
   case class StackStopped(        stackName: String, stack: ActorRef)
   def props(serviceRegistry: ActorRef)(implicit actorFactory: ActorFactory = ActorFactory) = Props(classOf[StackRegistry], serviceRegistry, actorFactory)
}

class StackRegistry(val serviceRegistry: ActorRef, val actorFactory: ActorFactory) extends StackRegistryActor

trait StackRegistryActor extends Actor with WithLogging with WithActorFactory  {
   import StackRegistry._
   import Stack._

   def serviceRegistry: ActorRef
   override def receive = normal

   val myService  = "my-service"
   val myDatabase = "my-database"
   val myStack = StackDetails("my-stack", Seq(myService,myDatabase))
   val stacks = Map("my-stack" -> myStack)
   var stacksRunning: Map[String, ActorRef] = Map.empty

   def findAndStartStack(stackName: String, initiator: ActorRef) = {
      log.debug(s"Finding $stackName")
      stacks.get(stackName).fold {
         sender ! StackToStartNotFound(stackName, initiator)
      }{ details =>
         stacksRunning.get(stackName).fold {
            val stack = actorFactory.actorOf(
                  Stack.props(details, self, serviceRegistry, self) )
            stack ! StartStack
         }{  stack =>
            log.debug(s"Stack already running: $stackName")
         }
         sender ! StackFound(stackName, initiator)
      }
   }

   def findAndStopStack(stackName: String, initiator: ActorRef) = {
     log.debug(s"Finding $stackName")
     stacks.get(stackName) match {
        case Some(stack) =>
           sender ! StackFound(stackName, initiator)
           stacksRunning.get(stackName) match {
              case Some(runningStack) => runningStack ! StopStack
              case _ => sender ! StackNotRunning(stackName)
           }
        case _ => sender ! StackToStopNotFound(stackName, initiator)
     }
   }

   def normal: Receive = {
      case FindAndStartStack(stackName, initiator) => findAndStartStack(stackName, initiator)
      case FindAndStopStack( stackName, initiator) => findAndStopStack( stackName, initiator)
      case StackStarted(stackName, stack, initiator) =>
         log.info(s"Started $stackName")
         stacks.get(stackName).foreach { _ =>
            stacksRunning = stacksRunning + (stackName -> stack)
         }
      case StackStopped(stackName, stack) =>
         log.info(s"Stopped $stackName")
         stacks.get(stackName).foreach { _ =>
            stacksRunning = stacksRunning - stackName
         }
         stack ! PoisonPill
   }
}
