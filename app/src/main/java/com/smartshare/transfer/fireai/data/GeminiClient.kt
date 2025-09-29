package com.smartshare.transfer.fireai.data

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GeminiClient(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
) {

    /**
     * Creates a well-structured prompt for interview-style Q&A
     */
    private fun createInterviewPrompt(question: String): String {
        return """
You are an expert technical interviewer providing direct, clear answers.

Question: $question

IMPORTANT RULES:
- Give a direct, complete answer - do NOT ask clarifying questions
- Assume the question is asking for a technical explanation or definition
- Write ONLY in plain text with NO special formatting
- NO markdown symbols: no ##, **, *, `, _, [], or code blocks
- NO bullet points or numbered lists - write in paragraphs only
- Separate paragraphs with blank lines
- Be comprehensive but concise
- Use natural language throughout

Provide a professional answer that directly addresses the question with relevant technical details.
        """.trimIndent()
    }

    /**
     * Filters and cleans the AI response text - removes ALL markdown formatting
     */
    private fun filterAndCleanText(text: String): String {
        var cleaned = text

        // STEP 1: Remove code blocks first (```...```)
        cleaned = cleaned.replace(Regex("```[a-z]*\\n?([\\s\\S]*?)```"), "$1")

        // STEP 2: Remove inline code (`text`)
        cleaned = cleaned.replace(Regex("`(.+?)`"), "$1")

        // STEP 3: Remove markdown headers (##, ###, etc.)
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")

        // STEP 4: Remove ALL asterisks-based formatting (bold, italic, bullets)
        // Remove **text** (bold)
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        // Remove *text* (italic)
        cleaned = cleaned.replace(Regex("\\*(.+?)\\*"), "$1")
        // Remove any remaining standalone asterisks at line start (bullets)
        cleaned = cleaned.replace(Regex("^\\s*\\*+\\s+", RegexOption.MULTILINE), "")
        // Remove any remaining asterisks
        cleaned = cleaned.replace("*", "")

        // STEP 5: Remove underscores formatting
        cleaned = cleaned.replace(Regex("__(.+?)__"), "$1")
        cleaned = cleaned.replace(Regex("_(.+?)_"), "$1")

        // STEP 6: Remove other list markers
        cleaned = cleaned.replace(Regex("^\\s*[•+-]\\s+", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")

        // STEP 7: Remove markdown links [text](url) - keep only text
        cleaned = cleaned.replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")

        // STEP 8: Remove blockquotes and horizontal rules
        cleaned = cleaned.replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("^[-_]{3,}$", RegexOption.MULTILINE), "")

        // STEP 9: Clean up whitespace
        // Convert multiple newlines to double newline
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")
        // Remove excessive spaces
        cleaned = cleaned.replace(Regex("[ \\t]+"), " ")
        // Clean up spaces around newlines
        cleaned = cleaned.replace(Regex(" *\\n *"), "\n")

        // STEP 10: Ensure proper sentence spacing
        cleaned = cleaned.replace(Regex("([.!?])([A-Z])"), "$1 $2")

        // STEP 11: Remove incomplete sentences at the end (optional)
        if (cleaned.length > 50 && !cleaned.matches(Regex(".*[.!?]\\s*$"))) {
            val lastComplete = cleaned.lastIndexOfAny(charArrayOf('.', '!', '?'))
            if (lastComplete > cleaned.length / 2) {
                cleaned = cleaned.substring(0, lastComplete + 1)
            }
        }

        return cleaned.trim()
    }

    /**
     * Streams interview-style answers with enhanced prompting and filtering
     */
    fun streamInterviewAnswer(
        question: String,
        onChunk: (String) -> Unit,
        onError: (String) -> Unit,
        useEnhancedPrompt: Boolean = true
    ) {
        val finalPrompt = if (useEnhancedPrompt) {
            createInterviewPrompt(question)
        } else {
            question
        }

        streamAnswer(
            prompt = finalPrompt,
            onChunk = { rawText ->
                val cleaned = filterAndCleanText(rawText)
                onChunk(cleaned)
            },
            onError = onError
        )
    }

    /**
     * Original streaming method with optional text filtering
     */
    fun streamAnswer(
        prompt: String,
        onChunk: (String) -> Unit,
        onError: (String) -> Unit,
        applyFilter: Boolean = false
    ) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqJson = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 2048) // Increased for detailed answers
                put("temperature", 0.7) // Balanced creativity
                put("topP", 0.9)
                put("topK", 40)
                put("thinkingConfig", JSONObject().apply { put("thinkingBudget", 0) })
            })
        }

        val body: RequestBody = reqJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        val main = Handler(Looper.getMainLooper())

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                main.post { onError(e.message ?: "Request failed") }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        var msg = "HTTP ${response.code}"
                        try {
                            val errJson = JSONObject(errBody).optJSONObject("error")
                            val emsg = errJson?.optString("message").orEmpty()
                            if (emsg.isNotBlank()) msg = "$msg: $emsg"
                        } catch (_: Throwable) {
                            // Keep default error message
                        }
                        main.post { onError(msg) }
                        return
                    }

                    val source = response.body?.source()
                    var accumulator = ""

                    try {
                        while (source?.exhausted() == false) {
                            val line = source.readUtf8Line() ?: break

                            if (!line.startsWith("data: ")) continue

                            val jsonData = line.substring(6).trim()
                            if (jsonData == "[DONE]" || jsonData.isEmpty()) continue

                            try {
                                val chunk = JSONObject(jsonData)
                                val candidates = chunk.optJSONArray("candidates")

                                if (candidates != null && candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    val text = parts?.optJSONObject(0)?.optString("text").orEmpty()

                                    if (text.isNotBlank()) {
                                        accumulator += text
                                        val output = if (applyFilter) {
                                            filterAndCleanText(accumulator)
                                        } else {
                                            accumulator
                                        }
                                        main.post { onChunk(output) }
                                    }
                                }
                            } catch (_: Throwable) {
                                // Skip malformed chunks
                            }
                        }

                        if (accumulator.isBlank()) {
                            main.post { onChunk("No response generated. Please try again.") }
                        }
                    } catch (t: Throwable) {
                        main.post { onError("Stream parsing error: ${t.message}") }
                    }
                }
            }
        })
    }
}

// Usage Example:
/*
val client = GeminiClient(httpClient, apiKey)

// For interview-style Q&A with enhanced formatting
client.streamInterviewAnswer(
    question = "What is polymorphism in OOP?",
    onChunk = { formattedText ->
        // Update UI with clean, organized text
        textView.text = formattedText
    },
    onError = { error ->
        // Handle errors
        Log.e("Gemini", error)
    }
)

// Or use the original method with filtering
client.streamAnswer(
    prompt = "Your custom prompt here",
    onChunk = { text -> /* update UI */ },
    onError = { error -> /* handle error */ },
    applyFilter = true
)
*/
