#!/bin/sh
./gradlew installDist
docker build -t hellonico/wasabi .