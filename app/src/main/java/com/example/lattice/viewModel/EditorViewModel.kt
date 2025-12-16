package com.example.lattice.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lattice.data.speech.GoogleSpeechToTextService
import com.example.lattice.domain.service.SpeechResult
import com.example.lattice.domain.service.SpeechToTextService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * EditorScreen 中"语音转文字"功能的 UI 状态 / UI state for speech-to-text feature in EditorScreen
 * 
 * 专门负责 EditorScreen 中"语音转文字"部分的 UI 状态。
 * 表单本身（title/description/priority/date 等）仍由 EditorScreen 自己用 rememberSaveable 管理。
 * 
 * Specifically manages the UI state for the speech-to-text part in EditorScreen.
 * The form itself (title/description/priority/date, etc.) is still managed by EditorScreen using rememberSaveable.
 */
data class EditorSttUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val error: String? = null
)

/**
 * EditorScreen 的语音转文字功能 ViewModel / ViewModel for speech-to-text feature in EditorScreen
 * 
 * 管理 EditorScreen 中语音转文字功能的 UI 状态和业务逻辑。
 * 负责处理录音、转写和错误状态。
 * 
 * Manages UI state and business logic for speech-to-text feature in EditorScreen.
 * Handles recording, transcription, and error states.
 */
class EditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val speechService: SpeechToTextService =
        GoogleSpeechToTextService(application.applicationContext)

    private val _uiState = MutableStateFlow(EditorSttUiState())
    val uiState: StateFlow<EditorSttUiState> = _uiState.asStateFlow()

    /**
     * 清除错误提示 / Clear error message
     * 
     * UI 点击麦克风前可以先调用此方法清除之前的错误状态。
     * 
     * Can be called before UI clicks the microphone to clear previous error state.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 设置错误并重置录音/转写状态 / Set error and reset recording/transcription state
     * 
     * @param message 错误消息 / Error message
     */
    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isTranscribing = false,
            error = message
        )
    }

    /**
     * 发起一次录音和转写 / Start recording and transcription
     * 
     * 发起一次录音和转写操作。如果已经在录音或转写中，则忽略重复调用。
     * 识别到文本后通过回调返回，由 UI 决定填到 title 还是 description。
     * 
     * Starts a recording and transcription operation. Ignores duplicate calls if already recording or transcribing.
     * Returns recognized text through callback, and UI decides whether to fill it into title or description.
     * 
     * @param seconds 录音时长（秒）/ Recording duration in seconds
     * @param languageCode 语言代码，例如 "en-US"、"zh-CN" / Language code, e.g., "en-US", "zh-CN"
     * @param onResult 识别到文本后的回调，由 UI 决定填到 title 还是 description / Callback when text is recognized, UI decides whether to fill into title or description
     */
    fun startRecording(
        seconds: Int = 5,
        languageCode: String = "en-US",
        onResult: (String) -> Unit
    ) {
        // Ignore duplicate calls if already recording or transcribing
        val current = _uiState.value
        if (current.isRecording || current.isTranscribing) return

        viewModelScope.launch {
            _uiState.value = EditorSttUiState(
                isRecording = true,
                isTranscribing = false,
                error = null
            )

            val result = try {
                speechService.recordAndTranscribe(
                    seconds = seconds,
                    languageCode = languageCode
                )
            } catch (e: Exception) {
                SpeechResult.Error(e.message ?: "Speech recognition failed.")
            }

            when (result) {
                is SpeechResult.Success -> {
                    _uiState.value = EditorSttUiState(
                        isRecording = false,
                        isTranscribing = false,
                        error = null
                    )
                    onResult(result.text)
                }

                is SpeechResult.Error -> {
                    _uiState.value = EditorSttUiState(
                        isRecording = false,
                        isTranscribing = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
