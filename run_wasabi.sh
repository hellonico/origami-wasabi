#!/bin/bash
mkdir -p data
docker run --restart unless-stopped -p 8095:8095 -d -v $(pwd)/data:/app/bin/data hellonico/wasabi:latest

# To enable SSL (HTTPS) for wasabi.hellonico.info:
# 1. Copy nginx.conf.example to /etc/nginx/sites-available/wasabi.hellonico.info
# 2. ln -s /etc/nginx/sites-available/wasabi.hellonico.info /etc/nginx/sites-enabled/
# 3. nginx -t && systemctl reload nginx
# 4. sudo certbot --nginx -d wasabi.hellonico.info
#    (This will generate a new cert OR expand existing cert if you choose)

