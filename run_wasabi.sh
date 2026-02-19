#!/bin/bash
mkdir -p data
docker run --restart unless-stopped -p 8095:8095 -d -v $(pwd)/data:/app/bin/data hellonico/wasabi:latest
