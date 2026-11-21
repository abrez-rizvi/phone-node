package com.androidbot.node

import com.androidbot.node.agent.ChatCompletionRequest
import com.androidbot.node.agent.ChatMessage
import com.androidbot.node.agent.GroqClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testToolDefinitionsSerialization() {
        val tools = GroqClient.agentTools
        val request = ChatCompletionRequest(
            messages = listOf(ChatMessage(role = "user", content = "List my files")),
            tools = tools
        )

        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("list_files"))
        assertTrue(serialized.contains("read_file"))
        assertTrue(serialized.contains("write_file"))
        assertTrue(serialized.contains("search_content"))
        assertTrue(serialized.contains("llama-3.3-70b-versatile"))
    }
}
