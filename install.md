
# Philharmonic client install

For installing on individual workstations

* Install Docker (https://www.docker.com/products/docker)

* Install Git (https://git-scm.com/downloads)

* Pull Conductor image
** ```docker pull flurdy/philharmonic-conductor:latest```

* Find/create Conductor configuration
**  Configure.md
** git clone git@example.com/conductor/notebook.git

* Start Conductor
** ```docker run -d -P -v /etc/conductor/notebook:notebook:ro flurdy/philharmonic-conductor:latest```

* Tail log and wait until up and running
** ```docker logs -t```
** Find host and port from ````docker ps```

* Check heart beat of Conductor
** ```curl http://example.com:32799/conductor/heart-beat```
