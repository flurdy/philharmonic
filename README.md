# Philharmonic

Microservices orchestration

Concept based on https://github.com/hmrc/service-manager but using docker and any hosts.

* registry of services
* stacks of services
* depdendency for services
* mocks and stubs can override service
* parrallel ramp up
* block dependency until ready
* unique name per service
* unique port per service
* dynamic ports for automated testing
* support for
** docker
** docker compose
** docker swarm
** docker cloud
** Amazon ECS
** Google GCE
** Google GKE
* run local images
* repository relase and snapshot builds
* lookups
** status
** port
** instaces
** zone
* logging
* feature flags and properties injection
* balcony UI read only viewer
* fluffer to build images for not yet docker services
