#!/bin/sh
curl -X POST -H "Content-Type: application/json" http://localhost:8095/widget  -d "{\"id\": -1, \"name\": \"new widget\",\"quantity\": 64}"

