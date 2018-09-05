#!/bin/bash
for type in "$@"
do
    if [ "$type" = "f" ]
    then
      cd ../FormFrontend
      ./gradlew build
      cd ../Docker
    elif [ "$type" = "b" ]
    then
      cd ../FormBackend
      ./gradlew build
      cd ../Docker
    fi
done

docker-compose build
docker-compose up -d