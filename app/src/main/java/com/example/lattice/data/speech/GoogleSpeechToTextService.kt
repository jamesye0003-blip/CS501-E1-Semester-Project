package com.example.lattice.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lattice.domain.service.SpeechResult
import com.example.lattice.domain.service.SpeechToTextService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Cloud Speech-to-Text 的 Retrofit API 定义。
 */
private interface GoogleSpeechApi {

    @POST("v1/speech:recognize")
    suspend fun recognize(
        @Query("key") apiKey: String,
        @Body body: GoogleRecognitionRequest
    ): GoogleSpeechResponse
}

/**
 * Google STT 请求 / 响应数据模型。
 */
private data class GoogleRecognitionRequest(
    val config: GoogleRecognitionConfig,
    val audio: GoogleRecognitionAudio
)

private data class GoogleRecognitionConfig(
    val encoding: String,
    val sampleRateHertz: Int,
    val languageCode: String
)

private data class GoogleRecognitionAudio(
    val content: String
)

private data class GoogleSpeechResponse(
    val results: List<GoogleSpeechResult>?
)

private data class GoogleSpeechResult(
    val alternatives: List<GoogleSpeechAlternative>?
)

private data class GoogleSpeechAlternative(
    val transcript: String?,
    val confidence: Float?
)

/**
 * 使用 Google Cloud Speech-to-Text 的语音转写实现。
 *
 * - 负责录音（AudioRecord）
 * - 负责调用 Google STT 接口
 * - 返回领域层的 SpeechResult
 */
class GoogleSpeechToTextService(
    private val context: Context,
    // 提供一个带默认值的 apiKey，方便你在测试阶段直接用 context 构造
    private val apiKey: String = API_KEY
) : SpeechToTextService {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate) // 避免极小 buffer

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val api: GoogleSpeechApi by lazy {
        retrofit.create(GoogleSpeechApi::class.java)
    }

    override suspend fun recordAndTranscribe(
        seconds: Int,
        languageCode: String
    ): SpeechResult = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            return@withContext SpeechResult.Error("RECORD_AUDIO permission not granted")
        }

        val audioBytes = try {
            recordPcm(seconds)
        } catch (e: Exception) {
            Log.e(TAG, "Record audio failed", e)
            return@withContext SpeechResult.Error(
                "Failed to record audio: ${e.message ?: "unknown error"}"
            )
        }

        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        try {
            val request = GoogleRecognitionRequest(
                config = GoogleRecognitionConfig(
                    encoding = "LINEAR16",
                    sampleRateHertz = sampleRate,
                    languageCode = languageCode
                ),
                audio = GoogleRecognitionAudio(
                    content = audioBase64
                )
            )

            val response = api.recognize(
                apiKey = apiKey,
                body = request
            )

            val transcript = response.results
                ?.firstOrNull()
                ?.alternatives
                ?.firstOrNull()
                ?.transcript
                ?.takeIf { !it.isNullOrBlank() }

            if (transcript != null) {
                SpeechResult.Success(transcript)
            } else {
                SpeechResult.Error("No speech recognized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google STT failed", e)
            SpeechResult.Error(
                "Speech recognition failed: ${e.message ?: "unknown error"}"
            )
        }
    }

    /**
     * 是否已经获得麦克风权限。
     * 请求权限仍应由 UI 层负责。
     */
    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 使用 AudioRecord 录制原始 PCM 数据。
     */
    private fun recordPcm(seconds: Int): ByteArray {
        val totalBytes = seconds * sampleRate * 2 // 16-bit, mono → 2 bytes per sample
        val buffer = ByteArray(minBufferSize)
        val output = ByteArray(totalBytes)
        var offset = 0

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord not initialized")
        }

        audioRecord.startRecording()
        try {
            while (offset < totalBytes) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) break

                val remaining = totalBytes - offset
                val toCopy = minOf(read, remaining)
                System.arraycopy(buffer, 0, output, offset, toCopy)
                offset += toCopy
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        // 如果实际录得比预期少，裁剪掉尾部空字节
        return if (offset == totalBytes) output else output.copyOf(offset)
    }

    companion object {
        private const val TAG = "GoogleSpeechToText"
        private const val BASE_URL = "https://speech.googleapis.com/"

        /**
         * 临时用于测试的 API Key。
         * TODO: 测试完后请改为从安全位置（如 BuildConfig / Encrypted prefs）注入。
         */
        const val API_KEY: String = "AIzaSyBpPtFfa9jYX5fdY6L4S0Wvj3dgt2VQimI"
    }
}
