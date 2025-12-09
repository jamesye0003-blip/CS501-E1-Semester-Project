package com.example.lattice.domain.service

/**
 * 语音转文字的统一结果类型：
 * - Success: 成功识别出文本
 * - Error: 出错时的错误信息
 *
 * Unified result type for speech-to-text:
 * - Success: Successfully identified the text
 * - Error: Error message when an error occurs
 */
sealed class SpeechResult {
    data class Success(val text: String) : SpeechResult()
    data class Error(val message: String) : SpeechResult()
}

/**
 * 领域层的语音转写能力抽象。
 * Domain abstraction for speech-to-text.
 */
interface SpeechToTextService {

    /**
     * Record and transcribe to text.
     *
     * @param seconds       recording duration (seconds)
     * @param languageCode  language code, e.g., "en-US", "zh-CN"
     */
    suspend fun recordAndTranscribe(
        seconds: Int = 5,
        languageCode: String = "en-US"
    ): SpeechResult
}
