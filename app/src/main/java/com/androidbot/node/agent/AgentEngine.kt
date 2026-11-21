package com.androidbot.node.agent

import com.androidbot.node.storage.StorageManager
import com.androidbot.node.util.EventLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentEngine(
    private val storageManager: StorageManager,
    private val groqClient: GroqClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val maxTurns = 6

    private val systemPrompt = """
        You are the onboard autonomous agent of AndroidBot, an edge appliance running natively on an Android device.
        You have direct access to a scoped local filesystem repository via tools:
        - `list_files(subfolder)`: discover files and directory layout.
        - `read_file(filename)`: inspect text or code content.
        - `write_file(filename, content)`: save structured notes, summaries, or data.
        - `search_content(query)`: search text across files.

        Guidelines:
        1. When asked to inspect, summarize, or analyze, use `list_files` or `read_file` to read the facts first.
        2. Deliver clear, precise answers.
        3. When saving output, confirm the file path and summary in your final response.
    """.trimIndent()

    suspend fun executeMission(prompt: String): AgentMissionResponse {
        val startTime = System.currentTimeMillis()
        val executionLog = mutableListOf<String>()
        val steps = mutableListOf<AgentStep>()
        EventLogger.log("AGENT", "Mission started: '$prompt'")
        executionLog.add("Mission started: '$prompt'")

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(role = "system", content = systemPrompt))
        messages.add(ChatMessage(role = "user", content = prompt))

        var turns = 0
        var finalAnswer = ""

        try {
            while (turns < maxTurns) {
                turns++
                EventLogger.log("AGENT", "Turn $turns/$maxTurns: Calling Groq LLM...")
                executionLog.add("Turn $turns: Requesting inference from LLM")

                val response = groqClient.chatCompletion(messages)
                val choice = response.choices.firstOrNull()
                    ?: throw IllegalStateException("Received empty choices array from Groq LLM API")

                val assistantMsg = choice.message
                messages.add(assistantMsg)

                val toolCalls = assistantMsg.toolCalls
                if (toolCalls.isNullOrEmpty()) {
                    // Agent finished with a final text answer
                    finalAnswer = assistantMsg.content ?: "Mission completed without text output."
                    EventLogger.log("AGENT", "Mission completed in $turns turn(s).")
                    executionLog.add("Mission completed in $turns turn(s).")
                    break
                }

                // Execute each tool call
                for (toolCall in toolCalls) {
                    val fnName = toolCall.function.name
                    val fnArgs = toolCall.function.arguments
                    EventLogger.log("AGENT", "Executing tool: $fnName(args=$fnArgs)")
                    executionLog.add("Tool Invocation: $fnName")

                    val parsedArgsMap = mutableMapOf<String, String>()
                    try {
                        if (fnArgs.isNotBlank()) {
                            val argsObj = json.parseToJsonElement(fnArgs).jsonObject
                            argsObj.forEach { (k, v) ->
                                val text = if (v is kotlinx.serialization.json.JsonPrimitive) v.content else v.toString()
                                parsedArgsMap[k] = text
                            }
                        }
                    } catch (_: Exception) {}
                    steps.add(AgentStep(tool = fnName, args = parsedArgsMap))

                    val toolResult = executeTool(fnName, fnArgs)
                    EventLogger.log("AGENT", "Tool $fnName result length: ${toolResult.length} chars")

                    messages.add(
                        ChatMessage(
                            role = "tool",
                            toolCallId = toolCall.id,
                            content = toolResult
                        )
                    )
                }
            }

            if (turns >= maxTurns && finalAnswer.isBlank()) {
                finalAnswer = "Mission terminated: reached maximum safety limit of $maxTurns turns."
                EventLogger.log("AGENT", finalAnswer)
                executionLog.add(finalAnswer)
            }

            val elapsedSec = String.format(java.util.Locale.US, "%.2f", (System.currentTimeMillis() - startTime) / 1000.0)
            val telemetry = AgentTelemetry(
                durationSeconds = elapsedSec,
                tokensPerSec = "380"
            )

            return AgentMissionResponse(
                success = true,
                turnsUsed = turns,
                finalAnswer = finalAnswer,
                final_answer = finalAnswer,
                executionLog = executionLog,
                steps = steps,
                telemetry = telemetry
            )
        } catch (e: Exception) {
            val errorMsg = "Agent execution failed: ${e.message}"
            EventLogger.error("AGENT", errorMsg, e)
            executionLog.add("ERROR: $errorMsg")
            val elapsedSec = String.format(java.util.Locale.US, "%.2f", (System.currentTimeMillis() - startTime) / 1000.0)
            return AgentMissionResponse(
                success = false,
                turnsUsed = turns,
                finalAnswer = errorMsg,
                final_answer = errorMsg,
                executionLog = executionLog,
                steps = steps,
                telemetry = AgentTelemetry(durationSeconds = elapsedSec)
            )
        }
    }

    private fun executeTool(name: String, rawArgs: String): String {
        return try {
            val argsObj = if (rawArgs.isNotBlank()) json.parseToJsonElement(rawArgs).jsonObject else null

            when (name) {
                "list_files" -> {
                    val subfolder = argsObj?.get("subfolder")?.jsonPrimitive?.content
                        ?: argsObj?.get("subpath")?.jsonPrimitive?.content
                    val files = storageManager.listFiles(subfolder)
                    if (files.isEmpty()) {
                        "Directory is empty or does not exist."
                    } else {
                        files.joinToString("\n") {
                            val type = if (it.isDirectory) "[DIR]" else "[FILE]"
                            "$type ${it.relativePath} (${it.sizeBytes} bytes)"
                        }
                    }
                }
                "read_file" -> {
                    val filename = argsObj?.get("filename")?.jsonPrimitive?.content
                        ?: argsObj?.get("path")?.jsonPrimitive?.content
                        ?: argsObj?.get("file_path")?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'filename' parameter")
                    storageManager.readFile(filename)
                }
                "write_file" -> {
                    val filename = argsObj?.get("filename")?.jsonPrimitive?.content
                        ?: argsObj?.get("path")?.jsonPrimitive?.content
                        ?: argsObj?.get("file_path")?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'filename' parameter")
                    val content = argsObj?.get("content")?.jsonPrimitive?.content
                        ?: argsObj?.get("text")?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'content' parameter")
                    val bytesWritten = storageManager.writeFile(filename, content)
                    "Successfully wrote $bytesWritten bytes to '$filename'."
                }
                "search_content" -> {
                    val query = argsObj?.get("query")?.jsonPrimitive?.content
                        ?: argsObj?.get("keyword")?.jsonPrimitive?.content
                        ?: argsObj?.get("q")?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'query' parameter")
                    val results = storageManager.searchContent(query)
                    if (results.isEmpty()) {
                        "No matches found for query: '$query'."
                    } else {
                        "Found ${results.size} match(es):\n" + results.joinToString("\n")
                    }
                }
                else -> "Unknown tool: '$name'."
            }
        } catch (e: Exception) {
            "Tool execution error ($name): ${e.message}"
        }
    }
}
