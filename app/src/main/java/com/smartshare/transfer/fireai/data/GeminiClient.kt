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
    fun streamAnswer(
        prompt: String,
        onChunk: (String) -> Unit,
        onError: (String) -> Unit
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
                put("maxOutputTokens", 1024)
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
                main.post { onError(e.message ?: "request failed") }
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
                        } catch (_: Throwable) {}
                        main.post { onError(msg) }
                        return
                    }
                    val source = response.body?.source()
                    var acc = ""
                    try {
                        while (!source?.exhausted()!!) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data: ")) continue
                            val jsonData = line.substring(6).trim()
                            if (jsonData == "[DONE]") break
                            if (jsonData.isEmpty()) continue
                            try {
                                val chunk = JSONObject(jsonData)
                                val candidates = chunk.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val content = candidates.getJSONObject(0).optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                                    if (text.isNotBlank()) {
                                        acc += text
                                        val emit = acc
                                        main.post { onChunk(emit) }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                        if (acc.isBlank()) main.post { onChunk("(no answer)") }
                    } catch (t: Throwable) {
                        main.post { onError("stream parse error: ${t.message}") }
                    }
                }
            }
        })
    }
}
