package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,Props}

object ServiceRegistry {
   case class FindAndStartServices(serviceNames: Seq[String], services: Map[String, ActorRef], initiator: ActorRef){
     def this(service: String, initiator: ActorRef) = this(Seq(service), Map.empty, initiator)
   }
   case class FindAndStopService(serviceName: String, initiator: ActorRef)
   case class ServiceNotFound(serviceName: String)
   case class ServiceStarted(stackName: String, servicesToStart: Seq[String], services: Map[String, ActorRef], initiator: ActorRef)
   case class ServiceStopped(stackName: String, services: Map[String, ActorRef], initiator: ActorRef)
   case class StopServices(servicesRunning: Map[String, ActorRef], initiator: ActorRef)
   def props() = Props(classOf[ServiceRegistry])
}

class ServiceRegistry extends ServiceRegistryActor

trait ServiceRegistryActor extends Actor with WithLogging {
   // import Director._
   import ServiceRegistry._
   import Stack._
   import Service._

   override def receive = normal

   val myService  = ServiceDetails("my-service")
   val myDatabase = ServiceDetails("my-database")
   val servicesRegistry = Map("my-service" -> myService, "my-database" -> myDatabase)
   var servicesRunning: Map[ActorRef,Map[String, ActorRef]] = Map.empty

   def startService(serviceNames: Seq[String],
                    services: Map[String, ActorRef], initiator: ActorRef): Unit = {
      serviceNames match {
         case head::tail =>
            servicesRegistry.get(head).fold( initiator ! ServiceNotFound(head) ){ details =>
               val initiatorService = servicesRunning.get(initiator).flatMap(_.get(head))
               val service = initiatorService.getOrElse{
                  context.actorOf(
                           Service.props(details),
                           s"service-${details.name}-${initiator.path.name}")
               }
               val servicesWithActor = services + (head -> service)
               initiatorService.fold{
                  service ! StartService(tail, servicesWithActor, initiator)
               }{ _ =>
                  startService(tail, servicesWithActor, initiator)
               }
            }
         case Nil =>
            val initiatorService = servicesRunning.get(initiator).getOrElse(Map.empty) ++ services
            servicesRunning = servicesRunning  + (initiator -> initiatorService)
            initiator ! ServicesStarted(services)
      }
   }

   def findAndStopService(serviceName: String, initiator: ActorRef) = {
      servicesRegistry.get(serviceName).fold( initiator ! ServiceNotFound(serviceName) ){ details =>
         servicesRunning.get(initiator) match {
            case Some(initiatorServices) =>
               initiatorServices.get(serviceName) match {
                  case Some(service) =>
                     val headLessServices = initiatorServices - serviceName
                     service ! StopService(headLessServices, initiator )
                  case _ => initiator ! ServicesStopped
               }
            case _ => initiator ! ServicesStopped
         }
      }
   }

   def stopServices(services: Map[String, ActorRef], initiator: ActorRef) = {
      services.keys.toList match {
         case head::tail =>
            val headLessServices = services - head
            services.get(head).map(_ ! StopService(headLessServices, initiator ))
         case Nil =>
            initiator ! ServicesStopped
      }
    }

   def normal: Receive = {
      case FindAndStartServices(serviceNames, services, initiator) =>
         startService(serviceNames, services, initiator)
      case FindAndStopService(serviceName, initiator) =>
         findAndStopService(serviceName, initiator)
      case ServiceStarted(serviceName, servicesToStart, services, initiator) =>
         log.info(s"$serviceName started")
         startService(servicesToStart, services, initiator)
      case StopServices(servicesRunning, initiator) =>
         log.debug("Stopping started")
         stopServices(servicesRunning, initiator)
      case ServiceStopped(serviceName, servicesRunning, initiator) =>
         log.info(s"$serviceName stopped")
         stopServices(servicesRunning, initiator)
   }
}
