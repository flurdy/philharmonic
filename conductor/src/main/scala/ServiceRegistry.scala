package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}

object ServiceRegistry {
   case class FindAndStartServices(serviceNames: Seq[String], services: Map[String, ActorRef], initiator: ActorRef){
     def this(service: String, initiator: ActorRef) = this(Seq(service), Map.empty, initiator)
   }
  //  case class StopService(serviceName: String)
   case class ServiceNotFound(serviceName: String)
   case class ServiceStarted(stackName: String, servicesToStart: Seq[String], services: Map[String, ActorRef], initiator: ActorRef)
   case class ServiceStopped(stackName: String, services: Map[String, ActorRef], initiator: ActorRef)
   case class StopServices(servicesRunning: Map[String, ActorRef], initiator: ActorRef)
   def props() = Props(classOf[ServiceRegistry])
}

class ServiceRegistry extends ServiceRegistryActor

trait ServiceRegistryActor extends Actor with WithLogging {
   import Director._
   import ServiceRegistry._
   import Stack._
   import Service._

   override def receive = normal

   val myService  = ServiceDetails("my-service")
   val myDatabase = ServiceDetails("my-database")
   val servicesRegistry = Map("my-service" -> myService, "my-database" -> myDatabase)

   def startService(serviceNames: Seq[String], services: Map[String, ActorRef], initiator: ActorRef) = {
      serviceNames match {
         case head::tail =>
            servicesRegistry.get(head) match {
               case Some(details) =>
                  val service = context.actorOf(
                     Service.props(details), s"service-${details.name}")
                  val servicesWithActor = services + (head -> service)
                  service ! StartService(tail, servicesWithActor, initiator)
               case _ =>
                  initiator ! ServiceNotFound(head)
            }
         case Nil =>
            initiator ! ServicesStarted(services)
      }
   }

   def stopServices(services: Map[String, ActorRef], initiator: ActorRef) = {
      services.keys.toList match {
         case head::tail =>
            val headLessServices: Map[String, ActorRef] = services - head
            services.get(head).map(_ ! StopService(headLessServices, initiator ))
         case Nil =>
            initiator ! ServicesStopped
      }
    }

   def normal: Receive = {
      case FindAndStartServices(serviceNames, services, initiator) => {
         startService(serviceNames, services, initiator)
      }
      case ServiceStarted( serviceName, servicesToStart, services, initiator) => {
         log.info(s"$serviceName started")
         startService(servicesToStart, services, initiator)
      }
      case StopServices(servicesRunning, initiator) => {
         log.debug("Stopping started")
         stopServices(servicesRunning, initiator)
      }
      case ServiceStopped(serviceName, servicesRunning, initiator) => {
         log.info(s"$serviceName stopped")
         stopServices(servicesRunning, initiator)
      }
   }
}
