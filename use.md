# How to run Philharmonic suite of services

## Phil

* -Not yet available-

## Conductor

* Start Conductor
** ```docker run -d -P -v /etc/conductor/notebook:notebook:ro flurdy/philharmonic-conductor:latest```

* Tail log and wait until up and running
** ```docker logs -t```
** Find host and port from ```docker ps```

* Check heart beat of Conductor
** ```curl http://example.com:32799/conductor/heart-beat```


## Gantry

* -Currently part of Conductor-

## Notepad

* -Not yet available-

## Melody

* -Not yet available-
