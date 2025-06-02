package com.ai.assistance.operit.ui.features.voice

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.AIService
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.VoiceCommand
import com.ai.assistance.operit.voice.VoiceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 语音助手ViewModel
 * 负责管理语音服务的状态和操作
 */
class VoiceAssistantViewModel(
    application: Application,
    private val aiService: EnhancedAIService,
    private val aiToolHandler: AIToolHandler
) : AndroidViewModel(application) {
    
    // 语音模块
    private var voiceModule: VoiceModule? = null
    
    // UI状态
    private val _uiState = MutableStateFlow(UiState())
    val voiceState: StateFlow<UiState> = _uiState.asStateFlow()
    
    /**
     * 初始化语音模块
     */
    fun initializeVoiceModule() {
        if (voiceModule != null) return
        
        viewModelScope.launch {
            try {
                // 创建语音模块
                voiceModule = VoiceModule(
                    context = getApplication(),
                    aiService = aiService,
                    aiToolHandler = aiToolHandler
                )
                
                // 监听语音模块状态变化
                viewModelScope.launch {
                    voiceModule?.voiceState?.collect { voiceState ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                isInitialized = voiceState.isInitialized,
                                isVoiceEnabled = voiceState.isVoiceEnabled,
                                isWakeWordEnabled = voiceState.isWakeWordEnabled,
                                isReadResponsesEnabled = voiceState.isReadResponsesEnabled,
                                isContinuousListeningEnabled = voiceState.isContinuousListeningEnabled,
                                isListening = voiceState.isListening,
                                isSpeaking = voiceState.isSpeaking,
                                recognitionConfidence = voiceState.recognitionConfidence,
                                noiseLevel = voiceState.noiseLevel,
                                lastRecognizedText = voiceState.lastRecognizedText,
                                lastWakeWord = voiceState.lastWakeWord,
                                partialText = voiceState.partialText,
                                lastCommand = voiceState.lastCommand,
                                error = voiceState.error
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "初始化语音模块失败: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * 开始语音监听
     */
    suspend fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            voiceModule?.startListening(
                wakeWordMode = _uiState.value.isWakeWordEnabled
            )
        }
    }
    
    /**
     * 停止语音监听
     */
    suspend fun stopListening() {
        voiceModule?.stopListening()
    }
    
    /**
     * 语音输出文本
     */
    suspend fun speak(text: String, interruptCurrent: Boolean = false) {
        voiceModule?.speak(text, interruptCurrent)
    }
    
    /**
     * 停止语音输出
     */
    fun stopSpeaking() {
        voiceModule?.stopSpeaking()
    }
    
    /**
     * 切换唤醒词模式
     */
    suspend fun toggleWakeWord() {
        val currentState = _uiState.value.isWakeWordEnabled
        voiceModule?.setWakeWordEnabled(!currentState)
    }
    
    /**
     * 切换语音输出
     */
    suspend fun toggleReadResponses() {
        val currentState = _uiState.value.isReadResponsesEnabled
        voiceModule?.setVoiceEnabled(!currentState)
    }
    
    /**
     * 启动连续对话模式
     */
    suspend fun startContinuousConversation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            voiceModule?.startContinuousConversation()
        }
    }
    
    /**
     * 停止连续对话模式
     */
    suspend fun stopContinuousConversation() {
        voiceModule?.stopContinuousConversation()
    }
    
    /**
     * UI状态数据类
     */
    data class UiState(
        val isInitialized: Boolean = false,
        val isVoiceEnabled: Boolean = false,
        val isWakeWordEnabled: Boolean = false,
        val isReadResponsesEnabled: Boolean = true,
        val isContinuousListeningEnabled: Boolean = false,
        val isListening: Boolean = false,
        val isSpeaking: Boolean = false,
        val recognitionConfidence: Float = 0.0f,
        val noiseLevel: Float = 0.0f,
        val lastRecognizedText: String = "",
        val lastWakeWord: String = "",
        val partialText: String = "",
        val lastCommand: VoiceCommand? = null,
        val error: String? = null
    )
    
    override fun onCleared() {
        super.onCleared()
        // 停止所有语音相关活动
        viewModelScope.launch {
            stopListening()
            stopSpeaking()
        }
    }
    
    /**
     * ViewModel工厂，用于创建VoiceAssistantViewModel实例
     */
    class Factory(
        private val application: Application,
        private val aiService: EnhancedAIService,
        private val aiToolHandler: AIToolHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VoiceAssistantViewModel::class.java)) {
                return VoiceAssistantViewModel(application, aiService, aiToolHandler) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 