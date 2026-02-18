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

        get("/filters") {
            try {
                call.respond(origami.FindFilters.findFilters())
            } catch(e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Error")
            }
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

        get("/list") {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.parameters["offset"]?.toLongOrNull() ?: 0L
            val tag = call.parameters["tag"]
            val list = origamiService.getAll(limit, offset, tag)
            call.respond(list)
        }

        get("/view") {
            val name = "Wasabi Gallery"
            val selectedTag = call.parameters["tag"]
            val allTags = origamiService.getAllTags()

            //Initial load only first batch
            val initialOrigamis: List<model.Origami> = origamiService.getAll(20, 0, selectedTag)
            val jsonOrigamis = util.JsonMapper.defaultMapper.encodeToString(initialOrigamis)

            call.respondHtml {
                head {
                    link(href = "/static/style.css", rel = "stylesheet")
                    link(href="https://fonts.googleapis.com/css2?family=Grand+Hotel&display=swap", rel="stylesheet")
                    link(href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css", rel="stylesheet")
                    script(src="https://cdn.jsdelivr.net/gh/alpinejs/alpine@v2.8.2/dist/alpine.min.js") {}
                    title { +name }
                }
                body {
                    unsafe {
                        +"""
                        <div x-data="gallery()" x-init="init()">
                            <nav class="navbar" x-show="!feedMode">
                                <div class="nav-content">
                                    <div class="logo">Wasabi</div>
                                    <div class="act">
                                        <a href="/" class="upload-btn">Upload</a>
                                    </div>
                                </div>
                            </nav>

                            <!-- Grid View -->
                            <div class="container" x-show="!feedMode">
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
                                        <div class="gallery-item" @click="openFeed(img)">
                                            <img :src="'/out/' + img.hash + '.out.jpg'" class="gallery-image">
                                            <div class="gallery-overlay">
                                                <span><i class="fas fa-heart"></i> <span x-text="img.tags ? img.tags.split(',').filter(t=>t.trim()!='').length : 0"></span></span>
                                            </div>
                                        </div>
                                    </template>
                                </div>
                                <div x-show="loading" style="text-align:center; padding:20px;">
                                    <i class="fas fa-spinner fa-spin fa-2x"></i>
                                </div>
                            </div>

                            <!-- Feed View (Full Screen) -->
                            <div class="feed-view" x-show="feedMode" style="display:none;" @scroll="onFeedScroll(${'$'}event)">
                                <div class="feed-header">
                                    <div class="feed-close-btn" @click="closeFeed()"><i class="fas fa-chevron-left"></i></div>
                                    <div style="flex:1; text-align:center; font-weight:600; font-family:'Grand Hotel', cursive; font-size:24px;">Posts</div>
                                    <div style="width:24px;"></div><!-- Spacer -->
                                </div>
                                
                                <div class="container" style="padding-bottom:50px;">
                                    <template x-for="img in images" :key="img.id">
                                        <div :id="'feed-item-'+img.id" class="feed-item" x-data="{ localTag: '' }">
                                            <div style="padding:10px; display:flex; align-items:center;">
                                                <div style="font-weight:600; font-size:14px;" x-text="'Image #' + img.id"></div>
                                            </div>
                                            
                                            <img :src="'/out/' + img.hash + '.out.jpg'" class="feed-image" loading="lazy">
                                            
                                            <div class="feed-actions">
                                                 <div style="margin-bottom:8px;">
                                                    <i class="far fa-heart fa-lg" style="margin-right:15px; cursor:pointer;"></i>
                                                    <i class="far fa-comment fa-lg" style="margin-right:15px; cursor:pointer;" @click="${'$'}refs.tagInput.focus()"></i>
                                                    <i class="far fa-paper-plane fa-lg" style="cursor:pointer;"></i>
                                                 </div>
                                                 
                                                 <div class="feed-tags" style="margin-bottom:8px; padding:0;">
                                                    <template x-for="tag in img.tags ? img.tags.split(',').filter(t=>t.trim()!='') : []">
                                                         <span style="color:#00376b; margin-right:5px;">
                                                            #<span x-text="tag"></span>
                                                            <span style="color:#ed4956; cursor:pointer; font-size:10px;" @click="removeTag(img, tag)">&times;</span>
                                                         </span>
                                                    </template>
                                                    <div x-show="!img.tags || img.tags.length===0" style="color:#8e8e8e; font-size:12px;">No tags yet</div>
                                                 </div>

                                                 <form @submit.prevent="addTag(img, localTag); localTag=''">
                                                     <input x-model="localTag" x-ref="tagInput" placeholder="Add a tag..." 
                                                            style="width:100%; border:none; outline:none; font-size:14px; padding:5px 0;">
                                                     <button type="submit" x-show="localTag" style="color:#0095f6; font-weight:600; background:none; border:none; padding:0; cursor:pointer;">Post</button>
                                                 </form>
                                            </div>
                                        </div>
                                    </template>
                                    <div x-show="loading" style="text-align:center; padding:20px;">
                                        <i class="fas fa-spinner fa-spin fa-2x"></i>
                                    </div>
                                </div>
                            </div>

                        </div>

                        <script>
                            function gallery() {
                                return {
                                    images: $jsonOrigamis,
                                    feedMode: false,
                                    offset: 20,
                                    limit: 20,
                                    loading: false,
                                    ended: false,
                                    currentTag: '${selectedTag ?: ""}',
                                    
                                    init() {
                                        window.addEventListener('scroll', () => {
                                            if(!this.feedMode) {
                                               if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 500) {
                                                   this.loadMore();
                                               }
                                            }
                                        });
                                    },
                                    
                                    onFeedScroll(e) {
                                        // Endless scroll in feed view
                                        const el = e.target;
                                        if ((el.scrollHeight - el.scrollTop - el.clientHeight) < 500) {
                                            this.loadMore();
                                        }
                                    },

                                    async loadMore() {
                                        if(this.loading || this.ended) return;
                                        this.loading = true;
                                        let url = '/origami/list?limit=' + this.limit + '&offset=' + this.offset;
                                        if(this.currentTag) url += '&tag=' + encodeURIComponent(this.currentTag);
                                        
                                        let res = await fetch(url);
                                        if(res.ok) {
                                            let newImages = await res.json();
                                            if(newImages.length > 0) {
                                                this.images = this.images.concat(newImages);
                                                this.offset += newImages.length;
                                            } else {
                                                this.ended = true;
                                            }
                                        }
                                        this.loading = false;
                                    },
                                    
                                    openFeed(img) { 
                                        this.feedMode = true; 
                                        document.body.style.overflow = 'hidden';
                                        
                                        // Wait for rendering then scroll
                                        setTimeout(() => {
                                            let el = document.getElementById('feed-item-' + img.id);
                                            if(el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
                                        }, 50);
                                    },
                                    
                                    closeFeed() { 
                                        this.feedMode = false; 
                                        document.body.style.overflow = 'auto';
                                    },

                                    async addTag(img, tag) {
                                        if(!tag) return;
                                        let id = img.id;
                                        let formData = new URLSearchParams();
                                        formData.append('tag', tag);
                                        
                                        let res = await fetch('/origami/' + id + '/tag', {
                                            method: 'POST',
                                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                            body: formData
                                        });

                                        if(res.ok) {
                                            let data = await res.json();
                                            // Update local state by finding image in array
                                            let idx = this.images.findIndex(i => i.id === id);
                                            if(idx > -1) this.images[idx].tags = data.tags;
                                        }
                                    },

                                    async removeTag(img, tag) {
                                        if(!confirm('Delete tag ' + tag + '?')) return;
                                        let id = img.id;
                                        let res = await fetch('/origami/' + id + '/tag/' + encodeURIComponent(tag), {
                                            method: 'DELETE'
                                        });

                                        if(res.ok) {
                                            let data = await res.json();
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
        
        post("/preview") {
            val multipart = call.receiveMultipart()
            val result = util.ImgProcessor().processPreview(multipart)
            if (result != null) {
                call.respondBytes(result, ContentType.Image.JPEG)
            } else {
                call.respond(HttpStatusCode.BadRequest)
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
