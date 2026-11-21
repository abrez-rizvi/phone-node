package com.androidbot.node.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatCompletionRequest(
    val model: String = "openai/gpt-oss-120b",
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double = 0.2
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class GroqError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val error: GroqError? = null
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class AgentMissionRequest(
    val prompt: String = "",
    @SerialName("session_id") val sessionId: String? = null
)

@Serializable
data class AgentStep(
    val tool: String,
    val args: Map<String, String> = emptyMap()
)

@Serializable
data class AgentTelemetry(
    val engine: String = "ZeroClaw",
    val model: String = "GPT-OSS 120B",
    @SerialName("duration_seconds") val durationSeconds: String = "1.2",
    @SerialName("tokens_per_sec") val tokensPerSec: String = "380"
)

@Serializable
data class AgentMissionResponse(
    val success: Boolean,
    val turnsUsed: Int,
    val finalAnswer: String = "",
    @SerialName("final_answer") val final_answer: String = "",
    val executionLog: List<String> = emptyList(),
    val steps: List<AgentStep> = emptyList(),
    val telemetry: AgentTelemetry = AgentTelemetry()
)
