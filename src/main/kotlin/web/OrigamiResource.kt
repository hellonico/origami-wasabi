package web

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import model.Origamis
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs.imread
import org.opencv.imgcodecs.Imgcodecs.imwrite
import origami.Origami
import service.OrigamiService
import service.WidgetService
import java.io.File
import java.nio.file.Files

import kotlinx.html.*
import origami.Filter
import origami.Filters


fun Route.origami(origamiService: OrigamiService) {


    static("out") {
        files("out")
    }

    static("static") {
        resources("static")
    }

    route("/origami") {

        get {
            call.respond(origamiService.getAll())
        }

        delete("/{did}") {
            val did: Int = Integer.parseInt(call.parameters["did"])
            println("delete me: " + did)
            origamiService.deleteOrigami(did)
            call.respondRedirect("/origami/view")
        }

        get("/view") {
            val name = "Origami with Wasabi"
            val allOrigamis: List<model.Origami> = origamiService.getAll()
            val selectedTag = call.request.queryParameters["tag"]

            val allTags = allOrigamis.flatMap { it.tags.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()

            val displayedOrigamis = if (selectedTag != null && selectedTag.isNotEmpty()) {
                allOrigamis.filter { it.tags.split(",").map { t -> t.trim() }.contains(selectedTag) }
            } else {
                allOrigamis
            }

            call.respondHtml {
                head {
                    link(href = "https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css", rel = "stylesheet")
                    link(href = "/static/style.css", rel = "stylesheet")
                    link(
                        href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@300&display=swap",
                        rel = "stylesheet"
                    )
                    title {
                        +name
                    }
                }
                body {
                    div(classes = "container") {
                        div(classes = "row") {
                            div(classes = "col-12 my-4") {
                                h1 { +"Origami Gallery" }
                                a(href = "/", classes = "btn btn-primary") { +"Upload New" }
                            }
                        }
                        div(classes = "row") {
                            div(classes = "col-md-3") {
                                h4 { +"Tags" }
                                ul(classes = "list-group") {
                                    li(classes = "list-group-item ${if(selectedTag == null) "active" else ""}") {
                                        a(href = "/origami/view", classes = "${if(selectedTag == null) "text-white" else ""}") { +"All" }
                                    }
                                    for (tag in allTags) {
                                        val isActive = tag == selectedTag
                                        li(classes = "list-group-item ${if(isActive) "active" else ""}") {
                                            a(href = "/origami/view?tag=$tag", classes = "${if(isActive) "text-white" else ""}") { +tag }
                                        }
                                    }
                                }
                            }
                            div(classes = "col-md-9") {
                                h4 { +(if (selectedTag != null) "Tag: $selectedTag" else "All Images") }
                                div(classes = "row") {
                                    for (o: model.Origami in displayedOrigamis) {
                                        div(classes = "col-md-4 mb-4") {
                                            a(href = "/out/${o.hash}.out.jpg") {
                                                img(src = "/out/${o.hash}.out.jpg", classes = "img-fluid rounded") // img-fluid for responsiveness
                                            }
                                            if(o.tags.isNotEmpty()) {
                                                p(classes="small text-muted") { +"Tags: ${o.tags}" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        post("/image") {
            val multipart = call.receiveMultipart()
            val processor = util.ImgProcessor()
            val result = processor.processPost(multipart, origamiService)
            call.respond(result)
        }

        post("/form") {
            val multipart = call.receiveMultipart()
            val processor = util.ImgProcessor()
            util.ImgProcessor().processPost(multipart, origamiService)
            call.respondRedirect("/origami/view")
        }


    }




}


