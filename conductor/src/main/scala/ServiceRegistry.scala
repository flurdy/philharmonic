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
   def props(director: ActorRef)(implicit actorFactory: ActorFactory, featureToggles: FeatureToggles) =
       Props(classOf[ServiceRegistry], director, actorFactory, featureToggles)
}

class ServiceRegistry(val director: ActorRef)(implicit val actorFactory: ActorFactory, val featureToggles: FeatureToggles) extends ServiceRegistryActor

trait ServiceRegistryActor extends Actor with WithLogging with WithActorFactory with WithFeatureToggles {
   import Director._
   import ServiceRegistry._
   import Stack._
   import Service._

   override def receive = normal

   def director: ActorRef
   val myService  = ServiceDetails("my-service")
   val myDatabase = ServiceDetails("my-database")
   val servicesRegistry = Map("my-service" -> myService, "my-database" -> myDatabase)
   var servicesRunning: Map[ActorRef,Map[String, ActorRef]] = Map.empty
   val gantryRegistry = actorFactory.actorOf(GantryRegistry.props())

   private def startService(serviceNames: Seq[String], initiator: ActorRef, sender: ActorRef): Unit = {

      def createAndStartService(details: ServiceDetails,
                                initiatorServices: Map[String, ActorRef] = Map.empty,
                                tail: List[String]) = {
         log.debug(s"Creating new service: ${details.name}")
         val service = actorFactory.actorOf(Service.props(details, self, gantryRegistry) )
         servicesRunning = servicesRunning + (initiator -> (initiatorServices + (details.name -> service)))
         service ! StartService(tail, initiator)
      }

      serviceNames match {
         case head::tail =>
            servicesRegistry.get(head).fold{
               log.debug("Service not found")
               director ! ServiceNotFound(head, initiator)
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
                        startService(tail, initiator, director)
                  }
               }
               director ! ServiceFound(head, initiator)
            }
         case Nil =>
            director ! ServicesStarted(servicesRunning.get(initiator).getOrElse(Map.empty))
      }
   }

   private def findAndStopService(serviceName: String, initiator: ActorRef, sender: ActorRef) = {
      servicesRegistry.get(serviceName).fold{
         log.debug("Service not found")
         sender ! ServiceNotFound(serviceName, initiator)
      }{ details =>
         log.debug(s"Service found: $serviceName")

         ( for {
            initiatorServices <- servicesRunning.get(initiator)
            service           <- initiatorServices.get(serviceName)
         } yield (initiatorServices, service)
         ).fold {
            log.debug(s"Service not running: $serviceName")
            sender ! ServicesStopped
         }{ case (initiatorServices, service) =>
            log.debug(s"Service already has run: $serviceName")
            val headLessServices = initiatorServices - serviceName
            service ! StopService(headLessServices, initiator )
         }
         sender ! ServiceFound(serviceName, initiator)
      }
   }

   private def stopServices(services: Map[String, ActorRef], initiator: ActorRef) = {
      services.keys.toList match {
         case head::tail =>
            val headLessServices = services - head
            log.debug(s"Stopping service $head")
            services.get(head).map(_ ! StopService(headLessServices, initiator ))
         case Nil => initiator ! ServicesStopped
      }
   }

   private def serviceStopped(serviceName: String, service: ActorRef, initiator: ActorRef) = {
      log.info(s"$serviceName stopped")
      this.servicesRunning.get(initiator).map{ initiatorServices =>
         val headLessServices = initiatorServices - serviceName
         this.servicesRunning = this.servicesRunning - initiator
         if( !headLessServices.isEmpty )
            this.servicesRunning = this.servicesRunning + (initiator -> headLessServices)
      }
      service ! PoisonPill
   }

   private def headService(services: Map[String, ActorRef]): Option[(ActorRef,Map[String, ActorRef])] = {
      services.keys.headOption.map{ key =>
         ( services(key), services - key )
      }
   }

   def normal: Receive = onMode

   def onMode: Receive = {

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
         serviceStopped(serviceName, service, initiator)
         stopServices(servicesRunning, initiator)

      case StopAllServices(initiator) =>
         log.info("Stopping all services")

         if( servicesRunning.isEmpty ){
            log.debug("All services already stopped")
            initiator ! AllServicesStopped
         } else
            for {
               key         <- servicesRunning.keys
               services    <- servicesRunning.get(key).toList
               // serviceName <- services.keys
               // service     <- services.get(serviceName).toList
            } {
               context.become(shutDownMode)
               log.debug(s"Stopping ${services.size} services")
               stopServices(services, initiator)
            }

         // servicesRunning.get(initiator) match {
         //    case Some(initiatorServices) =>
         //       context.become(shutDownMode)
         //       log.debug(s"Stopping ${initiatorServices.size} services")
         //       stopServices(initiatorServices, initiator)
         //    case _ =>
         //       log.debug("All services already stopped")
         //       initiator ! AllServicesStopped
         //
   }

   def shutDownMode: Receive = {

      case StopAllServices(initiator) =>
         log.debug("Already stopping all services")

      case ServiceStopped(serviceName, service, otherServices, initiator) =>
         log.debug("Stopped a service in shut down mode")
         serviceStopped(serviceName, service, initiator)
         if(servicesRunning.isEmpty) {
            initiator ! AllServicesStopped
            context.become(onMode)
         } else if(otherServices.isEmpty) {
            log.info("All services stopped")
            context.become(onMode)
            self ! StopAllServices(initiator)
         } else {
            otherServices.toList match {
               case (headName, headService) :: tail =>
                  headService ! StopService(tail.toMap, initiator)
               case _ =>
                  log.info("All services stopped")
                  context.become(onMode)
                  self ! StopAllServices
            }
         }
   }
}
