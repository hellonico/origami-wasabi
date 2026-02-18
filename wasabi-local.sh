#!/bin/sh
curl -F "data=@$1" -F "data2=@$2" http://localhost:8095/origami/image > hello.jpg
open hello.jpg
