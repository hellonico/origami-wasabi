package web

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.Route
import io.ktor.routing.route
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


@ExperimentalCoroutinesApi
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

        get("/view") {
            val name = "Origami with Wasabi"
            val olist: List<model.Origami> = origamiService.getAll()

            call.respondHtml {
                head {
                    link(href="/static/style.css",rel="stylesheet")
                    title {
                        +name
                    }
                }
                body {
                        img(src="static/wasabi.png")
                        for(o:model.Origami in olist) {
                                a(href="/out/${o.hash}.out.jpg") {
                                    img(src="/out/${o.hash}.out.jpg")
                                }
                        }
                }
            }
        }

        post("/image") {
            val multipart = call.receiveMultipart()
            val parts: List<PartData> = multipart.readAllParts()

            val imgfile = createTempFile("tmp_", ".jpg")
            val filterfile = createTempFile("tmp_", ".edn")

            for (part in parts) {
                println("Part: ${part.name} -> ${part.contentType}")
                when (part) {
                    is PartData.FileItem -> {
                        if (part.originalFileName!!.endsWith(".edn")) {
                            savePartToFile(part, filterfile)
                        } else {
                            savePartToFile(part, imgfile)
                        }
                    }
                    is PartData.FormItem -> {
                        print("form item ${part.value}")
                        part.value.byteInputStream().use { inputStream ->
                            filterfile.outputStream().buffered().use {
                                inputStream.copyTo(it)
                            }
                        }
                    }
                    is PartData.BinaryItem -> {
                        println("can do something here")
                    }
                }
                part.dispose()
            }

            val hash:Int = imgfile.absolutePath.hashCode()
            val fileIn:File = File("out/${hash}.in.${imgfile.extension}")
            val fileOut:File = File("out/${hash}.out.${imgfile.extension}")
            val _filter:File = File("out/${hash}.filter.edn")
            Files.move(imgfile.toPath(), fileIn.toPath())
            Files.move(filterfile.toPath(), _filter.toPath())

            val mat = imread(fileIn.absolutePath)
            // filter or not
            try {
                val filter = Origami.StringToFilter(_filter)
                val out: Mat = filter.apply(mat)
                imwrite(fileOut.absolutePath, out )
            } catch(e:Exception) {
                imwrite(fileOut.absolutePath, mat )
            }

            origamiService.addOrigami(model.Origami(id=0,hash=hash,date=System.currentTimeMillis()))

            call.respondFile(fileOut)

        }

    }
}

fun savePartToFile(
    part: PartData.FileItem,
    file: File
) {
    part.streamProvider().use { inputStream ->
        file.outputStream().buffered().use {
            inputStream.copyTo(it)
        }
    }
}
