
## Milestone 1:

* Skeleton Conductor talking to a Docker host - Done

## Milestone 2:

* Conductor can create, start and stop a container - Done

## Milestone 3:

* Notepad reading service description

## Beta:

* Conductor service
* Conductor can launch a set of containers
* Individual services only




## Long term:

* CLI
* Docker compose stacks
* Change Docker host ie remote
* Docker Cloud, Docker Swarm, Amazon ECS, Kubernetes etc support
* Service reset for quicker startup
* Multiple simultaneous duplicate stacks running









### Possible features

* registry of services
* stacks of services
* depdendency for services
* mocks and stubs can override service
* conf.d of services configs
* hocon or groovy dsl
* parrallel ramp up
* block dependency until ready
* unique name per service
* unique port per service
* dynamic ports for automated testing
* support for
 * docker
 * docker compose
 * docker swarm
 * docker cloud
 * Amazon ECS
 * Google GCE
 * Google GKE
* run local images
* repository relase and snapshot builds
* lookups
 * status
 * port
 * instaces
 * zone
* logging
* feature flags and properties injection
* balcony UI read only viewer
* fluffer to build images for not yet docker services
