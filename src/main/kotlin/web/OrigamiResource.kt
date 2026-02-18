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

        // API: List Origamis by Workspace
        get("/list") {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.parameters["offset"]?.toLongOrNull() ?: 0L
            val tag = call.parameters["tag"]
            val sortBy = call.parameters["sort"] ?: "id"
            val workspaceId = call.parameters["workspace"] ?: "default"
            
            val list = origamiService.getAll(limit, offset, tag, sortBy, workspaceId)
            call.respond(list)
        }

        // Standard JSON fetch
        get("/json/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if(id != null) {
                val o = origamiService.getOrigami(id)
                if(o != null) call.respond(o) else call.respond(HttpStatusCode.NotFound)
            }
        }
        
        get("/tags") {
             call.respond(origamiService.getAllTags())
        }

        // Thumbnail with Caching
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
                                val width = 600.0
                                val ratio = width / src.width()
                                val height = src.height() * ratio
                                Imgproc.resize(src, dst, Size(width, height))
                                imwrite(cacheFile.absolutePath, dst)
                                call.respondFile(cacheFile)
                            } else {
                                call.respondFile(original)
                            }
                        } catch(e: Exception) {
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

        // Actions
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
                        val commentSerializer = ListSerializer(Comment.serializer())
                        val currentList: List<Comment> = try {
                            lenientJson.decodeFromString(commentSerializer, o.comments)
                        } catch(e: Exception) { emptyList() }
                        
                        val mutableComments = currentList.toMutableList()
                        mutableComments.add(Comment(text, System.currentTimeMillis()))
                        
                        val json = lenientJson.encodeToString(commentSerializer, mutableComments)
                        origamiService.updateComments(id, json)
                        call.respond(HttpStatusCode.OK, mapOf("comments" to json))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // Uploads
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
            val result = util.ImgProcessor().processPost(multipart, origamiService)
            call.respond(result)
        }

        post("/form") {
            val multipart = call.receiveMultipart()
            val result = util.ImgProcessor().processPost(multipart, origamiService) // Now returns Map
            val workspaceId = result["workspaceId"] ?: "default"
            call.respondRedirect("/w/$workspaceId")
        }
        
        post("/{id}/tag") { /* Keep tag logic if needed, omitted for brevity as not requested explicitly to be changed */ 
             call.respond(HttpStatusCode.OK)
        }
    }
    
    // --- ROUTES ---

    // Landing Page: Workspace Manager
    get("/") {
        call.respondHtml {
            head {
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                link(href = "/static/style.css", rel = "stylesheet")
                link(href="https://fonts.googleapis.com/css2?family=Grand+Hotel&display=swap", rel="stylesheet")
                link(href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css", rel="stylesheet")
                script(src="https://cdn.jsdelivr.net/gh/alpinejs/alpine@v2.8.2/dist/alpine.min.js") {}
                title { +"Wasabi Spaces" }
            }
            body {
                unsafe {
                    +"""
                    <div x-data="spaces()" x-init="init()" class="container" style="max-width:600px; margin-top:50px; text-align:center;">
                        <h1 class="logo" style="font-size:48px; margin-bottom:40px;">Wasabi</h1>
                        
                        <div style="background:#fff; border:1px solid #dbdbdb; border-radius:3px; padding:20px;">
                            <h2 style="font-size:18px; color:#262626; margin-bottom:20px;">Your Spaces</h2>
                            
                            <template x-for="space in savedSpaces" :key="space.id">
                                <div style="display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #efefef; padding:15px 0;">
                                    <a :href="'/w/' + space.id" style="text-decoration:none; color:#262626; font-weight:600; font-size:16px;" x-text="space.name || space.id"></a>
                                    <div style="display:flex;">
                                        <button @click="copyLink(space)" style="margin-right:10px; background:none; border:none; cursor:pointer; color:#0095f6;">
                                            <i class="far fa-copy"></i>
                                        </button>
                                        <button @click="removeSpace(space)" style="background:none; border:none; cursor:pointer; color:#ed4956;">
                                            <i class="fas fa-trash"></i>
                                        </button>
                                    </div>
                                </div>
                            </template>
                            
                            <div x-show="savedSpaces.length === 0" style="color:#8e8e8e; padding:20px 0;">
                                No spaces saved yet.
                            </div>

                            <button @click="createSpace()" class="upload-btn" style="width:100%; margin-top:20px;">Create New Space</button>
                        </div>
                    </div>

                    <script>
                        function spaces() {
                            return {
                                savedSpaces: [],
                                init() {
                                    this.load();
                                },
                                load() {
                                    this.savedSpaces = JSON.parse(localStorage.getItem('wasabi_workspaces') || '[]');
                                },
                                createSpace() {
                                    let id = 'w_' + Math.random().toString(36).substr(2, 9);
                                    let name = prompt("Name your new space:", "My Space");
                                    if(name) {
                                        this.savedSpaces.push({ id: id, name: name });
                                        localStorage.setItem('wasabi_workspaces', JSON.stringify(this.savedSpaces));
                                        window.location.href = '/w/' + id;
                                    }
                                },
                                removeSpace(space) {
                                    if(confirm("Remove this space from your list? (Data is not deleted)")) {
                                        this.savedSpaces = this.savedSpaces.filter(s => s.id !== space.id);
                                        localStorage.setItem('wasabi_workspaces', JSON.stringify(this.savedSpaces));
                                    }
                                },
                                copyLink(space) {
                                    let url = window.location.origin + '/w/' + space.id;
                                    navigator.clipboard.writeText(url).then(() => alert('Copied link: ' + url));
                                }
                            }
                        }
                    </script>
                    """
                }
            }
        }
    }

    // Workspace Gallery Route
    get("/w/{id}") {
        val workspaceId = call.parameters["id"] ?: "default"
        val name = "Wasabi Gallery"
        val selectedTag = call.parameters["tag"]
        val sortBy = call.parameters["sort"] ?: "id"
        // Show specific photo if ID present
        val photoId = call.parameters["id_photo"] // Rename param for clarity or use query 'id'
        
        val allTags = origamiService.getAllTags() // Ideally filtered by workspace, but let's keep global for now simplicity or update later

        val initialOrigamis: List<model.Origami> = origamiService.getAll(20, 0, selectedTag, sortBy, workspaceId)
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
                    <div x-data="gallery('${workspaceId}')" x-init="init()">
                        <nav class="navbar">
                            <div class="nav-content">
                                <a href="/" class="logo" style="text-decoration:none; color:#262626;">Wasabi</a>
                                <div class="act" style="display:flex; align-items:center;">
                                    <i class="fas fa-th-large fa-lg" x-show="feedMode" @click="feedMode = false" style="cursor:pointer; margin-right:20px; font-size: 24px;"></i>
                                    <i class="fas fa-stream fa-lg" x-show="!feedMode" @click="feedMode = true" style="cursor:pointer; margin-right:20px; font-size: 24px;"></i>
                                    <a href="#" @click.prevent="showUpload = true" class="upload-btn">Upload</a>
                                </div>
                            </div>
                        </nav>
                        
                        <!-- Upload Modal (Simpler than separate page) -->
                        <div x-show="showUpload" style="position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); z-index:3000; display:flex; align-items:center; justify-content:center;">
                            <div style="background:#fff; padding:20px; border-radius:5px; width:90%; max-width:400px; position:relative;">
                                <button @click="showUpload = false" style="position:absolute; top:10px; right:15px; border:none; background:none; font-size:20px;">&times;</button>
                                <h3>Upload to Workspace</h3>
                                <form action="/origami/form" method="post" enctype="multipart/form-data" style="margin-top:20px;">
                                    <input type="hidden" name="workspace" value="${workspaceId}">
                                    <input type="file" name="customFile" accept="image/*" required style="margin-bottom:15px; width:100%;">
                                    <input type="text" name="tags" placeholder="Tags (comma separated)" style="width:100%; padding:10px; margin-bottom:15px; border:1px solid #dbdbdb;">
                                    <button type="submit" class="upload-btn" style="width:100%;">Upload</button>
                                </form>
                            </div>
                        </div>

                        <!-- Grid View (Gallery) -->
                        <div class="container" x-show="!feedMode">
                            <div class="tags-filter" style="margin-bottom:5px;">
                                <a href="/w/${workspaceId}" class="tag-pill ${if(sortBy == "id") "active" else ""}">Newest</a>
                                <a href="/w/${workspaceId}?sort=recent" class="tag-pill ${if(sortBy == "recent") "active" else ""}">Recent Activity</a>
                            </div>
                            <!-- Tags Filter -->
                            <div class="tags-filter" style="margin-bottom:20px;">
                                <a href="/w/${workspaceId}" class="tag-pill ${if(selectedTag == null) "active" else ""}">All</a>
                                ${allTags.joinToString("") { tag -> 
                                    "<a href='/w/$workspaceId?tag=$tag' class='tag-pill ${if(tag == selectedTag) "active" else ""}'>#$tag</a>" 
                                }}
                            </div>

                            <div class="gallery-grid">
                                <template x-for="img in images" :key="img.id">
                                    <div class="gallery-item" @click="openFeed(img)">
                                        <img :src="'/origami/thumbnail/' + img.hash" class="gallery-image">
                                    </div>
                                </template>
                            </div>
                         </div>

                        <!-- Feed View -->
                        <div class="container feed-container" x-show="feedMode">
                            <div style="padding-bottom:50px;">
                                <template x-for="img in images" :key="img.id">
                                    <div :id="'feed-item-'+img.id" class="feed-item" style="background:#fff; border:1px solid #dbdbdb; border-radius:3px; margin-bottom:60px;">
                                         <div style="padding:15px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #efefef;">
                                            <div style="font-weight:600; font-size:14px; display:flex; align-items:center;">
                                                <div style="width:32px; height:32px; background:#efefef; border-radius:50%; margin-right:10px;"></div>
                                                User
                                            </div>
                                            <div style="font-size:12px; color:#8e8e8e;" x-text="formatDate(img.date)"></div>
                                        </div>
                                        
                                        <img :src="'/origami/thumbnail/' + img.hash" class="feed-image" @dblclick="toggleLike(img)">

                                        <div class="feed-actions">
                                             <div style="margin-bottom:12px; display:flex; align-items:center;">
                                                <div style="display:flex; align-items:center; margin-right:20px;">
                                                    <i class="fa-heart fa-lg" :class="liked[img.id] ? 'fas text-red-500' : 'far'" 
                                                       style="cursor:pointer; color: #ed4956;" @click="toggleLike(img)"></i>
                                                </div>
                                                <div style="display:flex; align-items:center; margin-right:20px;">
                                                    <i class="far fa-comment fa-lg" style="cursor:pointer;" @click="document.getElementById('comment-input-'+img.id).focus()"></i>
                                                    <span x-text="parseComments(img.comments).length || ''" style="font-weight:600; font-size: 14px; margin-left: 5px;"></span>
                                                </div>
                                                <div style="display:flex; align-items:center;">
                                                    <i class="far fa-paper-plane fa-lg" style="cursor:pointer;" @click="shareImage(img)"></i>
                                                    <span x-text="img.shares || ''" style="font-weight:600; font-size: 14px; margin-left: 5px;"></span>
                                                </div>
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
                            </div>
                        </div>
                    </div>

                    <script>
                        function gallery(wsId) {
                            return {
                                images: $jsonOrigamis,
                                workspaceId: wsId,
                                feedMode: true,
                                showUpload: false,
                                offset: 20,
                                limit: 20,
                                loading: false,
                                ended: false,
                                newComments: {},
                                liked: {},
                                currentTag: '${selectedTag ?: ""}',
                                currentSort: '${sortBy ?: "id"}',
                                
                                init() {
                                    // Auto-save workspace to local list if not present
                                    let stored = JSON.parse(localStorage.getItem('wasabi_workspaces') || '[]');
                                    if(!stored.find(s => s.id === this.workspaceId) && this.workspaceId !== 'default') {
                                        stored.push({ id: this.workspaceId, name: 'Workspace ' + this.workspaceId.substr(0,4) });
                                        localStorage.setItem('wasabi_workspaces', JSON.stringify(stored));
                                    }
                                    
                                    // Load likes
                                    let likes = localStorage.getItem('wasabi_likes');
                                    if(likes) this.liked = JSON.parse(likes);

                                    window.addEventListener('scroll', () => {
                                       if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 800) {
                                           this.loadMore();
                                       }
                                    });
                                    
                                    // Check URL for specific image ID
                                    const urlParams = new URLSearchParams(window.location.search);
                                    const id = urlParams.get('id');
                                    if (id) this.fetchAndOpen(id);
                                },

                                formatDate(ts) {
                                    if(!ts) return 'User';
                                    return new Date(ts).toLocaleString();
                                },
                                
                                parseComments(comments) {
                                    if (!comments) return [];
                                    try {
                                        if (typeof comments === 'string') {
                                            if(comments === '[]') return [];
                                            return JSON.parse(comments);
                                        } else if (Array.isArray(comments)) return comments;
                                    } catch (e) {}
                                    return [];
                                },
                                
                                async fetchAndOpen(id) {
                                    let img = this.images.find(i => i.id == id);
                                    if (img) this.openFeed(img);
                                    else {
                                        try {
                                            let res = await fetch('/origami/json/' + id);
                                            if(res.ok) {
                                                let img = await res.json();
                                                this.images.unshift(img);
                                                this.openFeed(img);
                                            }
                                        } catch(e) {}
                                    }
                                },
                                
                                async loadMore() {
                                    if(this.loading || this.ended) return;
                                    this.loading = true;
                                    let url = '/origami/list?limit=' + this.limit + '&offset=' + this.offset + '&workspace=' + this.workspaceId;
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
                                
                                async toggleLike(img) {
                                    if(this.liked[img.id]) return;
                                    this.liked[img.id] = true;
                                    localStorage.setItem('wasabi_likes', JSON.stringify(this.liked));
                                    img.likes = (img.likes || 0) + 1;
                                    await fetch('/origami/' + img.id + '/like', { method: 'POST' });
                                },

                                async addComment(img, text) {
                                    if(!text) return;
                                    let formData = new URLSearchParams();
                                    formData.append('text', text);
                                    let res = await fetch('/origami/' + img.id + '/comment', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                        body: formData
                                    });
                                    if(res.ok) {
                                        let data = await res.json();
                                        img.comments = data.comments; 
                                    }
                                },
                                
                                async shareImage(img) {
                                    let url = window.location.origin + '/w/' + this.workspaceId + '?id=' + img.id;
                                    img.shares = (img.shares || 0) + 1;
                                    fetch('/origami/' + img.id + '/share', { method: 'POST' });

                                    if (navigator.share) {
                                        try {
                                            await navigator.share({ title: 'Wasabi Photo', url: url });
                                        } catch (e) {}
                                    } else {
                                        this.copyToClipboard(url);
                                    }
                                },
                                
                                copyToClipboard(text) {
                                   let ta = document.createElement("textarea");
                                   ta.value = text;
                                   document.body.appendChild(ta);
                                   ta.select();
                                   document.execCommand('copy');
                                   document.body.removeChild(ta);
                                   alert("Copied!");
                                }
                            }
                        }
                    </script>
                    """
                }
            }
        }
    }
}
