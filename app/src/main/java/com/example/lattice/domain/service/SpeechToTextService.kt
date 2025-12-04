package com.example.lattice.domain.service

/**
 * 语音转文字的统一结果类型：
 * - Success: 成功识别出文本
 * - Error: 出错时的错误信息
 */
sealed class SpeechResult {
    data class Success(val text: String) : SpeechResult()
    data class Error(val message: String) : SpeechResult()
}

/**
 * 领域层的语音转写能力抽象。
 *
 * UI / ViewModel 只依赖这个接口，
 * 至于底层是 Google STT、Whisper 还是 AssemblyAI，则由 data 层实现决定。
 */
interface SpeechToTextService {

    /**
     * 录音并转写成文本。
     *
     * @param seconds    录音时长（秒）
     * @param languageCode  语言代码，例如 "en-US"、"zh-CN"
     */
    suspend fun recordAndTranscribe(
        seconds: Int = 5,
        languageCode: String = "en-US"
    ): SpeechResult
}
