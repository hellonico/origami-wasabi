# Wasabi

Ktor project based on [template](https://github.com/raharrison/kotlin-ktor-exposed-starter)

This applies filter loaded from $1 to image $2 and store the result locally in hello.jpg

```bash
curl -F "data=@$1" -F "data=@$2" http://localhost:8080/origami/image > hello.jpg
```
or by passing the filter directly
```bash
curl -F "data={:class origami.filters.Manga}" -F "data=@media.jpg" http://localhost:8080/origami/image > hello.jpg
```

The result can be seen on:

[http://localhost:8080/origami/image](http://localhost:8080/origami/image)

This is running on:

[http://wasabi.hellonico.info/origami/image](http://wasabi.hellonico.info/origami/image)
