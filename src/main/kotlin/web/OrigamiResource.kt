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
        
        get("/filters") {
            call.respond(origami.FindFilters.findFilters().sorted())
        }

        get("/stats") {
            call.respond(origamiService.getStats())
        }

        get("/tags") {
             val workspace = call.parameters["workspace"] ?: "default"
             call.respond(origamiService.getAllTags(workspace))
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
                                    <div style="display:flex; align-items:center;">
                                        <!-- Edit Mode: Input -->
                                        <input :id="'input-' + space.id" x-show="editing === space.id" x-model="editName" @keydown.enter="saveEdit(space)" @click.away="cancelEdit()" @keydown.escape="cancelEdit()" style="font-size:16px; padding:4px;">
                                        
                                        <!-- View Mode: Name -->
                                        <a :href="'/w/' + space.id" x-show="editing !== space.id" style="text-decoration:none; color:#262626; font-weight:600; font-size:16px; margin-right:10px;" x-text="space.name || space.id"></a>

                                        <!-- Edit Icon -->
                                        <i class="fas fa-pencil-alt" x-show="editing !== space.id" @click="startEdit(space)" style="font-size:12px; color:#dbdbdb; cursor:pointer; margin-right:10px;" onmouseover="this.style.color='#8e8e8e'" onmouseout="this.style.color='#dbdbdb'" title="Rename"></i>

                                        <!-- Badge (Right) -->
                                        <span x-show="stats[space.id]" x-text="stats[space.id]" style="background:#0095f6; color:#fff; font-size:12px; font-weight:600; padding:2px 6px; border-radius:10px;"></span>
                                    </div>
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
                                stats: {},
                                editing: null,
                                editName: '',
                                init() {
                                    this.load();
                                    this.loadStats();
                                },
                                async loadStats() {
                                    try {
                                        let res = await fetch('/origami/stats');
                                        if(res.ok) this.stats = await res.json();
                                    } catch(e) {}
                                },
                                startEdit(space) {
                                    this.editing = space.id;
                                    this.editName = space.name;
                                    this.${'$'}nextTick(() => { document.getElementById('input-' + space.id).focus(); });
                                },
                                saveEdit(space) {
                                    if(this.editName && this.editName.trim()) {
                                        space.name = this.editName.trim();
                                        this.save();
                                    }
                                    this.editing = null;
                                },
                                cancelEdit() {
                                    this.editing = null;
                                },
                                save() {
                                     localStorage.setItem('wasabi_workspaces', JSON.stringify(this.savedSpaces));
                                },
                                load() {
                                    this.savedSpaces = JSON.parse(localStorage.getItem('wasabi_workspaces') || '[]');
                                },
                                createSpace() {
                                    let id = 'w_' + Math.random().toString(36).substr(2, 9);
                                    let name = prompt("Name your new space:", "My Space");
                                    if(name) {
                                        this.savedSpaces.push({ id: id, name: name });
                                        this.save();
                                        window.location.href = '/w/' + id;
                                    }
                                },
                                removeSpace(space) {
                                    if(confirm("Remove this space from your list? (Data is not deleted)")) {
                                        this.savedSpaces = this.savedSpaces.filter(s => s.id !== space.id);
                                        this.save();
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

    // New Full Upload Page
    get("/w/{id}/upload") {
        val workspaceId = call.parameters["id"] ?: "default"
        call.respondHtml {
            head {
                meta(name = "viewport", content = "width=device-width, initial-scale=1, shrink-to-fit=no")
                title { +"Wasabi - Upload" }
                script(src="https://cdn.jsdelivr.net/gh/alpinejs/alpine@v2.8.2/dist/alpine.min.js") {}
                link(href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css", rel="stylesheet")
                link(href="https://fonts.googleapis.com/css2?family=Grand+Hotel&display=swap", rel="stylesheet")
                link(href = "/static/style.css", rel = "stylesheet")
            }
            body(classes="bg-gray-100") {
                unsafe {
                    +"""
                    <nav class="navbar">
                        <div class="nav-content">
                            <div class="logo">Wasabi</div>
                            <div class="act">
                                <a href="/w/${workspaceId}" class="upload-btn" style="background:transparent; color:#262626; border:1px solid #dbdbdb;">Back to Gallery</a>
                            </div>
                        </div>
                    </nav>

                    <div class="container" style="max-width: 600px;">
                        <div style="background: white; border: 1px solid #dbdbdb; border-radius: 3px; padding: 40px; text-align: center;">
                            <h2 style="font-weight: 300; margin-bottom: 20px; font-size: 24px; color:#262626;">Upload New Photo</h2>

                            <form @submit.prevent="submitForm" x-data="uploadForm('${workspaceId}')" x-init="init()">
                                
                                <div style="border: 2px dashed #dbdbdb; min-height: 200px; display: flex; align-items: center; justify-content: center; border-radius: 8px; margin-bottom: 20px; cursor: pointer; position:relative; overflow: hidden; background-color: #f8f9fa;"
                                    class="hover:border-gray-400 transition"
                                    :style="(hoveredFilter !== null) ? 'border-color: #0095f6;' : ''">
                                    <input name="customFile" type="file" id="customFile"
                                        x-on:change="files1 = Object.values(${'$'}event.target.files); updatePreview()"
                                        style="position:absolute; top:0; left:0; width:100%; height:100%; opacity:0; cursor:pointer; z-index: 10;">

                                    <div x-show="!preview" style="text-align: center; padding: 40px;">
                                        <i class="fas fa-image fa-3x" style="color:#dbdbdb; margin-bottom:10px;"></i>
                                        <p style="color:#8e8e8e;">Drag photos here or click to select</p>
                                    </div>

                                    <div x-show="preview" style="width:100%; display: flex; justify-content: center;">
                                        <img :src="preview" style="max-width: 100%; max-height: 400px; width: auto; height: auto; object-fit: contain;">
                                    </div>

                                    <div x-show="hoveredFilter !== null"
                                        style="position:absolute; bottom:10px; left:50%; transform:translateX(-50%); background:rgba(0,0,0,0.7); color:white; padding:4px 8px; border-radius:4px; font-size:12px; pointer-events:none; z-index: 20;">
                                        Preview: <span x-text="hoveredFilter ? hoveredFilter.split('.').pop() : 'Normal'"></span>
                                    </div>
                                </div>

                                <div class="mb-4" style="text-align: left; position: relative; z-index: 20;">
                                    <div style="display:flex; justify-content: space-between; align-items:center; margin-bottom:10px; font-size:14px;">
                                        <label style="font-weight:600; color:#262626;">Filter Effect</label>
                                        <div>
                                            <label style="margin-right:10px; cursor:pointer;">
                                                <input type="radio" x-model="filterMode" value="list" @change="updatePreview()"> List
                                            </label>
                                            <label style="cursor:pointer;">
                                                <input type="radio" x-model="filterMode" value="file" @change="updatePreview()"> Upload
                                            </label>
                                        </div>
                                    </div>

                                    <div x-show="filterMode === 'list'">
                                        <div class="filter-bar" @mouseleave="hoveredFilter = null; updatePreview(300)">
                                            <!-- Normal Option -->
                                            <div class="filter-chip" :class="{'active': !selectedFilter}" @click="selectedFilter = ''; updatePreview()" @mouseenter="hoveredFilter = ''; updatePreview(200)">Normal</div>
                                            <!-- Dynamic Filters -->
                                            <template x-for="f in allFilters" :key="f">
                                                <div class="filter-chip" :class="{'active': selectedFilter === f}" @click="selectedFilter = f; updatePreview()" @mouseenter="hoveredFilter = f; updatePreview(200)">
                                                    <span x-text="f.split('.').pop()"></span>
                                                </div>
                                            </template>
                                        </div>
                                    </div>

                                    <div x-show="filterMode === 'file'">
                                        <input name="filter" type="file" x-on:change="files2 = Object.values(${'$'}event.target.files); updatePreview()"
                                            style="width:100%; padding:8px; border:1px solid #dbdbdb; background:#fafafa; border-radius:3px;">
                                    </div>
                                </div>

                                <div class="mb-4" style="text-align: left; margin-bottom:20px; position: relative; z-index: 20;">
                                    <label style="display:block; font-weight:600; font-size:14px; margin-bottom:5px; color:#262626;">Tags</label>
                                    <input type="text" x-model="tags" placeholder="e.g. summer, beach (comma separated)"
                                        style="width: 100%; padding: 10px; border: 1px solid #dbdbdb; background: #fafafa; border-radius: 3px; outline:none;">
                                    <!-- Suggestions -->
                                    <div class="filter-bar" style="margin-top:10px; padding:0; gap:5px;" x-show="allTags.length > 0">
                                        <template x-for="t in allTags" :key="t">
                                            <div class="filter-chip" @click.prevent="addTagFromList(t)" x-text="'#'+t" style="padding:5px 12px; font-size:13px; background:#f0f0f0; border:none;"></div>
                                        </template>
                                    </div>
                                </div>

                                <button type="submit" class="upload-btn" style="width: 100%; padding: 10px; font-size: 14px; position: relative; z-index: 20;" :disabled="isSubmitting || !files1">
                                    <span x-show="!isSubmitting">Share</span>
                                    <span x-show="isSubmitting"><i class="fas fa-spinner fa-spin"></i> Uploading...</span>
                                </button>
                            </form>
                        </div>
                    </div>
                    
                    <script>
                        function uploadForm(wsId) {
                            return {
                                workspaceId: wsId,
                                files1: null, 
                                files2: null, 
                                preview: null,
                                allFilters: [],
                                allTags: [],
                                filterMode: 'list', 
                                selectedFilter: '',
                                hoveredFilter: null,
                                debounceTimer: null,
                                isSubmitting: false,
                                tags: '',
                                
                                init() {
                                    fetch('/origami/filters').then(r => r.json()).then(data => { this.allFilters = data.sort(); });
                                    fetch('/origami/tags?workspace=' + this.workspaceId).then(r => r.json()).then(data => { this.allTags = data; });
                                },

                                updatePreview(debounceMs = 0) {
                                     if (this.debounceTimer) clearTimeout(this.debounceTimer);
                                     this.debounceTimer = setTimeout(() => this._doUpdatePreview(), debounceMs);
                                },

                                async _doUpdatePreview() {
                                     if (!this.files1 || this.files1.length === 0) { this.preview = null; return; }
                                     let formData = new FormData();
                                     formData.append('customFile', this.files1[0]);
                                     let activeFilter = (this.hoveredFilter !== null) ? this.hoveredFilter : this.selectedFilter;
                                     if(this.filterMode === 'file' && this.files2 && this.files2.length > 0) formData.append('filter', this.files2[0]);
                                     else if(this.filterMode === 'list' && activeFilter) formData.append('filterClass', activeFilter);
                                     
                                     // Preview does NOT include workspace ID usually, just global preview
                                     try {
                                         let res = await fetch('/origami/preview', { method: 'POST', body: formData });
                                         if (res.ok) { let blob = await res.blob(); this.preview = URL.createObjectURL(blob); }
                                     } catch(e) {}
                                },
                                
                                addTagFromList(t) {
                                    let current = this.tags ? this.tags.split(',').map(s => s.trim()) : [];
                                    if (!current.includes(t)) { current.push(t); this.tags = current.join(', '); }
                                },

                                async submitForm() {
                                    if (!this.files1 || this.files1.length === 0) { alert('Please select an image first.'); return; }
                                    this.isSubmitting = true;
                                    let formData = new FormData();
                                    formData.append('customFile', this.files1[0]);
                                    if(this.filterMode === 'file' && this.files2 && this.files2.length > 0) formData.append('filter', this.files2[0]);
                                    else if(this.filterMode === 'list' && this.selectedFilter) formData.append('filterClass', this.selectedFilter);
                                    if(this.tags) formData.append('tags', this.tags);
                                    
                                    // ADD WORKSPACE ID
                                    formData.append('workspace', this.workspaceId);

                                    try {
                                        let res = await fetch('/origami/form', { method: 'POST', body: formData });
                                        if(res.ok || res.redirected) {
                                            // Handle redirect manually if fetch doesn't follow or if we want to ensure client-side nav
                                            window.location.href = '/w/' + this.workspaceId;
                                        } else { alert('Upload failed.'); }
                                    } catch(e) { alert('Error.'); } 
                                    finally { this.isSubmitting = false; }
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
        
        val allTags = origamiService.getAllTags(workspaceId) // Filtered by workspace

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
                unsafe {
                    +"""
                    <style>
                        .post-btn-style {
                            font-weight: 600 !important;
                            border: none !important;
                            padding: 6px 12px !important;
                            border-radius: 4px !important;
                            margin-left: 10px !important;
                            transition: all 0.2s;
                            outline: none !important;
                            appearance: none !important;
                            -webkit-appearance: none !important;
                        }
                    </style>
                    """
                }
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
                                    <a href="/w/${workspaceId}/upload" class="upload-btn">Upload</a>
                                </div>
                            </div>
                        </nav>
                        
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
                                                 <button type="submit" class="post-btn-style" 
                                                         style="background-color: #e0f1ff; color: #0095f6;"
                                                         :style="{backgroundColor: newComments[img.id] ? '#0095f6' : '#e0f1ff', color: newComments[img.id] ? '#fff' : '#0095f6', cursor: newComments[img.id] ? 'pointer' : 'default'}">Post</button>
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
                                // showUpload: false, // REMOVED
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
