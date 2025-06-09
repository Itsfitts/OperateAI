package com.ai.assistance.operit.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.VoiceActionType
import com.ai.assistance.operit.data.model.VoiceCommand
import com.ai.assistance.operit.data.model.VoiceProfile
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.data.preferences.VoicePreferences.ReadResponseMode
import com.ai.assistance.operit.ui.features.chat.viewmodel.TokenStatisticsDelegate
import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import com.ai.assistance.operit.ui.features.voice.VoiceAssistantViewModel.UiState
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.voice.WakeWordDetector.WakeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音模块主类
 * 负责协调各个语音组件，提供统一接口
 */
class VoiceModule(
    private val context: Context,
    private var aiService: EnhancedAIService,
    private val aiToolHandler: AIToolHandler,
    private val uiVoiceStateFlow: StateFlow<UiState>,
    private val uiStateDelegate: UiStateDelegate
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
    private lateinit var voiceHistoryDelegate: VoiceHistoryDelegate
    private lateinit var textSummarizer: TextSummarizer

    // 服务收集器设置状态跟踪
    private var serviceCollectorSetupComplete = false
    private val tokenStatsDelegate =
        TokenStatisticsDelegate(
            getEnhancedAiService = { aiService },
            updateUiStatistics = { contextSize, inputTokens, outputTokens ->
                uiStateDelegate.updateChatStatistics(contextSize, inputTokens, outputTokens)
            }
        )

    // 协程作用域
    private val moduleScope = CoroutineScope(Dispatchers.Default + Job())

    // 状态追踪
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val voiceHistory: StateFlow<List<ChatMessage>> by lazy { voiceHistoryDelegate.voiceHistory }

    // 用于跟踪模块是否初始化完成
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

    // 上一条AI消息的引用，用于直接修改而不是创建新对象
    private var lastAiMessage: ChatMessage? = null
    private var batchedAiContent: String = ""
    private var lastAiUpdateTime: Long = 0
    private var updateJob: Job? = null
    private var readType: ReadResponseMode = ReadResponseMode.FULL

    private var speakTimestamp: Long = 0
    private var recognitionJob: Job? = null

    init {
        // 初始化仅依赖于context的组件
        initializeModule()
    }

    /**
     * 初始化模块
     */
    private fun initializeModule() {
        if (!isInitializing.compareAndSet(false, true)) {
            return
        }

        try {
            // TODO 暂时使用ChatHistoryDelegate
            voiceHistoryDelegate =
                VoiceHistoryDelegate(
                    context = context,
                    voiceModelScope = moduleScope,
                    onTokenStatisticsLoaded = { inputTokens: Int, outputTokens: Int ->
                        tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens)
                    },
                    getEnhancedAiService = { aiService },
                    getTokenCounts = { tokenStatsDelegate.getCurrentTokenCounts() }
                )

            voiceRecognitionService = VoiceRecognitionService(
                context,
                voicePreferences,
                audioStreamManager,
                noiseSuppressionManager,
                aiService
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
                moduleScope,
                voiceHistory,
                aiToolHandler
            )

            wakeWordDetector = WakeWordDetector(
                context,
                voicePreferences,
                audioStreamManager,
                voiceRecognitionService
            )

            textSummarizer = TextSummarizer(context)

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
    private fun loadSettings() {
        moduleScope.launch {
            try {
                // 获取语音启用状态
                val voiceEnabled = voicePreferences.isVoiceEnabled()
                
                // 获取唤醒词启用状态
                val wakeWordEnabled = voicePreferences.isWakeWordEnabled()
                
                // 获取连续监听状态
                val continuousListening = voicePreferences.getContinuousListeningEnabled()

                // 获取响应阅读设置
                val readResponses = _voiceState.value.isReadResponsesEnabled

                // 获取VAD模式设置
                val vadModeEnabled = voicePreferences.isVadModeEnabled()
                
                // 设置唤醒词检测器的VAD模式
                wakeWordDetector.setVadMode(vadModeEnabled)
                
                // 更新状态
                _voiceState.value = _voiceState.value.copy(
                    isVoiceEnabled = voiceEnabled,
                    isWakeWordEnabled = wakeWordEnabled,
                    isContinuousListeningEnabled = continuousListening,
                    isReadResponsesEnabled = readResponses,
                    isVadModeEnabled = vadModeEnabled
                )

                Log.d(TAG, "Voice settings loaded: voice=$voiceEnabled, wakeWord=$wakeWordEnabled, " +
                        "continuous=$continuousListening, readResponses=$readResponses, vadMode=$vadModeEnabled")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading voice settings: ${e.message}")
            }
        }
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
     * @param constantListeningMode 是否持续监听
     */
    fun startListening(timeoutMs: Long = SPEAK_INTERVAL, constantListeningMode: Boolean = false) {
        checkInitialized()
        val currentTime = System.currentTimeMillis()
        if (recognitionJob == null || currentTime - speakTimestamp >= timeoutMs) {
            cancelJob()

            recognitionJob = moduleScope.launch {
                voiceRecognitionService.setRecognitionProvider(
                    VoiceRecognitionService.RecognitionProvider.FUN_AUDIO_LLM)
                // 直接开始语音识别
                voiceRecognitionService.startListening(constantListeningMode)
                voicePreferences.setReadResponseMode(ReadResponseMode.SMART)
                speakTimestamp = currentTime

                // 开始收集噪音级别数据
                moduleScope.launch {
                    voiceRecognitionService.inputState
                        .collect { inputState ->
                            // 更新状态，确保噪音水平数据被传递
                            _voiceState.value = _voiceState.value.copy(
                                isListening = inputState.isListening,
                                partialText = inputState.partialResult,
                                noiseLevel = inputState.noiseLevel,
                                recognitionConfidence = inputState.recognitionConfidence
                            )
                        }
                }

                voiceRecognitionService.recognitionResults
                    .distinctUntilChanged()  // 只有当值改变时才发射
                    .buffer(capacity = Channel.CONFLATED) // 只保留最新值
                    .collect { text ->
                        if (text.isNotEmpty()) {
                            handleVoiceCommand(text)
                        }
                    }
            }
        }
    }

    /**
     * 停止语音监听
     */
    fun stopListening() {
        checkInitialized()
        cancelJob()
        recognitionJob = null
        speakTimestamp = 0

        if (_voiceState.value.isWakeWordEnabled && _voiceState.value.isListening) {
            wakeWordDetector.stopListening()
        } else if (_voiceState.value.isContinuousListeningEnabled) {
            stopContinuousConversation()
        } else {
            voiceRecognitionService.stopListening()
        }
    }

    /**
     * 处理语音命令
     * @param text 识别的文本
     */
    private suspend fun handleVoiceCommand(text: String) {
        // 更新状态
        _voiceState.value = _voiceState.value.copy(
            lastRecognizedText = text,
            lastRecognitionTime = System.currentTimeMillis()
        )

        // 使用命令处理器分析并执行命令
        val result = voiceCommandProcessor.processVoiceInput(text)

        if (!result.wasCommand) {
            handleWithAI(text)
        }
    }

    /**
     * 通过AI处理语音输入
     * @param text 要处理的文本
     */
    private suspend fun handleWithAI(text: String, chatId: String? = null) {
        try {
            // 检查网络连接
            if (!NetworkUtils.isNetworkAvailable(context)) {
                uiStateDelegate.showErrorMessage("网络连接不可用，请检查网络设置")
                return
            }

            // 添加用户消息到聊天历史
            voiceHistoryDelegate.addMessageToChat(ChatMessage(sender = "user", content = text))
            _voiceState.value = _voiceState.value.copy(isProcessing = true)

            // if (!textToSpeechService.isSpeaking.value) {
            //     speak("响应处理中 请稍后", true)
            // }

            // 向AI发送消息
            aiService.sendMessage(
                message = "用户通过语音说：\"$text\"。" +
                    "如果这是一个问题或闲聊，请正常回答；" +
                    "如果你不确定，请询问用户以获得更多信息。" +
                    "请将最后回答转化成适合TTS阅读的文本。",
                onPartialResponse = { content, thinking ->
                    handlePartialResponse(content, thinking)
                },
                chatHistory = voiceHistoryDelegate.getMemory(),
                onComplete = { handleResponseComplete() },
                functionType = FunctionType.VOICE,
                chatId = chatId
            )

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
        updateJob?.cancel()
        updateJob = null

        try {
            // 先全量阅读吧
            if (batchedAiContent.isNotEmpty()) {
                handleReadResponseMode(batchedAiContent)
                appendAiContent(batchedAiContent)
                batchedAiContent = ""
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理响应完成时发生错误", e)
        } finally {
            lastAiUpdateTime = 0
            lastAiMessage = null // 重置消息引用
            _voiceState.value = _voiceState.value.copy(isProcessing = false)
        }
    }

    private var lastThinking: String? = null

    /** 处理AI响应过程 */
    private fun handlePartialResponse(content: String, thinking: String?) {
        if (!_voiceState.value.isProcessing) {
            Log.d(TAG, "已取消加载，跳过响应处理")
            return
        }

        if (thinking != null  && thinking != lastThinking) {
            lastThinking = thinking
            // 更新或添加思考消息

            val voiceHistory = voiceHistoryDelegate.voiceHistory.value
            val lastUserIndex = voiceHistory.indexOfLast { it.sender == "user" }
            val thinkIndex = voiceHistory.indexOfLast { it.sender == "think" }

            if (thinkIndex >= 0 && thinkIndex > lastUserIndex) {
                // 已有思考消息，更新它
                val updatedThinkMessage = ChatMessage("think", thinking)
                voiceHistoryDelegate.addMessageToChat(updatedThinkMessage)
            } else {
                // 添加新的思考消息
                voiceHistoryDelegate.addMessageToChat(ChatMessage("think", thinking))
            }
        }

        if (content.isNotEmpty()) {
            // 获取当前时间
            val currentTime = System.currentTimeMillis()

            if (updateJob == null || currentTime - lastAiUpdateTime >= TTS_UPDATE_INTERVAL) {
                updateJob?.cancel()

                updateJob =
                    moduleScope.launch {
                        // 获取阅读方式
                        readType = voicePreferences.getReadResponseMode()

                        // 更新最后更新时间
                        lastAiUpdateTime = System.currentTimeMillis()

                        appendAiContent(content)

                        // 等待下一个更新间隔
                        delay(TTS_UPDATE_INTERVAL)

                        // 任务完成
                        updateJob = null
                    }
            } else {
                batchedAiContent = content // 直接替换为最新内容
            }
        }
    }

    /** 追加AI内容到消息 */
    private fun appendAiContent(newContent: String) {
        // 如果没有内容，直接返回
        if (newContent.isEmpty()) return

        // 获取当前聊天历史
        val chatHistory = voiceHistoryDelegate.voiceHistory.value
        val lastUserIndex = chatHistory.indexOfLast { it.sender == "user" }

        val contentToUse =
            if (batchedAiContent.isNotEmpty()) {
                batchedAiContent = ""
                newContent
            } else {
                newContent
            }

        if (lastAiMessage != null) {
            val newMessage =
                ChatMessage(
                    sender = "ai",
                    content = contentToUse,
                    timestamp = lastAiMessage?.timestamp ?: System.currentTimeMillis()
                )
            lastAiMessage = newMessage
            voiceHistoryDelegate.addMessageToChat(newMessage)
        } else {
            val lastAiIndex = chatHistory.indexOfLast { it.sender == "ai" }
            if (lastAiIndex > lastUserIndex && lastAiIndex >= 0) {
                val existingMessage = chatHistory[lastAiIndex]
                val newMessage =
                    ChatMessage(
                        sender = "ai",
                        content = contentToUse,
                        timestamp = existingMessage.timestamp
                    )
                lastAiMessage = newMessage
                voiceHistoryDelegate.addMessageToChat(newMessage)
            } else {
                val newMessage = ChatMessage("ai", contentToUse)
                lastAiMessage = newMessage
                voiceHistoryDelegate.addMessageToChat(newMessage)
            }
        }
    }


    /**
     * 根据阅读模式来匹配不同的阅读方式
     *
     * @param text 原始文本
     */
    private fun handleReadResponseMode(content: String) {
        when (readType) {
            ReadResponseMode.FULL -> {
                speak(content)
            }

            ReadResponseMode.SUMMARY -> {
                val summary = textSummarizer.process(content)
                speak(summary)
            }

            ReadResponseMode.SMART -> {
                // 根据响应长度和内容决定如何朗读
                val processText = textSummarizer.process(content)
                speak(processText)
            }

            ReadResponseMode.STREAMING -> {
                textToSpeechService.handleStreamingText(content)
            }
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
    fun speak(text: String, interruptCurrent: Boolean = false) {
        if (!_voiceState.value.isVoiceEnabled) {
            return
        }

        textToSpeechService.speak(text, interruptCurrent)
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
     * 启用或禁用持续监听功能
     * @param enabled 是否启用
     */
    fun setConstantListeningEnabled(enabled: Boolean) {
        voiceRecognitionService.setContinuousListening(enabled)
        _voiceState.value = _voiceState.value.copy(isContinuousListeningEnabled = enabled)
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

            startListening(timeoutMs = Long.MAX_VALUE, true)
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

    private fun cancelJob() {
        recognitionJob?.cancel()
        textToSpeechService.stopSpeaking()
        // cancelCurrentMessage()
    }

    /** 取消当前对话 */
    private fun cancelCurrentMessage() {
        moduleScope.launch {
            // 取消任何待处理的更新任务
            updateJob?.cancel()

            // 重置批处理变量
            batchedAiContent = ""
            lastAiUpdateTime = 0
            lastAiMessage = null

            _voiceState.value = _voiceState.value.copy(isProcessing = false)

            // 取消当前的AI响应
            try {
                aiService.cancelConversation()
                Log.d(TAG, "成功取消AI对话")
            } catch (e: Exception) {
                Log.e(TAG, "取消对话时发生错误", e)
                uiStateDelegate.showErrorMessage("取消对话时发生错误: ${e.message}")
            }

            Log.d(TAG, "取消流程完成")
        }
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
        val isProcessing: Boolean = false,
        val isSpeaking: Boolean = false,
        val recognitionConfidence: Float = 0.0f,
        val noiseLevel: Float = 0.0f,
        val lastRecognizedText: String = "",
        val lastWakeWord: String = "",
        val partialText: String = "",
        val lastRecognitionTime: Long = 0,
        val lastWakeTime: Long = 0,
        val lastCommand: VoiceCommand? = null,
        val isVadModeEnabled: Boolean = true, // 默认启用VAD模式
        val error: String? = null
    )
    
    /**
     * 设置VAD模式启用状态
     * @param enabled 是否启用VAD模式
     */
    suspend fun setVadModeEnabled(enabled: Boolean) {
        voicePreferences.setVadModeEnabled(enabled)
        
        // 设置唤醒词检测器的VAD模式
        wakeWordDetector.setVadMode(enabled)
        
        // 更新状态
        _voiceState.value = _voiceState.value.copy(isVadModeEnabled = enabled)
        
        Log.d(TAG, "VAD mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    companion object {
        private const val TAG = "VoiceModule"
        private const val TTS_UPDATE_INTERVAL = 500L // 0.5 seconds in milliseconds
        private const val SPEAK_INTERVAL = 500L // 0.5 seconds in milliseconds
    }
} 