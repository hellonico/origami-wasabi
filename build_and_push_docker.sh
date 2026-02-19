#!/bin/bash
set -e

# Rebuild the application locally
./gradlew installDist

# Enable multi-arch building if needed (uncomment if you want to use buildx)
# docker buildx create --use || true

# Build for linux/amd64 (target platform)
docker build --platform linux/amd64 -t hellonico/wasabi:latest .

# Push to Docker Hub
docker push hellonico/wasabi:latest

echo "Successfully built and pushed hellonico/wasabi:latest"
