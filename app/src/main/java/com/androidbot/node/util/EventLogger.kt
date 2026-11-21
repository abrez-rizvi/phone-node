package com.androidbot.node.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventLogger {

    private val _logs = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 50)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val formatted = "[$timestamp] [$tag] $message"
        _logs.tryEmit(formatted)
        android.util.Log.i("AndroidBot-$tag", message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val detail = if (throwable != null) " - ${throwable.message}" else ""
        val formatted = "[$timestamp] [ERROR/$tag] $message$detail"
        _logs.tryEmit(formatted)
        android.util.Log.e("AndroidBot-$tag", "$message$detail", throwable)
    }
}
