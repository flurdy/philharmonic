
# Configure Philharmonic Conductor

##  

* Clone example notebook configuration
** ```git clone https://github.com/flurdy/conductor-notebook```
* Change origin to your own git server (this will vary by server)
** ```git remote set-url origin git@gitserver/conductor/notebook```
** ```git push -u origin master```

## Configure environments

* File /environment.conf
** notebook.docker.host
** notebook.docker.registry.provider
** notebook.docker.registry.url

## Configure services

* Folder /registry/services.d/**.service

service {

}

## Configure stacks

* Folder /registry/stacks.d/**.stack

## Test configuration

* ```./gradlew test```
