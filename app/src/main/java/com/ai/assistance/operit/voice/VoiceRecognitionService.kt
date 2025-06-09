package com.ai.assistance.operit.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.VoiceInputState
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.voice.recognizer.VoiceRecognizer
import com.ai.assistance.operit.voice.recognizer.VoiceRecognizerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

/**
 * 语音识别服务，负责将语音转换为文本
 * 支持本地和云端识别，以及多语言识别
 */
class VoiceRecognitionService(
    private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val audioStreamManager: AudioStreamManager,
    private val noiseSuppressionManager: NoiseSuppressionManager,
    private val enhancedAIService: EnhancedAIService
) {
    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val MAX_RESULTS = 3
        private const val CONTINUOUS_LISTENING_DELAY = 1000L // 1000ms
    }

    /**
     * 语音识别器提供商枚举
     */
    enum class RecognitionProvider {
        ANDROID_BUILTIN,  // Android内置的SpeechRecognizer
        FUN_AUDIO_LLM,    // FunAudioLLMAPI / LOCAL_MODEL
        GOOGLE_MLKIT,     // Google的ML Kit本地语音识别
        WHISPER_LOCAL,    // 本地运行的Whisper模型
        OPENAI_API,       // OpenAI Whisper API
        AZURE_API,        // Microsoft Azure Speech API
        GOOGLE_CLOUD      // Google Cloud Speech API
    }
    
    // 语音识别器工厂
    private val recognizerFactory = VoiceRecognizerFactory(
        context,
        voicePreferences,
        audioStreamManager,
        noiseSuppressionManager,
        enhancedAIService
    )
    
    // 当前使用的语音识别器
    private var currentRecognizer: VoiceRecognizer? = null
    
    // 语音识别状态
    private val _inputState = MutableStateFlow(
        VoiceInputState(
            isListening = false,
            recognitionConfidence = 0f,
            detectedLanguage = null,
            partialResult = "",
            noiseLevel = 0f
        )
    )
    val inputState: StateFlow<VoiceInputState> = _inputState.asStateFlow()
    
    // 识别结果
    private val _recognitionResults = MutableSharedFlow<String>(
        replay = 0,  // 不重放历史值
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val recognitionResults = _recognitionResults.asSharedFlow()
    
    // 部分识别结果
    private val _partialResults = MutableSharedFlow<String>()
    val partialResults: Flow<String> = _partialResults.asSharedFlow()
    
    // 错误
    private val _errors = MutableSharedFlow<RecognitionError>()
    val errors: Flow<RecognitionError> = _errors.asSharedFlow()
    
    // 协程范围
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 识别配置
    private var continuousListening = false
    private var preferredLanguage: String? = null
    private var autoDetectLanguage = true
    
    // 当前使用的识别提供商
    private var currentProvider: RecognitionProvider = RecognitionProvider.ANDROID_BUILTIN
    
    // 错误类型
    sealed class RecognitionError {
        data object NoMatch : RecognitionError()
        data object NetworkError : RecognitionError()
        data object AudioError : RecognitionError()
        data object InsufficientPermissions : RecognitionError()
        data object ServiceNotAvailable : RecognitionError()
        data class ServerError(val errorCode: Int) : RecognitionError()
        data class UnknownError(val errorCode: Int) : RecognitionError()
    }
    
    init {
        loadSettings()
        setupCollectors()
    }
    
    /**
     * 从用户设置中加载语音识别配置
     */
    private fun loadSettings() {
        serviceScope.launch {
            try {
                continuousListening = voicePreferences.getContinuousListeningEnabled()
                preferredLanguage = voicePreferences.getPreferredLanguage()
                autoDetectLanguage = voicePreferences.getAutoDetectLanguageEnabled()
                
                // 加载识别提供商设置
                try {
                    currentProvider = voicePreferences.getRecognitionProvider()
                    initializeRecognizer()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading recognition provider, using default", e)
                    currentProvider = RecognitionProvider.ANDROID_BUILTIN
                    initializeRecognizer()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load voice settings", e)
                // 使用默认设置
                continuousListening = false
                preferredLanguage = Locale.getDefault().toString()
                autoDetectLanguage = true
                currentProvider = RecognitionProvider.ANDROID_BUILTIN
                initializeRecognizer()
            }
        }
    }
    
    /**
     * 设置收集器
     */
    private fun setupCollectors() {
        serviceScope.launch {
            audioStreamManager.getCurrentNoiseLevel()
            // 监听噪音级别
            serviceScope.launch {
                while (true) {
                    val noiseLevel = audioStreamManager.getCurrentNoiseLevel() * 100f
                    _inputState.value = _inputState.value.copy(noiseLevel = noiseLevel)
                    delay(50) // 20Hz更新率
                }
            }
        }
    }
    
    /**
     * 初始化语音识别器
     */
    private fun initializeRecognizer() {
        try {
            // 释放旧的识别器
            currentRecognizer?.release()
            
            // 创建新的识别器
            currentRecognizer = recognizerFactory.createRecognizer(currentProvider)
            
            // 设置收集器
            setupRecognizerCollectors()
            
            Log.d(TAG, "Initialized recognizer for provider: $currentProvider")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer", e)
            serviceScope.launch {
                _errors.emit(RecognitionError.ServiceNotAvailable)
            }
        }
    }
    
    /**
     * 设置识别器收集器
     */
    private fun setupRecognizerCollectors() {
        currentRecognizer?.let { recognizer ->
            serviceScope.launch {
                // 收集识别结果
                serviceScope.launch {
                    recognizer.getRecognitionResultsFlow().collect { result ->
                        _recognitionResults.emit(result)
                        _inputState.value = _inputState.value.copy(
                            isListening = false,
                            partialResult = result
                        )
                    }
                }
                
                // 收集部分识别结果
                serviceScope.launch {
                    recognizer.getPartialResultsFlow().collect { result ->
                        _partialResults.emit(result)
                        _inputState.value = _inputState.value.copy(partialResult = result)
                    }
                }
                
                // 收集错误
                serviceScope.launch {
                    recognizer.getErrorsFlow().collect { error ->
                        when (error) {
                            is RecognitionError -> _errors.emit(error)
                            else -> _errors.emit(RecognitionError.UnknownError(-1))
                        }
                        _inputState.value = _inputState.value.copy(isListening = false)
                        
                        // 处理连续监听模式下的错误
                        if (continuousListening && error is RecognitionError.NoMatch) {
                            delay(CONTINUOUS_LISTENING_DELAY)
                            startListening(true)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 开始语音识别
     * @param continuous 是否连续识别
     * @param languageOverride 强制使用特定语言
     */
    suspend fun startListening(
        continuous: Boolean = continuousListening,
        languageOverride: String? = Locale.getDefault().language
    ) = withContext(Dispatchers.Main) {
        if (_inputState.value.isListening) {
            stopListening()
        }
        
        if (currentRecognizer == null) {
            initializeRecognizer()
        }
        
        try {
            _inputState.value = _inputState.value.copy(
                isListening = true,
                partialResult = "",
                recognitionConfidence = 0f
            )
            
            // 启动识别
            currentRecognizer?.startRecognition(continuous, languageOverride)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _inputState.value = _inputState.value.copy(isListening = false)
            _errors.emit(RecognitionError.AudioError)
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        try {
            continuousListening = false
            currentRecognizer?.stopRecognition()
            _inputState.value = _inputState.value.copy(
                isListening = false,
                partialResult = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }
    
    /**
     * 取消当前识别
     */
    suspend fun cancel() = withContext(Dispatchers.Main) {
        try {
            currentRecognizer?.cancelRecognition()
            _inputState.value = _inputState.value.copy(
                isListening = false,
                partialResult = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        }
    }
    
    /**
     * 设置连续识别模式
     */
    fun setContinuousListening(enabled: Boolean) {
        continuousListening = enabled
        serviceScope.launch {
            voicePreferences.setContinuousListeningEnabled(enabled)
        }
    }
    
    /**
     * 设置首选语言
     */
    fun setPreferredLanguage(language: String) {
        preferredLanguage = language
        serviceScope.launch {
            voicePreferences.setPreferredLanguage(language)
        }
    }
    
    /**
     * 设置是否自动检测语言
     */
    fun setAutoDetectLanguage(enabled: Boolean) {
        autoDetectLanguage = enabled
        serviceScope.launch {
            voicePreferences.setAutoDetectLanguageEnabled(enabled)
        }
    }
    
    /**
     * 设置识别提供商
     * @param provider 要使用的识别提供商
     * @return 是否成功切换
     */
    suspend fun setRecognitionProvider(provider: RecognitionProvider): Boolean {
        if (currentProvider == provider) return true
        
        try {
            // 保存设置
            voicePreferences.setRecognitionProvider(provider)
            
            // 清理现有资源
            if (_inputState.value.isListening) {
                stopListening()
            }
            
            currentRecognizer?.release()
            currentRecognizer = null
            
            // 更新当前提供商
            currentProvider = provider
            
            // 重新初始化识别器
            initializeRecognizer()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching recognition provider", e)
            return false
        }
    }
    
    /**
     * 获取当前识别提供商
     */
    fun getCurrentRecognitionProvider(): RecognitionProvider {
        return currentProvider
    }
    
    /**
     * 清理资源
     */
    fun destroy() {
        serviceScope.coroutineContext.cancelChildren()
        currentRecognizer?.release()
        currentRecognizer = null
    }
} 