package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.VoiceActionType
import com.ai.assistance.operit.data.model.VoiceCommand
import com.ai.assistance.operit.data.model.VoiceProfile
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.voice.WakeWordDetector.WakeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音模块主类
 * 负责协调各个语音组件，提供统一接口
 */
class VoiceModule(
    private val context: Context,
    private val aiService: EnhancedAIService,
    private val aiToolHandler: AIToolHandler
) {
    // 子组件
    private val voicePreferences = VoicePreferences(context)
    private val audioStreamManager = AudioStreamManager(context)
    private val noiseSuppressionManager = NoiseSuppressionManager(context)
    private lateinit var voiceRecognitionService: VoiceRecognitionService
    private lateinit var textToSpeechService: TextToSpeechService
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var voiceProfileManager: VoiceProfileManager

    private val _chatHistory = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val chatHistory: Flow<List<Pair<String, String>>> = _chatHistory
    
    // 协程作用域
    private val moduleScope = CoroutineScope(Dispatchers.Default + Job())
    
    // 状态追踪
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    // 用于跟踪模块是否初始化完成
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    
    init {
        // 初始化仅依赖于context的组件
        moduleScope.launch {
            initializeModule()
        }
    }
    
    /**
     * 初始化模块
     */
    private suspend fun initializeModule() {
        if (!isInitializing.compareAndSet(false, true)) {
            return
        }
        
        try {
            // 初始化各组件
            voiceRecognitionService = VoiceRecognitionService(
                context,
                voicePreferences,
                audioStreamManager,
                noiseSuppressionManager
            )
            
            textToSpeechService = TextToSpeechService(
                context,
                voicePreferences,
            )
            
            voiceProfileManager = VoiceProfileManager(
                context,
                voicePreferences
            )
            
            // 需要aiService的组件放在后面初始化
            voiceCommandProcessor = VoiceCommandProcessor(
                context,
                aiService,
                aiToolHandler,
                moduleScope,
                chatHistory
            )
            
            wakeWordDetector = WakeWordDetector(
                context,
                voicePreferences,
                audioStreamManager,
                voiceRecognitionService
            )
            
            // 设置状态监听
            setupStateObservers()
            
            // 加载设置
            loadSettings()
            
            _voiceState.value = _voiceState.value.copy(isInitialized = true)
            isInitialized.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing voice module: ${e.message}")
            _voiceState.value = _voiceState.value.copy(
                isInitialized = false,
                error = "初始化语音模块失败: ${e.message}"
            )
        } finally {
            isInitializing.set(false)
        }
    }
    
    /**
     * 设置状态观察器
     */
    private fun setupStateObservers() {
        // 监听语音识别状态
        moduleScope.launch {
            voiceRecognitionService.inputState.collect { inputState ->
                _voiceState.value = _voiceState.value.copy(
                    isListening = inputState.isListening,
                    partialText = inputState.partialResult,
                    noiseLevel = inputState.noiseLevel,
                    recognitionConfidence = inputState.recognitionConfidence
                )
            }
        }
        
        // 监听TTS状态
        moduleScope.launch {
            textToSpeechService.isSpeaking.collect { ttsState ->
                _voiceState.value = _voiceState.value.copy(
                    isSpeaking = ttsState
                )
            }
        }
        
        // 监听TTS事件
        moduleScope.launch {
            textToSpeechService.ttsEvents.collect { event ->
                when (event) {
                    is TextToSpeechService.TTSEvent.Error -> {
                        _voiceState.value = _voiceState.value.copy(
                            error = event.utteranceId
                        )
                    }
                    else -> {
                        // 其他事件处理
                    }
                }
            }
        }
        
        // 监听唤醒词检测事件
        moduleScope.launch {
            wakeWordDetector.wakeEvents.collect { event ->
                when (event) {
                    is WakeEvent.WakeWordDetected -> {
                        _voiceState.value = _voiceState.value.copy(
                            lastWakeWord = event.wakeWord,
                            lastWakeTime = System.currentTimeMillis()
                        )
                    }
                    is WakeEvent.CommandAfterWake -> {
                        moduleScope.launch {
                            handleVoiceCommand(event.command)
                        }
                    }
                    is WakeEvent.Error -> {
                        _voiceState.value = _voiceState.value.copy(
                            error = event.message
                        )
                    }
                    else -> {
                        // 其他事件处理
                    }
                }
            }
        }
        
        // 监听命令处理事件
        moduleScope.launch {
            voiceCommandProcessor.commandEvents.collect { event ->
                when (event) {
                    is VoiceCommandProcessor.CommandEvent.Error -> {
                        _voiceState.value = _voiceState.value.copy(
                            error = event.message
                        )
                    }
                    is VoiceCommandProcessor.CommandEvent.CommandDetected -> {
                        _voiceState.value = _voiceState.value.copy(
                            lastCommand = event.command
                        )
                    }
                    else -> {
                        // 其他事件处理
                    }
                }
            }
        }
    }
    
    /**
     * 加载设置
     */
    private suspend fun loadSettings() {
        val voiceEnabled = voicePreferences.isVoiceEnabled()
        val wakeWordEnabled = voicePreferences.isWakeWordEnabled()
        val continuousListening = voicePreferences.getContinuousListeningEnabled()
        val readResponses = voicePreferences.isReadResponsesEnabled()
        
        _voiceState.value = _voiceState.value.copy(
            isVoiceEnabled = voiceEnabled,
            isWakeWordEnabled = wakeWordEnabled,
            isContinuousListeningEnabled = continuousListening,
            isReadResponsesEnabled = readResponses
        )
    }
    
    /**
     * 检查是否已初始化
     * @throws IllegalStateException 如果模块未初始化
     */
    private fun checkInitialized() {
        if (!isInitialized.get()) {
            throw IllegalStateException("Voice module is not initialized yet")
        }
    }
    
    /**
     * 开始语音监听
     * @param timeoutMs 超时时间，毫秒
     * @param wakeWordMode 是否使用唤醒词模式
     */
    suspend fun startListening(timeoutMs: Long = 10000, wakeWordMode: Boolean = false) {
        checkInitialized()
        
        if (wakeWordMode && _voiceState.value.isWakeWordEnabled) {
            // 使用唤醒词模式
            wakeWordDetector.startListening(timeoutMs = null) // 持续监听唤醒词
        } else {
            // 直接开始语音识别
            voiceRecognitionService.startListening(true)
            
            // 处理识别结果
            moduleScope.launch {
                voiceRecognitionService.recognitionResults
                    .filter { it.isNotEmpty() }
                    .collect { text ->
                        handleVoiceCommand(text)
                    }
            }
        }
    }
    
    /**
     * 停止语音监听
     */
    suspend fun stopListening() {
        checkInitialized()
        
        if (_voiceState.value.isWakeWordEnabled && _voiceState.value.isListening) {
            wakeWordDetector.stopListening()
        } else {
            voiceRecognitionService.stopListening()
        }
    }
    
    /**
     * 处理语音命令
     * @param text 识别的文本
     */
    private suspend fun handleVoiceCommand(text: String) {
        checkInitialized()
        
        // 更新状态
        _voiceState.value = _voiceState.value.copy(
            lastRecognizedText = text,
            lastRecognitionTime = System.currentTimeMillis()
        )
        
        // 使用命令处理器分析并执行命令
        val result = voiceCommandProcessor.processVoiceInput(text)
        
        if (result.wasCommand) {
            // 如果是命令，根据处理结果继续操作
            if (result.needsConfirmation) {
                // 需要用户确认的命令
                // 在实际应用中，这里可能需要启动UI交互请求确认
                val command = result.command
                if (command != null) {
                    speak("请确认您是否要${getCommandDescription(command)}？")
                }
            }
        } else {
            // 不是预定义命令，发送给AI处理
            handleWithAI(text)
        }
    }
    
    /**
     * 通过AI处理语音输入
     * @param text 要处理的文本
     */
    private suspend fun handleWithAI(text: String, chatId: String? = null) {
        try {
            // 向AI发送消息
            aiService.sendMessage(
                message = text,
                onPartialResponse = { content, thinking ->
                    handlePartialResponse(content, thinking)
                },
                chatHistory = _chatHistory.value,
                onComplete = { handleResponseComplete() },
                chatId = chatId
            )

            // 收集AI响应
            var responseText = ""

        } catch (e: Exception) {
            Log.e(TAG, "Error handling AI response: ${e.message}")
            _voiceState.value = _voiceState.value.copy(
                error = "AI响应处理失败: ${e.message}"
            )
        }
    }

    /** 处理AI响应完成 */
    private fun handleResponseComplete() {
        // 取消任何待处理的更新任务
        // TODO
    }

    /** 处理AI响应过程 */
    private fun handlePartialResponse(content: String, thinking: String?) {
        moduleScope.launch {
            try {
                if (_voiceState.value.isReadResponsesEnabled) {
                    when (voicePreferences.getReadResponseMode()) {
                        VoicePreferences.ReadResponseMode.FULL -> {
                            speak(content)
                        }
                        VoicePreferences.ReadResponseMode.SUMMARY -> {
                            // 这里可以添加逻辑来提取摘要
                            val summary = extractSummary(content)
                            speak(summary)
                        }
                        VoicePreferences.ReadResponseMode.SMART -> {
                            // 根据响应长度和内容决定如何朗读
                            if (content.length < 100) {
                                speak(content)
                            } else {
                                val summary = extractSummary(content)
                                speak(summary)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling AI response: ${e.message}")
                _voiceState.value = _voiceState.value.copy(
                    error = "处理AI响应时发生错误: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 提取文本摘要
     * 简单实现，实际应用中可能需要更复杂的算法
     * @param text 原始文本
     * @return 提取的摘要
     */
    private fun extractSummary(text: String): String {
        // 简单实现，取第一句话或前N个字符
        val firstSentence = text.split(Regex("[.!?。！？]"), 2).firstOrNull()
        return if (firstSentence != null && firstSentence.length < 100) {
            firstSentence
        } else {
            text.take(100) + "..."
        }
    }
    
    /**
     * 获取命令描述
     * @param command 语音命令
     * @return 命令的人类可读描述
     */
    private fun getCommandDescription(command: VoiceCommand): String {
        return when (command.action) {
            VoiceActionType.OPEN_APP -> {
                val appName = command.parameters["appName"] as? String ?: "应用"
                "打开$appName"
            }
            VoiceActionType.STOP_CURRENT_ACTION -> {
                "停止当前操作"
            }
            else -> {
                "执行此操作"
            }
        }
    }
    
    /**
     * 使用TTS朗读文本
     * @param text 要朗读的文本
     * @param interruptCurrent 是否中断当前朗读
     */
    suspend fun speak(text: String, interruptCurrent: Boolean = false) {
        checkInitialized()
        
        if (!_voiceState.value.isVoiceEnabled) {
            return
        }

        // IO线程中朗读
        withContext(Dispatchers.IO) {
            textToSpeechService.speak(text, interruptCurrent)
        }
    }
    
    /**
     * 停止语音朗读
     */
    fun stopSpeaking() {
        if (isInitialized.get()) {
            textToSpeechService.stopSpeaking()
        }
    }
    
    /**
     * 启用或禁用语音功能
     * @param enabled 是否启用
     */
    suspend fun setVoiceEnabled(enabled: Boolean) {
        voicePreferences.setVoiceEnabled(enabled)
        _voiceState.value = _voiceState.value.copy(isVoiceEnabled = enabled)
        
        // 如果禁用，确保停止所有活动
        if (!enabled) {
            stopListening()
            stopSpeaking()
        }
    }
    
    /**
     * 启用或禁用唤醒词功能
     * @param enabled 是否启用
     */
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        voicePreferences.setWakeWordEnabled(enabled)
        _voiceState.value = _voiceState.value.copy(isWakeWordEnabled = enabled)
    }
    
    /**
     * 开始连续对话模式
     */
    fun startContinuousConversation() {
        checkInitialized()
        
        if (!_voiceState.value.isContinuousListeningEnabled) {
            return
        }
        
        // 启动连续监听模式
        moduleScope.launch {
            voicePreferences.setContinuousListeningEnabled(true)
            _voiceState.value = _voiceState.value.copy(isContinuousListeningEnabled = true)
            
            startListening(timeoutMs = Long.MAX_VALUE)
        }
    }
    
    /**
     * 停止连续对话模式
     */
    fun stopContinuousConversation() {
        checkInitialized()
        
        moduleScope.launch {
            voicePreferences.setContinuousListeningEnabled(false)
            _voiceState.value = _voiceState.value.copy(isContinuousListeningEnabled = false)
            
            stopListening()
        }
    }
    
    /**
     * 获取当前语音配置文件
     */
    fun getCurrentVoiceProfile(): VoiceProfile? {
        return voiceProfileManager.currentProfile.value
    }
    
    /**
     * 设置当前语音配置文件
     * @param profileId 配置文件ID
     */
    fun setVoiceProfile(profileId: String): Boolean {
        return voiceProfileManager.switchProfile(profileId)
    }
    
    /**
     * 语音配置文件状态
     */
    data class VoiceState(
        val isInitialized: Boolean = false,
        val isVoiceEnabled: Boolean = false,
        val isWakeWordEnabled: Boolean = false,
        val isContinuousListeningEnabled: Boolean = false,
        val isReadResponsesEnabled: Boolean = true,
        val isListening: Boolean = false,
        val isSpeaking: Boolean = false,
        val recognitionConfidence: Float = 0.0f,
        val noiseLevel: Float = 0.0f,
        val lastRecognizedText: String = "",
        val lastWakeWord: String = "",
        val partialText: String = "",
        val lastRecognitionTime: Long = 0,
        val lastWakeTime: Long = 0,
        val lastCommand: VoiceCommand? = null,
        val error: String? = null
    )
    
    companion object {
        private const val TAG = "VoiceModule"
    }
} 