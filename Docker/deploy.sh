#!/bin/bash
docker-compose down && cd ../FormBackend && ./gradlew build && cd ../Docker && docker-compose up --build
### && cd ../FormFrontend && ./gradlew build
