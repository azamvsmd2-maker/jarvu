package com.interview.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interview.assistant.audio.VoiceDetectionManager
import com.interview.assistant.data.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Message(
    val id: Long = System.currentTimeMillis(),
    val question: String,
    val answer: String,
    val isStreaming: Boolean = false
)

data class UiState(
    val messages: List<Message> = emptyList(),
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val audioLevel: Float = 0f,
    val apiKey: String = ""
)

class InterviewViewModel(
    private val repository: InterviewRepository,
    private val voiceManager: VoiceDetectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState(apiKey = repository.getApiKey()))
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            voiceManager.isListening.collect { listening ->
                _uiState.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            voiceManager.rmsDb.collect { db ->
                _uiState.update { it.copy(audioLevel = db) }
            }
        }
        viewModelScope.launch {
            voiceManager.transcribedText.collect { transcribedPrompt ->
                submitQuestion(transcribedPrompt)
            }
        }
    }

    fun saveApiKey(newKey: String) {
        repository.saveApiKey(newKey)
        _uiState.update { it.copy(apiKey = newKey.trim()) }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun submitQuestion(query: String) {
        if (query.isBlank()) return
        val message = Message(question = query, answer = "", isStreaming = true)
        _uiState.update {
            it.copy(
                messages = it.messages + message,
                isProcessing = true
            )
        }

        viewModelScope.launch {
            try {
                val fullAnswer = StringBuilder()
                repository.streamAnswer(query).collect { token ->
                    fullAnswer.append(token)
                    _uiState.update { state ->
                        val updatedList = state.messages.toMutableList()
                        val lastIndex = updatedList.lastIndex
                        if (lastIndex >= 0) {
                            updatedList[lastIndex] = updatedList[lastIndex].copy(
                                answer = fullAnswer.toString(),
                                isStreaming = true
                            )
                        }
                        state.copy(messages = updatedList, isProcessing = false)
                    }
                }
                _uiState.update { state ->
                    val updatedList = state.messages.toMutableList()
                    val lastIndex = updatedList.lastIndex
                    if (lastIndex >= 0) {
                        updatedList[lastIndex] = updatedList[lastIndex].copy(isStreaming = false)
                    }
                    state.copy(messages = updatedList, isProcessing = false)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updatedList = state.messages.toMutableList()
                    val lastIndex = updatedList.lastIndex
                    if (lastIndex >= 0) {
                        updatedList[lastIndex] = updatedList[lastIndex].copy(
                            answer = "Error: ${e.localizedMessage}",
                            isStreaming = false
                        )
                    }
                    state.copy(messages = updatedList, isProcessing = false)
                }
            }
        }
    }
}
