package util

import io.ktor.http.content.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import origami.Filter
import origami.Filters
import origami.Origami
import service.OrigamiService
import java.io.File
import java.nio.file.Files

@ExperimentalCoroutinesApi
class ImgProcessor {

    suspend fun processPost(
        multipart: MultiPartData, origamiService: OrigamiService
    ): List<Int> {
        val parts: List<PartData> = multipart.readAllParts()
        val filter = loadFilter(parts)
        val tagsPart = parts.find { it.name == "tags" && it is PartData.FormItem } as? PartData.FormItem
        val tags = tagsPart?.value ?: ""

        val result : ArrayList<Int> = arrayListOf<Int>()

        for (part in parts) {
            println("Part: ${part.name} -> ${part.contentType}")
            if(part.name == "filter" || part.name == "tags") continue
            when (part) {
                is PartData.FileItem -> {
                    val imgfile = File.createTempFile("tmp_", ".jpg")
                    savePartToFile(part, imgfile)
                    val hash: Int = imgfile.absolutePath.hashCode()
                    val fileIn = File("out/${hash}.in.${imgfile.extension}")
                    println("<< "+fileIn)
                    fileIn.parentFile.mkdirs()
                    Files.move(imgfile.toPath(), fileIn.toPath())
                    val fileOut = File("out/${hash}.out.${imgfile.extension}")
                    println(">>"+ fileOut)

                    val mat = Imgcodecs.imread(fileIn.absolutePath)
                    val out: Mat = filter.apply(mat)
                    Imgcodecs.imwrite(fileOut.absolutePath, out)
                    origamiService.addOrigami(model.Origami(id = 0, hash = hash, date = System.currentTimeMillis(), tags = tags))
                    result.add(hash)
                }
                else -> {}
            }
            part.dispose()
        }
        return result
    }

    private fun savePartToFile(
        part: PartData.FileItem,
        file: File
    ) {
        part.streamProvider().use { inputStream ->
            file.outputStream().buffered().use {
                inputStream.copyTo(it)
            }
        }
    }

    suspend fun processPreview(multipart: MultiPartData): ByteArray? {
        val parts: List<PartData> = multipart.readAllParts()
        val filter = loadFilter(parts)
        var result: ByteArray? = null

        for (part in parts) {
            if(part.name == "customFile" && part is PartData.FileItem) {
                val imgfile = File.createTempFile("tmp_preview_", ".jpg")
                savePartToFile(part, imgfile)
                val mat = Imgcodecs.imread(imgfile.absolutePath)
                
                // Resize for preview (Max width 800)
                val previewMat = Mat()
                val targetWidth = 800.0
                if (mat.width() > targetWidth) {
                    val ratio = targetWidth / mat.width()
                    val newHeight = mat.height() * ratio
                    Imgproc.resize(mat, previewMat, Size(targetWidth, newHeight))
                } else {
                    mat.copyTo(previewMat)
                }
                
                val out: Mat = filter.apply(previewMat)
                val buffer = org.opencv.core.MatOfByte()
                Imgcodecs.imencode(".jpg", out, buffer)
                result = buffer.toArray()
                imgfile.delete()
            }
            part.dispose()
        }
        return result
    }

    private fun loadFilter(parts: List<PartData>): Filter {
        val filterClassPart = parts.find { it.name == "filterClass" && it is PartData.FormItem } as? PartData.FormItem
        if (filterClassPart != null && filterClassPart.value.isNotBlank()) {
            try {
                val className = filterClassPart.value
                val clazz = Class.forName(className)
                return clazz.newInstance() as Filter
            } catch(e: Exception) {
                println("Failed to load filter class: ${e.message}")
            }
        }

        val filterPart = parts.filter { it.name == "filter" }.toList()
        if (filterPart.size == 0) {
            return Filters.NoOP();
        } else {
            val part: PartData = filterPart[0]
            val filterfile = File.createTempFile("tmp_", ".edn")
            when (part) {
                is PartData.FileItem -> {
                    savePartToFile(part, filterfile)
                }
                // filter is coming as form text
                is PartData.FormItem -> {
                    print("form item ${part.value}")
                    part.value.byteInputStream().use { inputStream ->
                        filterfile.outputStream().buffered().use {
                            inputStream.copyTo(it)
                        }
                    }
                }
                else -> {}
            }
            try {
                return Origami.StringToFilter(filterfile)
            } catch (e:Exception) {
                println("Could not load filter ${e.message}")
                return Filters.NoOP()
            }

        }
    }

}