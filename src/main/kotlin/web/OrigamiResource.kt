package web

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
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

        get("/json/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id != null) {
                val o = origamiService.getOrigami(id)
                if(o != null) call.respond(o) else call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/{id}/tag") {
            val id = call.parameters["id"]?.toIntOrNull()
            val params = call.receiveParameters()
            val tag = params["tag"]?.trim()

            if (id != null && !tag.isNullOrBlank()) {
                val o = origamiService.getOrigami(id)
                if (o != null) {
                    val currentTags = o.tags.split(",").map{it.trim()}.filter{it.isNotEmpty()}.toMutableSet()
                    currentTags.add(tag)
                    origamiService.updateTags(id, currentTags.joinToString(","))
                    call.respond(HttpStatusCode.OK, mapOf("tags" to currentTags.joinToString(",")))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        delete("/{id}/tag/{tag}") {
            val id = call.parameters["id"]?.toIntOrNull()
            val tag = call.parameters["tag"]?.trim()

            if (id != null && !tag.isNullOrBlank()) {
                val o = origamiService.getOrigami(id)
                if (o != null) {
                    val currentTags = o.tags.split(",").map{it.trim()}.filter{it.isNotEmpty()}.toMutableSet()
                    currentTags.remove(tag)
                    origamiService.updateTags(id, currentTags.joinToString(","))
                    call.respond(HttpStatusCode.OK, mapOf("tags" to currentTags.joinToString(",")))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        delete("/{did}") {
            val did: Int = Integer.parseInt(call.parameters["did"])
            println("delete me: " + did)
            origamiService.deleteOrigami(did)
            call.respondRedirect("/origami/view")
        }

        get("/view") {
            val name = "Wasabi Gallery"
            val allOrigamis: List<model.Origami> = origamiService.getAll()
            val selectedTag = call.parameters["tag"]

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

            val jsonOrigamis = util.JsonMapper.defaultMapper.encodeToString(displayedOrigamis)

            call.respondHtml {
                head {
                    link(href = "/static/style.css", rel = "stylesheet")
                    // Grand Hotel font for logo
                    link(href="https://fonts.googleapis.com/css2?family=Grand+Hotel&display=swap", rel="stylesheet")
                    link(href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css", rel="stylesheet")
                    script(src="https://cdn.jsdelivr.net/gh/alpinejs/alpine@v2.8.2/dist/alpine.min.js") {}
                    title { +name }
                }
                body {
                    unsafe {
                        +"""
                        <div x-data="gallery()">
                            <!-- Navbar -->
                            <nav class="navbar">
                                <div class="nav-content">
                                    <div class="logo">Wasabi</div>
                                    <div class="act">
                                        <a href="/" class="upload-btn">Upload</a>
                                    </div>
                                </div>
                            </nav>

                            <div class="container">
                                <!-- Tags Filter -->
                                <div class="tags-filter">
                                    <a href="/origami/view" class="tag-pill ${if(selectedTag == null) "active" else ""}">All</a>
                                    ${allTags.joinToString("") { tag -> 
                                        "<a href='/origami/view?tag=$tag' class='tag-pill ${if(tag == selectedTag) "active" else ""}'>#$tag</a>" 
                                    }}
                                </div>

                                <!-- Gallery Grid -->
                                <div class="gallery-grid">
                                    <template x-for="img in images" :key="img.id">
                                        <div class="gallery-item" @click="selectImage(img)">
                                            <img :src="'/out/' + img.hash + '.out.jpg'" class="gallery-image">
                                            <div class="gallery-overlay">
                                                <span><i class="fas fa-heart"></i> <span x-text="img.tags ? img.tags.split(',').filter(t=>t.trim()!='').length : 0"></span> Tags</span>
                                            </div>
                                        </div>
                                    </template>
                                </div>
                            </div>

                            <!-- Modal -->
                            <div class="modal" x-show="selectedImage" @click.self="close()" style="display: none;">
                                <div class="close-modal" @click="close()">&times;</div>
                                <div class="modal-content" x-show="selectedImage">
                                    <div class="modal-image-container">
                                        <img :src="selectedImage ? '/out/' + selectedImage.hash + '.out.jpg' : ''" class="modal-image">
                                    </div>
                                    <div class="modal-info">
                                        <div class="modal-header">
                                            <span x-text="'Image #' + (selectedImage ? selectedImage.id : '')"></span>
                                        </div>
                                        <div class="modal-body">
                                            <div class="modal-tags">
                                                <template x-for="tag in (selectedImage ? (selectedImage.tags ? selectedImage.tags.split(',').filter(t=>t.trim()!='') : []) : [])">
                                                    <span class="modal-tag">
                                                        <span x-text="'#' + tag"></span>
                                                        <span class="modal-tag-delete" @click="removeTag(tag)">&times;</span>
                                                    </span>
                                                </template>
                                            </div>
                                            <div x-show="(!selectedImage || !selectedImage.tags || selectedImage.tags.length === 0)" class="text-muted" style="padding:20px; text-align:center; color:#999;">
                                                No tags yet.
                                            </div>
                                        </div>
                                        <div class="modal-footer">
                                            <form @submit.prevent="addTag()" class="add-tag-form">
                                                <input type="text" x-model="newTag" class="tag-input" placeholder="Add a tag...">
                                                <button type="submit" class="post-btn" :disabled="!newTag">Post</button>
                                            </form>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <script>
                            function gallery() {
                                return {
                                    images: $jsonOrigamis,
                                    selectedImage: null,
                                    newTag: '',
                                    selectImage(img) { 
                                        this.selectedImage = img; 
                                        document.body.style.overflow = 'hidden';
                                    },
                                    close() { 
                                        this.selectedImage = null; 
                                        document.body.style.overflow = 'auto';
                                    },
                                    async addTag() {
                                        if(!this.newTag) return;
                                        let id = this.selectedImage.id;
                                        let tag = this.newTag;
                                        let formData = new URLSearchParams();
                                        formData.append('tag', tag);
                                        
                                        let res = await fetch('/origami/' + id + '/tag', {
                                            method: 'POST',
                                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                            body: formData
                                        });

                                        if(res.ok) {
                                            let data = await res.json();
                                            // Update local state
                                            this.selectedImage.tags = data.tags;
                                            this.newTag = '';
                                            // Find in list to update grid overlay count
                                            let idx = this.images.findIndex(i => i.id === id);
                                            if(idx > -1) this.images[idx].tags = data.tags;
                                        }
                                    },
                                    async removeTag(tag) {
                                        if(!confirm('Delete tag ' + tag + '?')) return;
                                        let id = this.selectedImage.id;
                                        let res = await fetch('/origami/' + id + '/tag/' + encodeURIComponent(tag), {
                                            method: 'DELETE'
                                        });

                                        if(res.ok) {
                                            let data = await res.json();
                                            this.selectedImage.tags = data.tags;
                                            let idx = this.images.findIndex(i => i.id === id);
                                            if(idx > -1) this.images[idx].tags = data.tags;
                                        }
                                    }
                                }
                            }
                        </script>
                        """
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


