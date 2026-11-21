package com.androidbot.node.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileItem(
    val name: String,
    @SerialName("rel_path") val relPath: String,
    val relativePath: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("is_directory") val isDirectory: Boolean,
    @SerialName("is_video") val isVideo: Boolean = false,
    @SerialName("is_image") val isImage: Boolean = false,
    @SerialName("is_text") val isText: Boolean = false,
    @SerialName("last_modified") val lastModified: Long,
    @SerialName("mime_type") val mimeType: String
)

@Serializable
data class FolderItem(
    val name: String,
    @SerialName("file_count") val fileCount: Int = 0
)

@Serializable
data class FileListResponse(
    val files: List<FileItem>,
    val folders: List<FolderItem> = emptyList()
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val lastFile: String? = null
)

@Serializable
data class SimpleSuccessResponse(
    val success: Boolean = true,
    val message: String? = null
)

@Serializable
data class SessionsResponse(
    val sessions: List<String> = emptyList()
)

@Serializable
data class FeedResponse(
    val feed: List<String> = emptyList()
)
