#!/bin/sh
curl -F "data=@filter.edn" -F "data=@media.jpg" http://wasabi.hellonico.info/origami/image > hello.jpg
open hello.jpg