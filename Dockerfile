FROM ubuntu:20.04

RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    maven \
    && apt-get clean

WORKDIR /app

COPY pom.xml /app/
COPY src /app/src

RUN mvn clean package

EXPOSE 8080
