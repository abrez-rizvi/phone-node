package com.androidbot.node.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.androidbot.node.util.EventLogger
import java.io.File
import java.io.IOException

data class StorageUsage(
    val totalNodeBytes: Long,
    val fileCount: Int,
    val freeBytes: Long,
    val totalDeviceBytes: Long
)

class StorageManager(private val context: Context) {

    val rootDir: File by lazy {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val mediaDir = File(dir, "Media")
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        mediaDir
    }

    init {
        seedSampleData()
    }

    fun resolveSafe(relativePath: String): File {
        val sanitized = relativePath.trim().removePrefix("/").removePrefix("\\")
        if (sanitized.contains("..")) {
            throw SecurityException("Path traversal attempt detected: '$relativePath'")
        }
        val target = File(rootDir, sanitized).canonicalFile
        if (!target.path.startsWith(rootDir.canonicalPath)) {
            throw SecurityException("Access outside storage sandbox denied: '$relativePath'")
        }
        return target
    }

    fun listFiles(subfolder: String? = null): List<FileItem> {
        val targetDir = if (subfolder.isNullOrBlank()) rootDir else resolveSafe(subfolder)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return emptyList()
        }

        val results = mutableListOf<FileItem>()
        targetDir.walkTopDown()
            .maxDepth(4)
            .filter { it != rootDir }
            .forEach { file ->
                val relative = file.toRelativeString(rootDir).replace('\\', '/')
                val isDir = file.isDirectory
                val size = if (isDir) 0L else file.length()
                val mime = if (isDir) "inode/directory" else getMimeType(file)
                val ext = file.extension.lowercase()
                val isVid = !isDir && (mime.startsWith("video/") || ext in listOf("mp4", "webm", "mkv", "mov", "avi"))
                val isImg = !isDir && (mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg"))
                val isTxt = !isDir && (mime.startsWith("text/") || ext in listOf("txt", "md", "json", "csv", "log", "yaml", "yml"))
                results.add(
                    FileItem(
                        name = file.name,
                        relPath = relative,
                        relativePath = relative,
                        sizeBytes = size,
                        isDirectory = isDir,
                        isVideo = isVid,
                        isImage = isImg,
                        isText = isTxt,
                        lastModified = file.lastModified(),
                        mimeType = mime
                    )
                )
            }
        return results
    }

    fun readFile(relativePath: String): String {
        val file = resolveSafe(relativePath)
        if (!file.exists()) {
            throw IOException("File not found: $relativePath")
        }
        if (file.isDirectory) {
            throw IOException("Target is a directory, cannot read: $relativePath")
        }
        val maxSizeBytes = 5 * 1024 * 1024 // 5 MB ceiling
        if (file.length() > maxSizeBytes) {
            throw IOException("File size (${file.length()} bytes) exceeds 5MB safety limit for agent reading")
        }
        return file.readText(Charsets.UTF_8)
    }

    fun writeFile(relativePath: String, content: String): Long {
        val file = resolveSafe(relativePath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content, Charsets.UTF_8)
        val len = file.length()
        TransferTracker.record(TransferEvent(file.name, len, TransferOrigin.AGENT_OUTPUT))
        return len
    }

    fun deleteFile(relativePath: String): Boolean {
        val file = resolveSafe(relativePath)
        if (!file.exists()) return false
        val deleted = file.deleteRecursively()
        if (deleted) {
            EventLogger.log("STORAGE", "Deleted: '$relativePath'")
        }
        return deleted
    }

