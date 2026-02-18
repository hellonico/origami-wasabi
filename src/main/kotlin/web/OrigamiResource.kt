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
import kotlinx.serialization.builtins.ListSerializer
import model.Comment
import model.Origamis
import org.slf4j.LoggerFactory
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs.imread
import org.opencv.imgcodecs.Imgcodecs.imwrite
import org.opencv.imgproc.Imgproc
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

        get("/thumbnail/{hash}") {
            val hash = call.parameters["hash"]
            if (hash != null) {
                val cacheFile = File("out/${hash}.thumb.jpg")
                if (cacheFile.exists()) {
                    call.respondFile(cacheFile)
                } else {
                    val original = File("out/${hash}.out.jpg")
                    if (original.exists()) {
                        try {
                            val src = imread(original.absolutePath)
                            if (!src.empty()) {
                                val dst = Mat()
                                // Resize to width 600
                                val width = 600.0
                                val ratio = width / src.width()
                                val height = src.height() * ratio
                                Imgproc.resize(src, dst, Size(width, height))
                                imwrite(cacheFile.absolutePath, dst)
                                call.respondFile(cacheFile)
                            } else {
                                // Fallback if regular load fails
                                call.respondFile(original)
                            }
                        } catch(e: Exception) {
                            log.error("Error resizing image: $hash", e)
                            call.respondFile(original)
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
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

        post("/{id}/share") {
             val id = call.parameters["id"]?.toIntOrNull()
             if (id != null) {
                 val o = origamiService.getOrigami(id)
                 if (o != null) {
                     val newShares = o.shares + 1
                     origamiService.updateShares(id, newShares)
                     call.respond(HttpStatusCode.OK, mapOf("shares" to newShares))
                 } else {
                     call.respond(HttpStatusCode.NotFound)
                 }
             } else {
                 call.respond(HttpStatusCode.BadRequest)
             }
        }

        post("/{id}/comment") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                val params = call.receiveParameters()
                val text = params["text"]?.trim()

                if (id != null && !text.isNullOrBlank()) {
                    val o = origamiService.getOrigami(id)
                    if (o != null) {
                        // Use explicit List<Comment> for serialization stability
                        val commentSerializer = ListSerializer(Comment.serializer())
                        
                        val currentList: List<Comment> = try {
                            lenientJson.decodeFromString(commentSerializer, o.comments)
                        } catch(e: Exception) { 
                            log.error("Failed to parse comments: ${e.message}")
                            emptyList()
                        }
                        
                        val mutableComments = currentList.toMutableList()
                        val newComment = Comment(text, System.currentTimeMillis())
                        mutableComments.add(newComment)
                        
                        // Encode explicit
                        val json = lenientJson.encodeToString(commentSerializer, mutableComments)
                        origamiService.updateComments(id, json)
                        log.info("Updated comments for $id: $json")
                        
                        call.respond(HttpStatusCode.OK, mapOf("comments" to json))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Missing id or text")
                }
            } catch (e: Exception) {
                log.error("Error posting comment", e)
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
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
            val sortBy = call.parameters["sort"] ?: "id"
            val list = origamiService.getAll(limit, offset, tag, sortBy)
            call.respond(list)
        }

        get("/view") {
            val name = "Wasabi Gallery"
            val selectedTag = call.parameters["tag"]
            val sortBy = call.parameters["sort"] ?: "id"
            
            val allTags = origamiService.getAllTags()

            //Initial load only first batch
            val initialOrigamis: List<model.Origami> = origamiService.getAll(20, 0, selectedTag, sortBy)
            
            // Explicit serializer for list of Origamis
            val origamiSerializer = ListSerializer(model.Origami.serializer())
            val jsonOrigamis = lenientJson.encodeToString(origamiSerializer, initialOrigamis)

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
                            <nav class="navbar">
                                <div class="nav-content">
                                    <div class="logo" @click="feedMode = true; window.scrollTo(0,0);">Wasabi</div>
                                    <div class="act" style="display:flex; align-items:center;">
                                        <i class="fas fa-th-large fa-lg" x-show="feedMode" @click="feedMode = false" style="cursor:pointer; margin-right:20px; font-size: 24px;"></i>
                                        <i class="fas fa-stream fa-lg" x-show="!feedMode" @click="feedMode = true" style="cursor:pointer; margin-right:20px; font-size: 24px;"></i>
                                        <a href="/" class="upload-btn">Upload</a>
                                    </div>
                                </div>
                            </nav>

                            <!-- Grid View (Gallery) -->
                            <div class="container" x-show="!feedMode">
                                <!-- Sort Filter -->
                                <div class="tags-filter" style="margin-bottom:5px;">
                                    <a href="/origami/view" class="tag-pill ${if(sortBy == "id") "active" else ""}">Newest</a>
                                    <a href="/origami/view?sort=recent" class="tag-pill ${if(sortBy == "recent") "active" else ""}">Recent Activity</a>
                                </div>
                                
                                <!-- Tags Filter -->
                                <div class="tags-filter" style="margin-bottom:20px;">
                                    <a href="/origami/view" class="tag-pill ${if(selectedTag == null) "active" else ""}">All</a>
                                    ${allTags.joinToString("") { tag -> 
                                        "<a href='/origami/view?tag=$tag' class='tag-pill ${if(tag == selectedTag) "active" else ""}'>#$tag</a>" 
                                    }}
                                </div>

                                <!-- Gallery Grid -->
                                <div class="gallery-grid">
                                    <template x-for="img in images" :key="img.id">
                                        <div class="gallery-item" @click="openFeed(img)">
                                            <img :src="'/origami/thumbnail/' + img.hash" class="gallery-image">
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

                            <!-- Feed View (Default Browsing) -->
                            <div class="container feed-container" x-show="feedMode">
                                <div style="padding-bottom:50px;">
                                    <template x-for="img in images" :key="img.id">
                                        <div :id="'feed-item-'+img.id" class="feed-item" style="background:#fff; border:1px solid #dbdbdb; border-radius:3px; margin-bottom:60px;">
                                            <div style="padding:15px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #efefef;">
                                                <div style="font-weight:600; font-size:14px; display:flex; align-items:center;">
                                                    <div style="width:32px; height:32px; background:#efefef; border-radius:50%; margin-right:10px;"></div>
                                                    WasabiUser
                                                </div>
                                                <div style="font-size:12px; color:#8e8e8e;" x-text="new Date(img.date).toLocaleDateString()"></div>
                                            </div>
                                            
                                            <img :src="'/origami/thumbnail/' + img.hash" class="feed-image" loading="lazy" @dblclick="toggleLike(img)">
                                            
                                            <div class="feed-actions">
                                                 <div style="margin-bottom:12px; display:flex; align-items:center;">
                                                    <div style="display:flex; align-items:center; margin-right:20px;">
                                                        <i class="fa-heart fa-lg" :class="liked[img.id] ? 'fas text-red-500' : 'far'" 
                                                           style="cursor:pointer; color: #ed4956; margin-right: 5px;" @click="toggleLike(img)"></i>
                                                    </div>
                                                    
                                                    <div style="display:flex; align-items:center; margin-right:20px;">
                                                        <i class="far fa-comment fa-lg" style="cursor:pointer; margin-right: 5px;" @click="document.getElementById('comment-input-'+img.id).focus()"></i>
                                                        <span x-text="parseComments(img.comments).length || ''" style="font-weight:600; font-size: 14px;"></span>
                                                    </div>

                                                    <div style="display:flex; align-items:center;">
                                                        <i class="far fa-paper-plane fa-lg" style="cursor:pointer; margin-right: 5px;" @click="shareImage(img)"></i>
                                                        <span x-text="img.shares || ''" style="font-weight:600; font-size: 14px;"></span>
                                                    </div>
                                                 </div>
                                                 
                                                 <div style="font-weight:600; font-size:14px; margin-bottom:8px;" x-show="img.likes > 0">
                                                     <span x-text="img.likes"></span> likes
                                                 </div>
                                                 
                                                 <div class="feed-tags" style="margin-bottom:8px; padding:0;">
                                                    <template x-for="tag in img.tags ? img.tags.split(',').filter(t=>t.trim()!='') : []">
                                                         <a :href="'/origami/view?tag='+tag" style="color:#00376b; margin-right:5px; text-decoration:none;">#<span x-text="tag"></span></a>
                                                    </template>
                                                 </div>
 
                                                  <!-- Comments List -->
                                                  <div class="comments-list" style="margin-bottom:10px; padding: 0;">
                                                      <template x-for="comment in parseComments(img.comments)">
                                                          <div style="font-size:14px; margin-bottom:4px;">
                                                              <span style="font-weight:600;" x-text="formatDate(comment.date)"></span> 
                                                              <span x-text="comment.text"></span>
                                                          </div>
                                                      </template>
                                                  </div>
                                            </div>
                                            
                                            <div style="border-top:1px solid #efefef; padding:10px 15px;">
                                                 <form @submit.prevent="addComment(img, newComments[img.id]); newComments[img.id]=''" style="display:flex; align-items:center;">
                                                     <input x-model="newComments[img.id]" :id="'comment-input-'+img.id" placeholder="Add a comment..." 
                                                            style="flex:1; border:none; outline:none; font-size:14px; padding:5px 0;">
                                                     <button type="submit" x-show="newComments[img.id]" 
                                                             style="color:#0095f6; font-weight:600; background:none; border:none; padding:0; cursor:pointer; margin-left:10px;"
                                                             :style="{opacity: newComments[img.id] ? '1' : '0.3'}"
                                                             x-bind:disabled="!newComments[img.id]">Post</button>
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
                                    feedMode: true, // Default to Feed
                                    offset: 20,
                                    limit: 20,
                                    loading: false,
                                    ended: false,
                                    newComments: {},
                                    liked: {},
                                    currentTag: '${selectedTag ?: ""}',
                                    currentSort: '${sortBy ?: "id"}',
                                    
                                    init() {
                                        // Load liked state
                                        let stored = localStorage.getItem('wasabi_likes');
                                        if(stored) this.liked = JSON.parse(stored);

                                        // Universal Scroll Listener (Window)
                                        window.addEventListener('scroll', () => {
                                           if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 800) {
                                               this.loadMore();
                                           }
                                        });
                                        
                                        // Check for ID in URL
                                        const urlParams = new URLSearchParams(window.location.search);
                                        const id = urlParams.get('id');
                                        if (id) {
                                            this.fetchAndOpen(id);
                                        }
                                    },
                                    
                                    formatDate(ts) {
                                        if(!ts) return 'User';
                                        // Simple date formatting
                                        return new Date(ts).toLocaleString();
                                    },
                                    
                                    /* Robust Comment Parsing Helper */
                                    parseComments(comments) {
                                        if (!comments) return [];
                                        try {
                                            // Ensure comments is a string before parsing
                                            if (typeof comments === 'string') {
                                                if(comments === '[]') return [];
                                                return JSON.parse(comments);
                                            } else if (Array.isArray(comments)) {
                                                // Should not happen with Kotlin data mapping but safe to handle
                                                return comments;
                                            }
                                        } catch (e) {
                                            console.error("Failed to parse comments JSON", e, comments);
                                        }
                                        return [];
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
                                        // Deprecated, using window scroll now
                                    },

                                    async loadMore() {
                                        if(this.loading || this.ended) return;
                                        this.loading = true;
                                        let url = '/origami/list?limit=' + this.limit + '&offset=' + this.offset;
                                        if(this.currentTag) url += '&tag=' + encodeURIComponent(this.currentTag);
                                        if(this.currentSort) url += '&sort=' + encodeURIComponent(this.currentSort);
                                        
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
                                        setTimeout(() => {
                                            let el = document.getElementById('feed-item-' + img.id);
                                            if(el) el.scrollIntoView({ behavior: 'auto', block: 'center' });
                                        }, 50);
                                    },
                                    
                                    closeFeed() { 
                                        this.feedMode = false; 
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
                                    
                                    async shareImage(img) {
                                        let url = window.location.origin + '/origami/view?id=' + img.id;
                                        
                                        // Update Share Count (Optimistic)
                                        img.shares = (img.shares || 0) + 1;
                                        fetch('/origami/' + img.id + '/share', { method: 'POST' }).catch(e=>console.log(e));

                                        if (navigator.share) {
                                            try {
                                                await navigator.share({
                                                    title: 'Wasabi Photo',
                                                    text: 'Check out this photo on Wasabi!',
                                                    url: url,
                                                });
                                            } catch (error) { console.log('Error sharing', error); }
                                        } else {
                                            this.copyToClipboard(url);
                                        }
                                    },

                                    copyToClipboard(text) {
                                        let textArea = document.createElement("textarea");
                                        textArea.value = text;
                                        textArea.style.position = "fixed";
                                        textArea.style.left = "-9999px";
                                        document.body.appendChild(textArea);
                                        textArea.focus();
                                        textArea.select();
                                        try {
                                            document.execCommand('copy');
                                            alert("Link copied to clipboard!");
                                        } catch (err) {
                                            prompt("Copy link to share:", text);
                                        }
                                        document.body.removeChild(textArea);
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
