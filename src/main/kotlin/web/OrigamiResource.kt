package web

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Comment
import model.Origamis
import org.slf4j.LoggerFactory
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

    val log = LoggerFactory.getLogger("OrigamiWeb")
    val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

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

        get("/tags") {
            try {
                call.respond(origamiService.getAllTags())
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

        post("/{id}/like") {
             val id = call.parameters["id"]?.toIntOrNull()
             if (id != null) {
                 val o = origamiService.getOrigami(id)
                 if (o != null) {
                     val newLikes = o.likes + 1
                     origamiService.updateLikes(id, newLikes)
                     call.respond(HttpStatusCode.OK, mapOf("likes" to newLikes))
                 } else {
                     call.respond(HttpStatusCode.NotFound)
                 }
             } else {
                 call.respond(HttpStatusCode.BadRequest)
             }
        }

        post("/{id}/comment") {
            val id = call.parameters["id"]?.toIntOrNull()
            val params = call.receiveParameters()
            val text = params["text"]?.trim()

            if (id != null && !text.isNullOrBlank()) {
                val o = origamiService.getOrigami(id)
                if (o != null) {
                    val currentComments: MutableList<Comment> = try {
                        lenientJson.decodeFromString(o.comments)
                    } catch(e: Exception) { 
                        log.error("Failed to parse comments: ${e.message}")
                        mutableListOf() 
                    }
                    
                    val newComment = Comment(text, System.currentTimeMillis())
                    currentComments.add(newComment)
                    
                    val json = lenientJson.encodeToString(currentComments)
                    origamiService.updateComments(id, json)
                    log.info("Updated comments: $json")
                    
                    call.respond(HttpStatusCode.OK, mapOf("comments" to json))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
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
                    meta(name = "viewport", content = "width=device-width, initial-scale=1, shrink-to-fit=no, viewport-fit=cover")
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
                                                <span><i class="fas fa-heart"></i> <span x-text="img.likes || 0"></span></span>
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
                                        <div :id="'feed-item-'+img.id" class="feed-item">
                                            <div style="padding:10px; display:flex; align-items:center; justify-content:space-between;">
                                                <div style="font-weight:600; font-size:14px;" x-text="'Image #' + img.id"></div>
                                                <div style="font-size:12px; color:#8e8e8e;" x-text="new Date(img.date).toLocaleDateString()"></div>
                                            </div>
                                            
                                            <img :src="'/out/' + img.hash + '.out.jpg'" class="feed-image" loading="lazy" @dblclick="toggleLike(img)">
                                            
                                            <div class="feed-actions">
                                                 <div style="margin-bottom:8px; display:flex; align-items:center;">
                                                    <i class="fa-heart fa-lg" :class="liked[img.id] ? 'fas text-red-500' : 'far'" 
                                                       style="margin-right:15px; cursor:pointer; color: #ed4956;" @click="toggleLike(img)"></i>
                                                    <i class="far fa-comment fa-lg" style="margin-right:15px; cursor:pointer;" @click="document.getElementById('comment-input-'+img.id).focus()"></i>
                                                    <i class="far fa-paper-plane fa-lg" style="cursor:pointer;" @click="shareImage(img)"></i>
                                                 </div>
                                                 
                                                 <div style="font-weight:600; font-size:14px; margin-bottom:5px;" x-show="img.likes > 0">
                                                     <span x-text="img.likes"></span> likes
                                                 </div>
                                                 
                                                 <div class="feed-tags" style="margin-bottom:8px; padding:0;">
                                                    <template x-for="tag in img.tags ? img.tags.split(',').filter(t=>t.trim()!='') : []">
                                                         <span style="color:#00376b; margin-right:5px;">#<span x-text="tag"></span></span>
                                                    </template>
                                                 </div>

                                                 <!-- Comments List -->
                                                  <div class="comments-list" style="margin-bottom:10px; padding: 0 15px;">
                                                      <template x-for="comment in (img.comments ? JSON.parse(img.comments) : [])">
                                                          <div style="font-size:14px; margin-bottom:4px;">
                                                              <span style="font-weight:600;" x-text="comment.author"></span> 
                                                              <span x-text="comment.text"></span>
                                                          </div>
                                                      </template>
                                                  </div>

                                                 <form @submit.prevent="addComment(img, newComments[img.id]); newComments[img.id]=''" style="padding: 0 15px;">
                                                     <input x-model="newComments[img.id]" :id="'comment-input-'+img.id" placeholder="Add a comment..." 
                                                            style="width:100%; border:none; outline:none; font-size:14px; padding:10px 0; border-top:1px solid #efefef;">
                                                     <button type="submit" x-show="newComments[img.id]" style="color:#0095f6; font-weight:600; background:none; border:none; padding:0; cursor:pointer; float:right;">Post</button>
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
                                    newComments: {},
                                    liked: {},
                                    currentTag: '${selectedTag ?: ""}',
                                    
                                    init() {
                                        // Load liked state
                                        let stored = localStorage.getItem('wasabi_likes');
                                        if(stored) this.liked = JSON.parse(stored);

                                        window.addEventListener('scroll', () => {
                                            if(!this.feedMode) {
                                               if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 500) {
                                                   this.loadMore();
                                               }
                                            }
                                        });
                                        
                                        // Check for ID in URL
                                        const urlParams = new URLSearchParams(window.location.search);
                                        const id = urlParams.get('id');
                                        if (id) {
                                            this.fetchAndOpen(id);
                                        }
                                    },
                                    
                                    async fetchAndOpen(id) {
                                        // Check local
                                        let img = this.images.find(i => i.id == id);
                                        if (img) {
                                            this.openFeed(img);
                                        } else {
                                            // Fetch from server
                                            try {
                                                let res = await fetch('/origami/json/' + id);
                                                if(res.ok) {
                                                    let img = await res.json();
                                                    // Add to top of images list
                                                    this.images.unshift(img);
                                                    this.openFeed(img);
                                                }
                                            } catch(e) { console.error('Error fetching image', e); }
                                        }
                                    },
                                    
                                    onFeedScroll(e) {
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
                                        setTimeout(() => {
                                            let el = document.getElementById('feed-item-' + img.id);
                                            if(el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
                                        }, 50);
                                    },
                                    
                                    closeFeed() { 
                                        this.feedMode = false; 
                                        document.body.style.overflow = 'auto';
                                        // Update URL back to clean
                                        window.history.pushState({}, '', '/origami/view');
                                    },

                                    async toggleLike(img) {
                                        if(this.liked[img.id]) return;
                                        this.liked[img.id] = true;
                                        localStorage.setItem('wasabi_likes', JSON.stringify(this.liked));
                                        
                                        img.likes = (img.likes || 0) + 1;
                                        await fetch('/origami/' + img.id + '/like', { method: 'POST' });
                                    },

                                    async addComment(img, text) {
                                        if(!text) return;
                                        let id = img.id;
                                        let formData = new URLSearchParams();
                                        formData.append('text', text);
                                        
                                        let res = await fetch('/origami/' + id + '/comment', {
                                            method: 'POST',
                                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                            body: formData
                                        });

                                        if(res.ok) {
                                            let data = await res.json();
                                            img.comments = data.comments; // Update reactive list
                                        }
                                    },
                                    
                                    shareImage(img) {
                                        let url = window.location.origin + '/origami/view?id=' + img.id;
                                        if (navigator.share) {
                                            navigator.share({
                                                title: 'Wasabi Photo',
                                                text: 'Check out this photo on Wasabi!',
                                                url: url,
                                            }).catch((error) => console.log('Error sharing', error));
                                        } else {
                                            prompt("Copy link to share:", url);
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
