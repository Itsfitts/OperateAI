package com.ai.assistance.operit.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ai.assistance.operit.data.model.VoiceInputState
import com.ai.assistance.operit.data.preferences.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
    private val noiseSuppressionManager: NoiseSuppressionManager
) {
    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val MAX_RESULTS = 3
        private const val CONTINUOUS_LISTENING_DELAY = 500L // 500ms
    }

    /**
     * 语音识别器提供商枚举
     */
    enum class RecognitionProvider {
        GOOGLE_MLKIT,     // Google的ML Kit本地语音识别
        ANDROID_BUILTIN,  // Android内置的SpeechRecognizer
        WHISPER_LOCAL,    // 本地运行的Whisper模型
        OPENAI_API,       // OpenAI Whisper API
        AZURE_API,        // Microsoft Azure Speech API
        GOOGLE_CLOUD      // Google Cloud Speech API
    }
    
    // 语音识别器
    private var speechRecognizer: SpeechRecognizer? = null
    
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
    private val _recognitionResults = MutableSharedFlow<String>()
    val recognitionResults: Flow<String> = _recognitionResults.asSharedFlow()
    
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading recognition provider, using default", e)
                    currentProvider = RecognitionProvider.ANDROID_BUILTIN
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load voice settings", e)
                // 使用默认设置
                continuousListening = false
                preferredLanguage = Locale.getDefault().toString()
                autoDetectLanguage = true
                currentProvider = RecognitionProvider.ANDROID_BUILTIN
            }
        }
    }
    
    /**
     * 初始化语音识别器
     */
    private fun initializeRecognizer() {
        try {
            when (currentProvider) {
                RecognitionProvider.ANDROID_BUILTIN -> {
                    initializeAndroidRecognizer()
                }
                RecognitionProvider.GOOGLE_MLKIT -> {
                    // TODO: 实现ML Kit识别器
                    Log.i(TAG, "Google ML Kit recognizer not fully implemented yet, falling back to Android built-in")
                    initializeAndroidRecognizer()
                }
                RecognitionProvider.WHISPER_LOCAL -> {
                    // TODO: 实现本地Whisper模型
                    Log.i(TAG, "Local Whisper recognizer not implemented yet, falling back to Android built-in")
                    initializeAndroidRecognizer()
                }
                RecognitionProvider.OPENAI_API -> {
                    // TODO: 实现OpenAI API
                    Log.i(TAG, "OpenAI API recognizer not implemented yet, falling back to Android built-in")
                    initializeAndroidRecognizer()
                }
                RecognitionProvider.AZURE_API -> {
                    // TODO: 实现Azure API
                    Log.i(TAG, "Azure API recognizer not implemented yet, falling back to Android built-in")
                    initializeAndroidRecognizer()
                }
                RecognitionProvider.GOOGLE_CLOUD -> {
                    // TODO: 实现Google Cloud API
                    Log.i(TAG, "Google Cloud API recognizer not implemented yet, falling back to Android built-in")
                    initializeAndroidRecognizer()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer", e)
            serviceScope.launch {
                _errors.emit(RecognitionError.ServiceNotAvailable)
            }
        }
    }
    
    /**
     * 初始化Android内置语音识别器
     */
    private fun initializeAndroidRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } else {
            serviceScope.launch {
                _errors.emit(RecognitionError.ServiceNotAvailable)
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
        
        if (speechRecognizer == null) {
            initializeRecognizer()
        }
        
        try {
            _inputState.value = _inputState.value.copy(
                isListening = true,
                partialResult = "",
                recognitionConfidence = 0f
            )
            
            // 启动AudioStreamManager
            audioStreamManager.startAudioCapture()
            
            // 根据当前提供商执行相应的启动逻辑
            when (currentProvider) {
                RecognitionProvider.ANDROID_BUILTIN -> {
                    startAndroidRecognition(continuous, languageOverride)
                }
                RecognitionProvider.GOOGLE_MLKIT, 
                RecognitionProvider.WHISPER_LOCAL,
                RecognitionProvider.OPENAI_API,
                RecognitionProvider.AZURE_API,
                RecognitionProvider.GOOGLE_CLOUD -> {
                    // 暂时都使用Android内置识别
                    // TODO
                    startAndroidRecognition(continuous, languageOverride)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _inputState.value = _inputState.value.copy(isListening = false)
            _errors.emit(RecognitionError.AudioError)
        }
    }
    
    /**
     * 启动Android内置语音识别
     */
    private fun startAndroidRecognition(continuous: Boolean, languageOverride: String?) {
        // 准备识别意图
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // 设置语言
            val language = languageOverride ?: preferredLanguage
            if (language != null && !autoDetectLanguage) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            }
        }
        
        continuousListening = continuous
        speechRecognizer?.startListening(recognizerIntent)
    }
    
    /**
     * 停止语音识别
     */
    suspend fun stopListening() = withContext(Dispatchers.Main) {
        try {
            continuousListening = false
            speechRecognizer?.stopListening()
            audioStreamManager.stopAudioStream()
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
            speechRecognizer?.cancel()
            audioStreamManager.stopAudioStream()
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
            
            speechRecognizer?.destroy()
            speechRecognizer = null
            
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
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    /**
     * 语音识别监听器
     */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            serviceScope.launch {
                _inputState.value = _inputState.value.copy(
                    isListening = true,
                    recognitionConfidence = 0f,
                    partialResult = ""
                )
            }
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 将RMS值转换为0-100的噪音级别
            val normalizedLevel = min((rmsdB + 100) / 100f, 1f) * 100f
            serviceScope.launch {
                _inputState.value = _inputState.value.copy(noiseLevel = normalizedLevel)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // 可以用于低级处理语音数据
            buffer?.let { audioData ->
                serviceScope.launch {
                    val processedBuffer = noiseSuppressionManager.processAudioBuffer(audioData)
                    // 这里可以处理处理后的音频缓冲区...
                }
            }
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            val errorType = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> RecognitionError.NoMatch
                SpeechRecognizer.ERROR_NETWORK -> RecognitionError.NetworkError
                SpeechRecognizer.ERROR_AUDIO -> RecognitionError.AudioError
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionError.InsufficientPermissions
                SpeechRecognizer.ERROR_CLIENT -> RecognitionError.UnknownError(error)
                SpeechRecognizer.ERROR_SERVER -> RecognitionError.ServerError(error)
                else -> RecognitionError.UnknownError(error)
            }
            
            serviceScope.launch {
                _errors.emit(errorType)
                _inputState.value = _inputState.value.copy(isListening = false)
                
                if (continuousListening && errorType == RecognitionError.NoMatch) {
                    // 如果是连续模式且没有匹配，短暂延迟后重新开始识别
                    delay(CONTINUOUS_LISTENING_DELAY)
                    startListening(true)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val recognition = matches[0]
                    
                    serviceScope.launch {
                        // 更新状态
                        _inputState.value = _inputState.value.copy(
                            partialResult = recognition,
                            isListening = false
                        )
                        
                        // 发送识别结果
                        _recognitionResults.emit(recognition)
                        
                        // 检查是否需要继续监听
                        if (continuousListening) {
                            delay(CONTINUOUS_LISTENING_DELAY)
                            startListening(true)
                        }
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val partialRecognition = matches[0]
                    
                    serviceScope.launch {
                        // 更新部分识别结果
                        _inputState.value = _inputState.value.copy(partialResult = partialRecognition)
                        _partialResults.emit(partialRecognition)
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // 可以处理自定义事件
            Log.d(TAG, "Speech recognizer event: $eventType")
        }
    }
} 