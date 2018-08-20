#!/bin/bash
docker-compose down && cd ../FormBackend && ./gradlew build \
&& cd ../FormFrontend && ./gradlew build \
&& cd ../Docker && docker-compose up --build
