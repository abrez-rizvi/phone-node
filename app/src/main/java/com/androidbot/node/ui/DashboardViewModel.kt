package com.androidbot.node.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidbot.node.AndroidBotApp
import com.androidbot.node.agent.AgentMissionResponse
import com.androidbot.node.agent.AgentStep
import com.androidbot.node.service.NodeService
import com.androidbot.node.storage.FileItem
import com.androidbot.node.storage.StorageUsage
import com.androidbot.node.storage.TransferEvent
import com.androidbot.node.storage.TransferTracker
import com.androidbot.node.util.EventLogger
import com.androidbot.node.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AlbumFilter(val title: String) {
    ALL("All Media"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    AGENT_OUTPUTS("Agent Briefs"),
    IMAGES("Photos")
}

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val steps: List<AgentStep> = emptyList(),
    val durationSec: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class DashboardViewModel : ViewModel() {

    private val storageManager get() = AndroidBotApp.instance.storageManager
    private val agentEngine get() = AndroidBotApp.instance.agentEngine

    val isServerRunning: StateFlow<Boolean> = AndroidBotApp.instance.ktorServerManager.isRunning
    val serverPort: StateFlow<Int> = AndroidBotApp.instance.ktorServerManager.port

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _apiKey = MutableStateFlow(AndroidBotApp.instance.getGroqApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val storagePath: String = storageManager.rootDir.absolutePath

    // Media & Storage State
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _storageUsage = MutableStateFlow(
        StorageUsage(totalNodeBytes = 0, fileCount = 0, freeBytes = 0, totalDeviceBytes = 0)
    )
    val storageUsage: StateFlow<StorageUsage> = _storageUsage.asStateFlow()

    val transfers: StateFlow<List<TransferEvent>> = TransferTracker.transfers

    // Album Filter
    private val _currentFilter = MutableStateFlow(AlbumFilter.ALL)
    val currentFilter: StateFlow<AlbumFilter> = _currentFilter.asStateFlow()

    // Active Fullscreen Media Viewer State
    private val _selectedFile = MutableStateFlow<FileItem?>(null)
    val selectedFile: StateFlow<FileItem?> = _selectedFile.asStateFlow()

    private val _previewText = MutableStateFlow<String?>(null)
    val previewText: StateFlow<String?> = _previewText.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // Conversational Agent Chat State
    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageItem>>(
        listOf(
            ChatMessageItem(
                isUser = false,
                text = "👋 Welcome to ZeroClaw Edge Agent.\nI have direct access to your phone's sovereign storage. Ask me to inspect files, summarize energy audits, generate executive reports, or audit vault integrity."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessageItem>> = _chatMessages.asStateFlow()

    init {
        refreshIp()
        refreshFiles()

        viewModelScope.launch {
            EventLogger.logs.collect { newLog ->
                _logs.value = (_logs.value + newLog).takeLast(100)
            }
        }
    }

    fun setFilter(filter: AlbumFilter) {
        _currentFilter.value = filter
    }

    fun refreshIp() {
        _localIp.value = NetworkUtils.getLocalIpAddress()
    }

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
        AndroidBotApp.instance.setGroqApiKey(newKey)
        EventLogger.log("UI", "Groq API key updated.")
    }

    fun toggleServer() {
        val app = AndroidBotApp.instance
        if (isServerRunning.value) {
            NodeService.stopService(app)
        } else {
            refreshIp()
            NodeService.startService(app)
        }
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = storageManager.listFiles()
            val usage = storageManager.getStorageUsage()
            _files.value = list
            _storageUsage.value = usage
        }
    }

    fun importFile(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = storageManager.importFileFromUri(uri, contentResolver)
            if (item != null) {
                refreshFiles()
            }
        }
    }

    fun deleteFile(relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (storageManager.deleteFile(relativePath)) {
                if (_selectedFile.value?.relativePath == relativePath) {
                    closePreview()
                }
                refreshFiles()
            }
        }
    }

    fun getResolvedFile(relativePath: String): File {
        return storageManager.resolveSafe(relativePath)
    }

    fun previewFile(file: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedFile.value = file
            _previewText.value = null
            _previewBitmap.value = null

            try {
                if (file.isText) {
                    val content = storageManager.readFile(file.relativePath)
                    _previewText.value = content
                } else if (file.isImage) {
                    val resolved = storageManager.resolveSafe(file.relativePath)
                    val bmp = BitmapFactory.decodeFile(resolved.absolutePath)
                    _previewBitmap.value = bmp
                }
            } catch (e: Exception) {
                _previewText.value = "Error reading file content: ${e.message}"
            }
        }
    }

    fun closePreview() {
        _selectedFile.value = null
        _previewText.value = null
        _previewBitmap.value = null
    }

    fun sendChatMessage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isAgentRunning.value) return

        val userMsg = ChatMessageItem(isUser = true, text = clean)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAgentRunning.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = agentEngine.executeMission(clean)
                val assistantMsg = ChatMessageItem(
                    isUser = false,
                    text = res.finalAnswer,
                    steps = res.steps,
                    durationSec = res.telemetry.durationSeconds
                )
                _chatMessages.value = _chatMessages.value + assistantMsg
                refreshFiles()
            } catch (e: Exception) {
                val errorMsg = ChatMessageItem(
                    isUser = false,
                    text = "⚠️ Execution error: ${e.message}"
                )
                _chatMessages.value = _chatMessages.value + errorMsg
            } finally {
                _isAgentRunning.value = false
            }
        }
    }
}
