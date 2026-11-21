package com.androidbot.node.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class TransferOrigin {
    WEB_UPLOAD,
    DEVICE_IMPORT,
    AGENT_OUTPUT
}

@Serializable
data class TransferEvent(
    val fileName: String,
    val sizeBytes: Long,
    val origin: TransferOrigin,
    val timestamp: Long = System.currentTimeMillis()
)

object TransferTracker {
    private val _transfers = MutableStateFlow<List<TransferEvent>>(emptyList())
    val transfers: StateFlow<List<TransferEvent>> = _transfers.asStateFlow()

    fun record(event: TransferEvent) {
        _transfers.value = (listOf(event) + _transfers.value).take(50)
    }
}
