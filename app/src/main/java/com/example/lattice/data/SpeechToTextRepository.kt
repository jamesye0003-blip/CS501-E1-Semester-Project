package com.example.lattice.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 录制一小段 PCM 音频 + 调用 Google Cloud Speech-to-Text。
 * 所有错误在这里转成 SpeechResult.Error，而不是抛异常，避免 App 闪退。
 */
class SpeechToTextRepository(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextRepository"
        private const val BASE_URL = "https://speech.googleapis.com/"

        // ⚠ 课堂项目可以硬编码，提交 GitHub 前建议换/删掉
        const val API_KEY = "AIzaSyBpPtFfa9jYX5fdY6L4S0Wvj3dgt2VQimI"
    }

    private val api: GoogleSpeechApi by lazy {
        val client = OkHttpClient.Builder().build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleSpeechApi::class.java)
    }

    /**
     * 录音 + 调用 Cloud Speech，同步返回结果。
     * 不抛异常，只返回 Success / Error。
     */
    suspend fun recordAndTranscribe(
        seconds: Int = 5,
        languageCode: String = "en-US"
    ): SpeechResult = withContext(Dispatchers.IO) {
        try {
            // 1. 权限检查
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                return@withContext SpeechResult.Error("Microphone permission not granted.")
            }

            // 2. 录音
            val audioBytes = recordPcm(seconds)

            if (audioBytes.isEmpty()) {
                return@withContext SpeechResult.Error("No audio data recorded.")
            }

            // 3. Base64
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            // 4. 调用 Cloud Speech API
            val request = GoogleSpeechRequest(
                config = GoogleRecognitionConfig(
                    encoding = "LINEAR16",
                    sampleRateHertz = 16000,
                    languageCode = languageCode
                ),
                audio = GoogleRecognitionAudio(
                    content = audioBase64
                )
            )

            val response = api.recognize(
                apiKey = API_KEY,
                body = request
            )

            val text = response.results
                ?.firstOrNull()
                ?.alternatives
                ?.firstOrNull()
                ?.transcript
                ?: ""

            if (text.isBlank()) {
                SpeechResult.Error("Speech recognized but empty result.")
            } else {
                SpeechResult.Success(text)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "recordAndTranscribe error", t)
            SpeechResult.Error(t.message ?: "Speech error: ${t::class.java.simpleName}")
        }
    }

    /**
     * 录制 seconds 秒 16kHz 单声道 PCM_16BIT。
     */
    private fun recordPcm(seconds: Int): ByteArray {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize =
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            throw IllegalStateException("Invalid buffer size: $minBufferSize")
        }

        val bufferSize = minBufferSize * 2

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("AudioRecord init failed: ${e.message}")
        }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(minBufferSize)

        try {
            audioRecord.startRecording()

            val totalBytesToRead = sampleRate * 2 * seconds // 16bit = 2 bytes
            var bytesReadTotal = 0

            while (bytesReadTotal < totalBytesToRead) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE
                ) {
                    throw IOException("AudioRecord read error: $read")
                } else {
                    break
                }
            }
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Throwable) {
            }
            audioRecord.release()
        }

        return outputStream.toByteArray()
    }
}

/**
 * Cloud Speech v1 speech:recognize API 定义
 */
interface GoogleSpeechApi {
    @POST("v1/speech:recognize")
    suspend fun recognize(
        @Query("key") apiKey: String,
        @Body body: GoogleSpeechRequest
    ): GoogleSpeechResponse
}

// === 数据结构 ===

data class GoogleSpeechRequest(
    val config: GoogleRecognitionConfig,
    val audio: GoogleRecognitionAudio
)

data class GoogleRecognitionConfig(
    val encoding: String,
    val sampleRateHertz: Int,
    val languageCode: String
)

data class GoogleRecognitionAudio(
    val content: String
)

data class GoogleSpeechResponse(
    val results: List<GoogleSpeechResult>?
)

data class GoogleSpeechResult(
    val alternatives: List<GoogleSpeechAlternative>?
)

data class GoogleSpeechAlternative(
    val transcript: String?,
    val confidence: Float?
)

/**
 * 封装成功/失败结果，避免在 UI 层处理异常。
 */
sealed class SpeechResult {
    data class Success(val text: String) : SpeechResult()
    data class Error(val message: String) : SpeechResult()
}