    fun importFileFromUri(uri: Uri, contentResolver: ContentResolver): FileItem? {
        var displayName: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}

        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment ?: "imported_${System.currentTimeMillis()}"
        }

        val safeName = File(displayName!!).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        var targetFile = File(rootDir, safeName)
        if (targetFile.exists()) {
            val base = safeName.substringBeforeLast('.')
            val ext = if (safeName.contains('.')) "." + safeName.substringAfterLast('.') else ""
            targetFile = File(rootDir, "${base}_${System.currentTimeMillis() % 10000}$ext")
        }

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
        } catch (e: Exception) {
            EventLogger.error("STORAGE", "Failed to import file: ${e.message}", e)
            return null
        }

        val len = targetFile.length()
        EventLogger.log("STORAGE", "Imported: '${targetFile.name}' ($len bytes) from device")
        TransferTracker.record(TransferEvent(targetFile.name, len, TransferOrigin.DEVICE_IMPORT))

        val relative = targetFile.toRelativeString(rootDir).replace('\\', '/')
        val mime = getMimeType(targetFile)
        val ext = targetFile.extension.lowercase()
        return FileItem(
            name = targetFile.name,
            relPath = relative,
            relativePath = relative,
            sizeBytes = len,
            isDirectory = false,
            isVideo = mime.startsWith("video/") || ext in listOf("mp4", "webm", "mkv", "mov", "avi"),
            isImage = mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg"),
            isText = mime.startsWith("text/") || ext in listOf("txt", "md", "json", "csv", "log", "yaml", "yml"),
            lastModified = targetFile.lastModified(),
            mimeType = mime
        )
    }

    fun getStorageUsage(): StorageUsage {
        var totalBytes = 0L
        var count = 0
        rootDir.walkTopDown().filter { it.isFile }.forEach {
            totalBytes += it.length()
            count++
        }
        return StorageUsage(
            totalNodeBytes = totalBytes,
            fileCount = count,
            freeBytes = rootDir.freeSpace,
            totalDeviceBytes = rootDir.totalSpace
        )
    }

    fun searchContent(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<String>()
        val maxMatches = 50

        rootDir.walkTopDown()
            .maxDepth(4)
            .filter { it.isFile && it.length() <= 2 * 1024 * 1024 } // limit search to files <= 2MB
            .forEach { file ->
                if (matches.size >= maxMatches) return@forEach
                val mime = getMimeType(file)
                // Only inspect text or code files
                if (mime.startsWith("text/") || file.extension in listOf("txt", "md", "json", "csv", "log", "kt", "js", "html")) {
                    try {
                        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                if (matches.size >= maxMatches) return@useLines
                                if (line.contains(query, ignoreCase = true)) {
                                    val rel = file.toRelativeString(rootDir).replace('\\', '/')
                                    matches.add("$rel:${index + 1}: ${line.trim()}")
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip unreadable files
                    }
                }
            }
        return matches
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun seedSampleData() {
        val welcome = File(rootDir, "welcome.txt")
        if (!welcome.exists()) {
            welcome.writeText(
                "Welcome to AndroidBot Edge Appliance.\n" +
                "Storage root initialized successfully.\n" +
                "Use the web interface or ReAct agent to inspect and manage files."
            )
        }
        val sampleMeeting = File(rootDir, "meeting_notes.txt")
        if (!sampleMeeting.exists()) {
            sampleMeeting.writeText(
                "Project Alpha Status Update\n" +
                "Date: 2026-11-20\n" +
                "Attendees: Alex, Sarah, David\n\n" +
                "1. Edge appliance prototype: Core Ktor server functional.\n" +
                "2. HTTP 206 streaming: Verified instant seek on media files.\n" +
                "3. Agent: Groq LLM integration with function calling complete.\n" +
                "Action item: Present live demonstration in upcoming viva defense."
            )
        }
        val sampleReport = File(rootDir, "quarterly_report.txt")
        if (!sampleReport.exists()) {
            sampleReport.writeText(
                "Q3 2026 Sovereign Edge Infrastructure Operations Report\n\n" +
                "1. Executive Summary:\n" +
                "During Q3 2026, the edge computing fleet transitioned from legacy high-idle x86 appliances to ARM-based sovereign Android nodes (AndroidBot). The transition reduced overall facility energy consumption by 84% while maintaining 99.98% local service availability.\n\n" +
                "2. Infrastructure Milestones:\n" +
                "- 120 edge nodes deployed across regional hubs.\n" +
                "- Over 4.2 TB of local media and document assets served via HTTP 206 chunked streaming.\n" +
                "- Average read latency under 12ms for local area network clients.\n\n" +
                "3. Autonomous Operations:\n" +
                "- Onboard ReAct agent completed 1,420 automated storage audits and telemetry reconciliations without manual intervention.\n" +
                "- Zero data exfiltration: all analytical summaries generated strictly via scoped filesystem tools.\n\n" +
                "4. Financial & Power Metrics:\n" +
                "- Total electricity savings: $14,200 annualized.\n" +
                "- Hardware acquisition cost reduced by 65% by repurposing existing mobile hardware.\n\n" +
                "5. Key Recommendations for Q4:\n" +
                "- Scale node deployment to remaining satellite offices.\n" +
                "- Upgrade local storage pools to high-endurance UFS 4.0 storage."
            )
        }
    }
}
