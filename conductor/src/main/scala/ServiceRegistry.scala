package com.flurdy.conductor

import akka.actor.{Actor,ActorRef,PoisonPill,Props}
import com.flurdy.sander.actor.{ActorFactory,WithActorFactory}

object ServiceRegistry {
   case class FindAndStartServices(serviceNames: Seq[String], initiator: ActorRef){
     def this(service: String, initiator: ActorRef) = this(Seq(service), initiator)
   }
   case class FindAndStopService(serviceName: String, initiator: ActorRef)
   case class ServiceNotFound(serviceName: String, initiator: ActorRef)
   case class ServiceFound(serviceName: String, initiator: ActorRef)
   case class ServiceStarted(stackName: String, servicesToStart: Seq[String], initiator: ActorRef)
   case class ServiceStopped(stackName: String, service: ActorRef, services: Map[String, ActorRef], initiator: ActorRef)
   case class StopServices(servicesRunning: Map[String, ActorRef], initiator: ActorRef)
   def props()(implicit actorFactory: ActorFactory) = Props(classOf[ServiceRegistry], actorFactory)
}

class ServiceRegistry()(implicit val actorFactory: ActorFactory) extends ServiceRegistryActor

trait ServiceRegistryActor extends Actor with WithLogging with WithActorFactory {
   import ServiceRegistry._
   import Stack._
   import Service._

   override def receive = normal

   val myService  = ServiceDetails("my-service")
   val myDatabase = ServiceDetails("my-database")
   val servicesRegistry = Map("my-service" -> myService, "my-database" -> myDatabase)
   var servicesRunning: Map[ActorRef,Map[String, ActorRef]] = Map.empty
   val gantryRegistry = actorFactory.actorOf(GantryRegistry.props())

   def startService(serviceNames: Seq[String], initiator: ActorRef, sender: ActorRef): Unit = {

      def createAndStartService(details: ServiceDetails,
                                initiatorServices: Map[String, ActorRef] = Map.empty,
                                tail: List[String]) = {
         log.debug(s"Creating new service: ${details.name}")
         val service = actorFactory.actorOf(Service.props(details, self, gantryRegistry) )
                        //   s"service-${details.name}-${initiator.path.name}")
         servicesRunning = servicesRunning + (initiator -> (initiatorServices + (details.name -> service)))
         service ! StartService(tail, initiator)
      }

      serviceNames match {
         case head::tail =>
            servicesRegistry.get(head).fold{
               log.debug("Service not found")
               sender ! ServiceNotFound(head, initiator)
            }{ details =>
               log.debug(s"Service details found: $head")
               servicesRunning.get(initiator).fold{
                  log.debug(s"Service not running: $head")
                  createAndStartService(details = details, tail = tail)
               }{ initiatorServices =>
                  log.debug(s"Initiator known")
                  initiatorServices.get(head).fold{
                     log.debug(s"Service $head not running")
                     createAndStartService(details, initiatorServices, tail)
                  } { service =>
                        log.debug("Service initiator $head running")
                        startService(tail, initiator, sender)
                  }
               }
               sender ! ServiceFound(head, initiator)
            }
         case Nil =>
            initiator ! ServicesStarted(servicesRunning.get(initiator).getOrElse(Map.empty))
      }
   }

   def findAndStopService(serviceName: String, initiator: ActorRef, sender: ActorRef) = {
      servicesRegistry.get(serviceName).fold{
         log.debug("Service not found")
         sender ! ServiceNotFound(serviceName, initiator)
      }{ details =>
         log.debug(s"Service found: $serviceName")
         servicesRunning.get(initiator).fold{
            log.debug(s"Service not running: $serviceName")
            sender ! ServicesStopped
         }{ initiatorServices =>
            log.debug(s"Initator already known")
            initiatorServices.get(serviceName).fold{
               log.debug(s"Service not running: $serviceName")
               sender ! ServicesStopped
            }{ service =>
               log.debug(s"Service already has run: $serviceName")
               val headLessServices = initiatorServices - serviceName
               service ! StopService(headLessServices, initiator )
            }
            sender ! ServiceFound(serviceName, initiator)
         }
      }
   }

   def stopServices(services: Map[String, ActorRef], initiator: ActorRef) = {
      services.keys.toList match {
         case head::tail =>
            val headLessServices = services - head
            log.debug(s"Stopping service $head")
            services.get(head).map(_ ! StopService(headLessServices, initiator ))
         case Nil => initiator ! ServicesStopped
      }
   }

   def normal: Receive = {

      case FindAndStartServices(serviceNames, initiator) =>
         startService(serviceNames, initiator, sender)

      case FindAndStopService(serviceName, initiator) =>
         findAndStopService(serviceName, initiator, sender)

      case ServiceStarted(serviceName, servicesToStart, initiator) =>
         log.info(s"$serviceName started")
         startService(servicesToStart, initiator, sender)

      case StopServices(servicesRunning, initiator) =>
         log.debug("Stopping started")
         stopServices(servicesRunning, initiator)

      case ServiceStopped(serviceName, service, servicesRunning, initiator) =>
         log.info(s"$serviceName stopped")
         this.servicesRunning.get(initiator).map{ initiatorServices =>
            val headLessServices = initiatorServices - serviceName
            this.servicesRunning = this.servicesRunning - initiator + (initiator -> headLessServices)
         }
         stopServices(servicesRunning, initiator)
         service ! PoisonPill
   }
}
