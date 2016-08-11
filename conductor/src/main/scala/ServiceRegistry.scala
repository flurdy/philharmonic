package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,PoisonPill,Props}

object ServiceRegistry {
   case class FindAndStartServices(serviceNames: Seq[String], initiator: ActorRef){
     def this(service: String, initiator: ActorRef) = this(Seq(service), initiator)
   }
   case class FindAndStopService(serviceName: String, initiator: ActorRef)
   case class ServiceNotFound(serviceName: String)
   case class ServiceStarted(stackName: String, servicesToStart: Seq[String], initiator: ActorRef)
   case class ServiceStopped(stackName: String, self: ActorRef, services: Map[String, ActorRef], initiator: ActorRef)
   case class StopServices(servicesRunning: Map[String, ActorRef], initiator: ActorRef)
   def props(actorFactory: ActorFactory = ActorFactory) = Props(classOf[ServiceRegistry], actorFactory)
}

class ServiceRegistry(val actorFactory: ActorFactory) extends ServiceRegistryActor

trait ServiceRegistryActor extends Actor with WithLogging with WithActorFactory {
   import ServiceRegistry._
   import Stack._
   import Service._

   override def receive = normal

   val myService  = ServiceDetails("my-service")
   val myDatabase = ServiceDetails("my-database")
   val servicesRegistry = Map("my-service" -> myService, "my-database" -> myDatabase)
   var servicesRunning: Map[ActorRef,Map[String, ActorRef]] = Map.empty

   def startService(serviceNames: Seq[String], initiator: ActorRef): Unit = {

      def createAndStartService(details: ServiceDetails, initiatorServices: Map[String, ActorRef] = Map.empty, tail: List[String]) = {
         val service = actorFactory.newActor(Service.props(details),
                              s"service-${details.name}-${initiator.path.name}")
         servicesRunning = servicesRunning + (initiator -> (initiatorServices + (details.name -> service)))
         service ! StartService(tail, initiator)
      }

      serviceNames match {
         case head::tail =>
            servicesRegistry.get(head).fold{
               initiator ! ServiceNotFound(head)
            }{ details =>
               servicesRunning.get(initiator).fold{
                  createAndStartService(details = details, tail = tail)
               }{ initiatorServices =>
                     initiatorServices.get(head).fold{
                        createAndStartService(details, initiatorServices, tail)
                     } { service =>
                           log.debug(s"Service already running: $head")
                           startService(tail, initiator)
                     }
               }
            }
         case Nil => initiator ! ServicesStarted(servicesRunning.get(initiator).getOrElse(Map.empty))
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
         case Nil => initiator ! ServicesStopped
      }
   }

   def normal: Receive = {
      case FindAndStartServices(serviceNames, initiator) =>
         startService(serviceNames, initiator)
      case FindAndStopService(serviceName, initiator) =>
         findAndStopService(serviceName, initiator)
      case ServiceStarted(serviceName, servicesToStart, initiator) =>
         log.info(s"$serviceName started")
         startService(servicesToStart, initiator)
      case StopServices(servicesRunning, initiator) =>
         log.debug("Stopping started")
         stopServices(servicesRunning, initiator)
      case ServiceStopped(serviceName, service, servicesRunning, initiator) =>
         log.info(s"$serviceName stopped")
         stopServices(servicesRunning, initiator)
         this.servicesRunning.get(initiator).map{ initiatorServices =>
            val headLessServices = initiatorServices - serviceName
            this.servicesRunning = this.servicesRunning - initiator + (initiator -> headLessServices)
         }
         service ! PoisonPill
   }
}
