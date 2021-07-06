#!/bin/sh
curl -F "data=@$1" -F "data=@$2" http://wasabi.hellonico.info/origami/image > hello.jpg
open hello.jpg