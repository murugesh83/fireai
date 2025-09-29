package com.smartshare.transfer.fireai.data

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.annotation.RequiresPermission
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class CloudSttRecorder(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val apiKey: String,
) {
    private var recorderThread: Thread? = null
    private var shouldRun = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        if (recorderThread != null) return
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, sampleRate * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord = recorder
        shouldRun.set(true)
        recorder.startRecording()
        recorderThread = Thread {
            try {
                val chunkMs = 2000
                val bytesPerSec = sampleRate * 2
                val targetBytes = bytesPerSec * chunkMs / 1000
                val readBuffer = ByteArray(targetBytes)
                while (shouldRun.get()) {
                    var readTotal = 0
                    while (readTotal < targetBytes && shouldRun.get()) {
                        val r = recorder.read(readBuffer, readTotal, targetBytes - readTotal)
                        if (r > 0) readTotal += r else Thread.sleep(10)
                    }
                    if (!shouldRun.get()) break
                    onPartial("…")

                    val contentB64 = Base64.encodeToString(readBuffer, 0, readTotal, Base64.NO_WRAP)
                    val reqJson = JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("encoding", "LINEAR16")
                            put("sampleRateHertz", sampleRate)
                            put("languageCode", "en-US")
                            put("enableAutomaticPunctuation", true)
                        })
                        put("audio", JSONObject().apply { put("content", contentB64) })
                    }
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body: RequestBody = reqJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url("https://speech.googleapis.com/v1/speech:recognize")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(body)
                        .build()
                    try {
                        httpClient.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) return@use
                            val respStr = resp.body?.string().orEmpty()
                            val root = JSONObject(respStr)
                            val results = root.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val first = results.getJSONObject(0)
                                val alts = first.optJSONArray("alternatives")
                                if (alts != null && alts.length() > 0) {
                                    val transcript = alts.getJSONObject(0).optString("transcript")
                                    if (transcript.isNotBlank()) onFinal(transcript)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                    onPartial("")
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                audioRecord = null
            }
        }.also { it.start() }
    }

    fun stop() {
        shouldRun.set(false)
        recorderThread?.join(500)
        recorderThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }
}
