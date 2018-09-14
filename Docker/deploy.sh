#!/bin/bash
for type in "$@"
do
    if [ "$type" = "f" ]
    then
      cd ../FormFrontend
      ./gradlew build
      docker build -t adamratzman/ccs-forms-frontend .
      cd ../Docker
    elif [ "$type" = "b" ]
    then
      cd ../FormBackend
      ./gradlew build
      docker build -t adamratzman/ccs-forms-backend .
      cd ../Docker
    fi
done

docker-compose up -d