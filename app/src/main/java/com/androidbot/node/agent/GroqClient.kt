package com.androidbot.node.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class GroqClient(private val apiKeyProvider: () -> String) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 45000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 45000
        }
    }

    suspend fun chatCompletion(messages: List<ChatMessage>): ChatCompletionResponse {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Groq API key is not configured. Please enter your API key in the app.")
        }

        val request = ChatCompletionRequest(
            model = "openai/gpt-oss-120b",
            messages = messages,
            tools = agentTools,
            toolChoice = "auto",
            temperature = 0.2
        )

        val httpResponse = httpClient.post("https://api.groq.com/openai/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseBody = httpResponse.body<String>()
        val result = try {
            json.decodeFromString<ChatCompletionResponse>(responseBody)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse Groq response: $responseBody", e)
        }

        if (result.error != null) {
            throw IllegalStateException("Groq API returned error: ${result.error.message ?: "Unknown error"}")
        }
        if (result.choices.isEmpty()) {
            throw IllegalStateException("Groq API returned no choices. Raw response: $responseBody")
        }

        return result
    }

    companion object {
        val agentTools = listOf(
            ToolDefinition(
                function = FunctionDefinition(
                    name = "list_files",
                    description = "Lists files and subdirectories in the scoped storage workspace.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("subfolder") {
                                put("type", "string")
                                put("description", "Optional subfolder relative to workspace root (e.g. 'docs'). Leave blank for root.")
                            }
                        }
                    }
                )
            ),
            ToolDefinition(
                function = FunctionDefinition(
                    name = "read_file",
                    description = "Reads the UTF-8 text contents of a file in the workspace.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("filename") {
                                put("type", "string")
                                put("description", "Path to the file relative to the storage workspace root.")
                            }
                        }
                        put("required", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("filename"))
                        })
                    }
                )
            ),
            ToolDefinition(
                function = FunctionDefinition(
                    name = "write_file",
                    description = "Writes content to a UTF-8 file in the workspace. Overwrites if exists.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("filename") {
                                put("type", "string")
                                put("description", "Path to the file to create or overwrite relative to root.")
                            }
                            putJsonObject("content") {
                                put("type", "string")
                                put("description", "The complete text content to write.")
                            }
                        }
                        put("required", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("filename"))
                            add(kotlinx.serialization.json.JsonPrimitive("content"))
                        })
                    }
                )
            ),
            ToolDefinition(
                function = FunctionDefinition(
                    name = "search_content",
                    description = "Performs keyword search across all text files in the workspace.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("query") {
                                put("type", "string")
                                put("description", "Keyword or phrase to search for.")
                            }
                        }
                        put("required", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("query"))
                        })
                    }
                )
            )
        )
    }
}
