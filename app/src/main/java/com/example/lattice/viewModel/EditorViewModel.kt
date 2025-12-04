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
 * 专门负责 EditorScreen 中“语音转文字”部分的 UI 状态。
 * 表单本身（title/description/priority/date 等）仍由 EditorScreen 自己用 rememberSaveable 管。
 */
data class EditorSttUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val error: String? = null
)

class EditorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val speechService: SpeechToTextService =
        GoogleSpeechToTextService(application.applicationContext)

    private val _uiState = MutableStateFlow(EditorSttUiState())
    val uiState: StateFlow<EditorSttUiState> = _uiState.asStateFlow()

    /** 清掉错误提示（UI 点击麦克风前可以先调用）。 */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** 设置错误并重置录音/转写状态。 */
    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isTranscribing = false,
            error = message
        )
    }

    /**
     * 发起一次录音 + 转写。
     *
     * @param seconds       录音时长（秒）
     * @param languageCode  语言代码，例如 "en-US"、"zh-CN"
     * @param onResult      识别到文本后的回调，由 UI 决定填到 title 还是 description
     */
    fun startRecording(
        seconds: Int = 5,
        languageCode: String = "en-US",
        onResult: (String) -> Unit
    ) {
        // 已经在录音/转写中时，直接忽略重复点击
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
