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
 * Google Cloud Speech-to-Text 的 Retrofit API 定义 / Retrofit API definition for Google Cloud Speech-to-Text
 */
private interface GoogleSpeechApi {

    /**
     * 识别语音 / Recognize speech
     * 
     * @param apiKey API 密钥 / API key
     * @param body 识别请求体 / Recognition request body
     * @return 语音识别响应 / Speech recognition response
     */
    @POST("v1/speech:recognize")
    suspend fun recognize(
        @Query("key") apiKey: String,
        @Body body: GoogleRecognitionRequest
    ): GoogleSpeechResponse
}

/**
 * Google STT 请求数据模型 / Google STT request data model
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
 * 使用 Google Cloud Speech-to-Text 的语音转写实现 / Speech-to-text implementation using Google Cloud Speech-to-Text
 * 
 * - 负责录音（AudioRecord）
 * - 负责调用 Google STT 接口
 * - 返回领域层的 SpeechResult
 * 
 * - Records audio (AudioRecord)
 * - Calls Google STT API
 * - Returns domain layer SpeechResult
 * 
 * @param context Android 上下文 / Android context
 * @param apiKey API 密钥，默认使用 companion object 中的值 / API key, defaults to value in companion object
 */
class GoogleSpeechToTextService(
    private val context: Context,
    private val apiKey: String = API_KEY
) : SpeechToTextService {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate) // Avoid extremely small buffer

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

    /**
     * 录音并转写 / Record and transcribe
     * 
     * 录音指定时长的音频，然后调用 Google Cloud Speech-to-Text API 进行转写。
     * 如果权限未授予或录音/转写失败，返回错误结果。
     * 
     * Records audio for specified duration, then calls Google Cloud Speech-to-Text API for transcription.
     * Returns error result if permission not granted or recording/transcription fails.
     * 
     * @param seconds 录音时长（秒）/ Recording duration in seconds
     * @param languageCode 语言代码，例如 "en-US"、"zh-CN" / Language code, e.g., "en-US", "zh-CN"
     * @return 转写结果 / Transcription result
     */
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
     * 检查麦克风权限 / Check microphone permission
     * 
     * UI 层应在权限缺失时请求权限。
     * 
     * UI layer should request permission if missing.
     * 
     * @return 是否已授予权限 / Whether permission is granted
     */
    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 通过 AudioRecord 录制原始 PCM 音频 / Record raw PCM audio via AudioRecord
     * 
     * @param seconds 录音时长（秒）/ Recording duration in seconds
     * @return PCM 音频字节数组 / PCM audio byte array
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

        // Trim tail if fewer bytes than expected
        return if (offset == totalBytes) output else output.copyOf(offset)
    }

    companion object {
        private const val TAG = "GoogleSpeechToText"
        private const val BASE_URL = "https://speech.googleapis.com/"

        /**
         * 临时用于测试的 API Key / Temporary API key for testing
         * 
         * 注意：此 API Key 不应提交到版本控制系统。
         * 后续应从安全源注入（如本地配置文件）。
         * 
         * Note: This API key should not be committed to version control.
         * Should be injected from secure source later (e.g., local config file).
         */
        const val API_KEY: String = "AIzaSyBpPtFfa9jYX5fdY6L4S0Wvj3dgt2VQimI"
    }
}
