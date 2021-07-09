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
            val olist: List<model.Origami> = origamiService.getAll()

            call.respondHtml {
                head {
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
                    div(classes = "home") {
                        span { +"Gallery" }
                        a(href = "/") {
                            img(src = "/static/wasabi.png")
                        }

                    }
                    br {

                    }
                    div {
                        for (o: model.Origami in olist) {
                            a(href = "/out/${o.hash}.out.jpg") {
                                img(src = "/out/${o.hash}.out.jpg")
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


