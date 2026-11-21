package com.androidbot.node.server

import android.content.Context
import com.androidbot.node.agent.AgentEngine
import com.androidbot.node.agent.AgentMissionRequest
import com.androidbot.node.storage.FeedResponse
import com.androidbot.node.storage.FileListResponse
import com.androidbot.node.storage.FolderItem
import com.androidbot.node.storage.SessionsResponse
import com.androidbot.node.storage.SimpleSuccessResponse
import com.androidbot.node.storage.StorageManager
import com.androidbot.node.storage.UploadResponse
import com.androidbot.node.util.EventLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class KtorServerManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val agentEngine: AgentEngine
) {
    private var engine: ApplicationEngine? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun start(port: Int = 8080) {
        if (_isRunning.value) return

        withContext(Dispatchers.IO) {
            try {
                EventLogger.log("SERVER", "Starting embedded Ktor CIO engine on port $port...")
                _port.value = port

                engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    configureServer()
                }.start(wait = false)

                _isRunning.value = true
                EventLogger.log("SERVER", "Ktor server active and listening on port $port.")
            } catch (e: Exception) {
                EventLogger.error("SERVER", "Failed to start server", e)
                _isRunning.value = false
                throw e
            }
        }
    }

    suspend fun stop() {
        if (!_isRunning.value) return
        withContext(Dispatchers.IO) {
            EventLogger.log("SERVER", "Stopping Ktor server...")
            try {
                engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
                engine = null
                _isRunning.value = false
                EventLogger.log("SERVER", "Ktor server stopped.")
            } catch (e: Exception) {
                EventLogger.error("SERVER", "Error stopping server", e)
            }
        }
    }

    private fun Application.configureServer() {
        install(ContentNegotiation) {
            json(json)
        }

        install(PartialContent) {
            maxRangeCount = 10
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Range)
            exposeHeader(HttpHeaders.ContentRange)
            exposeHeader(HttpHeaders.AcceptRanges)
            exposeHeader(HttpHeaders.ContentLength)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
        }

        routing {
            // REST Endpoints
            route("/api") {
                get("/files") {
                    val subfolder = call.request.queryParameters["subfolder"]
                    val files = storageManager.listFiles(subfolder)
                    val folders = files.filter { it.isDirectory }.map {
                        FolderItem(name = it.name, fileCount = 0)
                    }
                    call.respond(FileListResponse(files = files, folders = folders))
                }

                get("/files/{path...}") {
                    var rawPath = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    val isContent = rawPath.endsWith("/content")
                    if (isContent) {
                        rawPath = rawPath.removeSuffix("/content")
                    }

                    try {
                        val file = storageManager.resolveSafe(rawPath)
                        if (!file.exists()) {
                            call.respond(HttpStatusCode.NotFound, "File not found: $rawPath")
                            return@get
                        }
                        if (isContent) {
                            call.respondText(storageManager.readFile(rawPath), ContentType.Text.Plain)
                        } else {
                            call.respondFile(file)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                    }
                }

                get("/stream/{path...}") {
                    val relativePath = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    if (relativePath.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Missing file path")
                        return@get
                    }

                    try {
                        val file = storageManager.resolveSafe(relativePath)
                        if (!file.exists()) {
                            call.respond(HttpStatusCode.NotFound, "File not found: $relativePath")
                            return@get
                        }
                        if (file.isDirectory) {
                            call.respond(HttpStatusCode.BadRequest, "Cannot stream directory: $relativePath")
                            return@get
                        }

                        // Ktor handles RFC 7233 Range headers (HTTP 206 Partial Content) automatically
                        call.respondFile(file)
                    } catch (e: SecurityException) {
                        call.respond(HttpStatusCode.Forbidden, e.message ?: "Security violation")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Stream error: ${e.message}")
                    }
                }

                post("/upload") {
                    try {
                        val multipart = call.receiveMultipart()
                        var uploadedCount = 0
                        var lastFileName = ""

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val originalName = part.originalFileName ?: "uploaded_file_${System.currentTimeMillis()}"
                                val safeFile = storageManager.resolveSafe(originalName)
                                part.streamProvider().use { input ->
                                    safeFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                uploadedCount++
                                lastFileName = originalName
                                val fileLen = safeFile.length()
                                EventLogger.log("STORAGE", "Uploaded: '$originalName' ($fileLen bytes)")
                                com.androidbot.node.storage.TransferTracker.record(
                                    com.androidbot.node.storage.TransferEvent(
                                        fileName = originalName,
                                        sizeBytes = fileLen,
                                        origin = com.androidbot.node.storage.TransferOrigin.WEB_UPLOAD
                                    )
                                )
                            }
                            part.dispose()
                        }
                        call.respond(
                            UploadResponse(
                                success = true,
                                message = "Successfully uploaded $uploadedCount file(s).",
                                lastFile = lastFileName
                            )
                        )
                    } catch (e: Exception) {
                        EventLogger.error("STORAGE", "Upload failed: ${e.message}", e)
                        call.respond(HttpStatusCode.InternalServerError, UploadResponse(success = false, message = e.message ?: "Upload failed"))
                    }
                }

                post("/agent/run") {
                    try {
                        val bodyText = call.receiveText().trim().removePrefix("\uFEFF")
                        EventLogger.log("AGENT", "Received /api/agent/run request: $bodyText")
                        val request = if (bodyText.isNotBlank()) {
                            json.decodeFromString<AgentMissionRequest>(bodyText)
                        } else {
                            AgentMissionRequest(prompt = "")
                        }
                        val result = agentEngine.executeMission(request.prompt)
                        call.respond(result)
                    } catch (e: Exception) {
                        EventLogger.error("AGENT", "Agent invocation error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            SimpleSuccessResponse(success = false, message = e.message ?: "Failed to execute mission")
                        )
                    }
                }

                get("/agent/sessions") {
                    call.respond(SessionsResponse(sessions = emptyList()))
                }

                get("/agent/sessions/{id}") {
                    call.respond(HttpStatusCode.NotFound, SimpleSuccessResponse(success = false, message = "Session not found"))
                }

                post("/agent/history/clear") {
                    call.respond(SimpleSuccessResponse(success = true))
                }

                get("/feed") {
                    call.respond(FeedResponse(feed = emptyList()))
                }
            }

            // Web Portal Serving
            get("/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                serveStaticOrFallback(call, path)
            }
        }
    }

    private suspend fun serveStaticOrFallback(call: ApplicationCall, requestPath: String) {
        val targetPath = if (requestPath.isBlank() || requestPath == "/") "index.html" else requestPath
        val assetPath = "web/$targetPath"

        try {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.readBytes()
                val contentType = resolveContentType(targetPath)
                call.respondBytes(bytes, contentType)
            }
        } catch (_: Exception) {
            // Try fallback to index.html if SPA route
            try {
                context.assets.open("web/index.html").use { stream ->
                    val bytes = stream.readBytes()
                    call.respondBytes(bytes, ContentType.Text.Html)
                }
            } catch (_: Exception) {
                // If assets/web is not yet bundled, serve built-in fallback portal
                call.respondText(getBuiltInPortalHtml(), ContentType.Text.Html)
            }
        }
    }

    private fun resolveContentType(path: String): ContentType {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> ContentType.Text.Html
            "css" -> ContentType.Text.CSS
            "js" -> ContentType.Application.JavaScript
            "json" -> ContentType.Application.Json
            "png" -> ContentType.Image.PNG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "svg" -> ContentType.Image.SVG
            else -> ContentType.Application.OctetStream
        }
    }

    private fun getBuiltInPortalHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AndroidBot Edge Appliance</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
                    .card { background: #1e293b; border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.2); }
                    h1 { color: #38bdf8; margin-top: 0; }
                    h2 { color: #94a3b8; font-size: 1.1rem; border-bottom: 1px solid #334155; padding-bottom: 8px; }
                    button { background: #0284c7; color: white; border: none; padding: 10px 18px; border-radius: 6px; cursor: pointer; font-weight: 600; }
                    button:hover { background: #0369a1; }
                    input, textarea { width: 100%; box-sizing: border-box; background: #0f172a; color: white; border: 1px solid #334155; border-radius: 6px; padding: 10px; margin: 8px 0; font-family: monospace; }
                    ul { list-style: none; padding: 0; }
                    li { padding: 10px; border-bottom: 1px solid #334155; display: flex; justify-content: space-between; align-items: center; }
                    video { width: 100%; max-height: 400px; background: black; border-radius: 8px; margin-top: 12px; }
                    pre { background: #0f172a; padding: 12px; border-radius: 6px; overflow-x: auto; color: #a5f3fc; }
                </style>
            </head>
            <body>
                <h1>AndroidBot Node Portal</h1>
                <div class="card">
                    <h2>📁 Storage Repository (/api/files)</h2>
                    <button onclick="loadFiles()">Refresh Files</button>
                    <ul id="fileList">Loading files...</ul>
                </div>

                <div class="card">
                    <h2>🎬 RFC 7233 Media Streamer (HTTP 206)</h2>
                    <div id="videoContainer">Select a media file from the list to stream with instant seek.</div>
                </div>

                <div class="card">
                    <h2>🤖 Onboard Autonomous Agent (/api/agent/run)</h2>
                    <textarea id="agentPrompt" rows="3" placeholder="e.g. Read meeting_notes.txt and write an executive summary to summary.md"></textarea>
                    <button id="agentBtn" onclick="runAgent()">Execute Mission</button>
                    <div id="agentStatus" style="margin-top: 12px;"></div>
                    <pre id="agentOutput" style="display:none;"></pre>
                </div>

                <script>
                    async function loadFiles() {
                        const res = await fetch('/api/files');
                        const files = await res.json();
                        const list = document.getElementById('fileList');
                        list.innerHTML = '';
                        files.forEach(f => {
                            const li = document.createElement('li');
                            li.innerHTML = `<span><strong>${'$'}{f.isDirectory ? '[DIR] ' : ''}${'$'}{f.relativePath}</strong> (${'$'}{f.sizeBytes} bytes)</span>`;
                            if (!f.isDirectory && (f.mimeType.startsWith('video/') || f.mimeType.startsWith('audio/') || f.name.endsWith('.mp4'))) {
                                const btn = document.createElement('button');
                                btn.textContent = 'Stream (206)';
                                btn.onclick = () => playStream(f.relativePath);
                                li.appendChild(btn);
                            }
                            list.appendChild(li);
                        });
                    }

                    function playStream(path) {
                        const container = document.getElementById('videoContainer');
                        const url = '/api/stream/' + encodeURI(path);
                        container.innerHTML = `<video controls autoplay src="${'$'}{url}"></video><p>Streaming: <code>${'$'}{url}</code></p>`;
                    }

                    async function runAgent() {
                        const prompt = document.getElementById('agentPrompt').value.trim();
                        if (!prompt) return;
                        const btn = document.getElementById('agentBtn');
                        const status = document.getElementById('agentStatus');
                        const out = document.getElementById('agentOutput');
                        btn.disabled = true;
                        status.textContent = 'Running ReAct loop on device...';
                        out.style.display = 'none';

                        try {
                            const res = await fetch('/api/agent/run', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ prompt: prompt })
                            });
                            const data = await res.json();
                            status.textContent = 'Status: ' + (data.success ? 'Success (' + data.turnsUsed + ' turns)' : 'Failed');
                            out.style.display = 'block';
                            out.textContent = data.finalAnswer + '\n\n--- Execution Trace ---\n' + (data.executionLog || []).join('\n');
                            loadFiles();
                        } catch (err) {
                            status.textContent = 'Error: ' + err.message;
                        } finally {
                            btn.disabled = false;
                        }
                    }

                    loadFiles();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
