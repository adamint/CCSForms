FROM openjdk:8-jre-slim

WORKDIR /app
COPY ./build/libs/*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "192.168.99.100"]