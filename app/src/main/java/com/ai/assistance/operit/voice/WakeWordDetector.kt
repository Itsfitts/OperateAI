package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.preferences.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 唤醒词检测器
 * 负责监听和检测特定的唤醒词
 * 使用VAD技术优化语音检测流程，减少资源消耗
 */
class WakeWordDetector(
    private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val audioStreamManager: AudioStreamManager,
    private val voiceRecognitionService: VoiceRecognitionService
) {
    // 协程作用域
    private val detectorScope = CoroutineScope(Dispatchers.Default + Job())
    
    // 唤醒事件流
    private val _wakeEvents = MutableSharedFlow<WakeEvent>()
    val wakeEvents: Flow<WakeEvent> = _wakeEvents
    
    // 当前是否正在监听
    private val isListening = AtomicBoolean(false)
    
    // 默认唤醒词
    private var defaultWakeWord = "你好助手"
    
    // 当前使用的唤醒词
    private var currentWakeWords = mutableSetOf<String>()
    
    // VAD检测器
    private val voiceActivityDetector by lazy {
        VoiceActivityDetector(context, audioStreamManager)
    }
    
    // 是否使用VAD模式
    private var useVadMode = true
    
    init {
        loadWakeWords()
        setupVadListener()
    }
    
    /**
     * 从偏好设置加载唤醒词
     */
    private fun loadWakeWords() {
        currentWakeWords.clear()
        
        // 添加默认唤醒词
        currentWakeWords.add(defaultWakeWord)

        detectorScope.launch {
            // 从用户偏好加载自定义唤醒词
            val customWakeWords = voicePreferences.getCustomWakeWords()
            if (customWakeWords.isNotEmpty()) {
                currentWakeWords.addAll(customWakeWords)
            }
        }
    }
    
    /**
     * 设置VAD监听器
     */
    private fun setupVadListener() {
        detectorScope.launch {
            voiceActivityDetector.speechState.collect { state ->
                when (state) {
                    VoiceActivityDetector.SpeechState.SPEECH_DETECTED -> {
                        // 检测到语音活动，但尚未处理
                        Log.d(TAG, "VAD: 检测到语音活动")
                    }
                    VoiceActivityDetector.SpeechState.SPEECH_PROCESSING -> {
                        // VAD确认检测到有效语音，启动语音识别
                        Log.d(TAG, "VAD: 启动语音识别处理")
                        
                        // 只有在检测到有效语音时才启动语音识别
                        if (isListening.get() && useVadMode) {
                            // 短暂启动语音识别，带超时，避免长时间运行
                            startRecognitionAfterVad()
                        }
                    }
                    VoiceActivityDetector.SpeechState.IDLE -> {
                        // 空闲状态，无需操作
                    }
                }
            }
        }
    }
    
    /**
     * VAD检测到语音后启动语音识别
     */
    private fun startRecognitionAfterVad() {
        detectorScope.launch {
            try {
                // 启动一次性语音识别，不连续
                voiceRecognitionService.startListening(false)
                
                // 收集识别结果
                voiceRecognitionService.recognitionResults
                    .filter { it.isNotEmpty() }
                    .collect { text ->
                        // 检查是否包含唤醒词
                        val detectedWakeWord = checkForWakeWord(text)
                        if (detectedWakeWord != null) {
                            _wakeEvents.emit(WakeEvent.WakeWordDetected(detectedWakeWord))

                            // 获取实际命令（去除唤醒词）
                            val command = extractCommand(text, detectedWakeWord)
                            if (command.isNotBlank()) {
                                _wakeEvents.emit(WakeEvent.CommandAfterWake(command))
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recognition after VAD: ${e.message}")
            }
        }
    }
    
    /**
     * 添加唤醒词
     * @param wakeWord 要添加的唤醒词
     */
    fun addWakeWord(wakeWord: String) {
        if (wakeWord.isNotBlank() && wakeWord.length >= 2) {
            currentWakeWords.add(wakeWord.trim())
            detectorScope.launch {
                voicePreferences.saveCustomWakeWords(currentWakeWords.toList())
            }
        }
    }
    
    /**
     * 移除唤醒词
     * @param wakeWord 要移除的唤醒词
     */
    fun removeWakeWord(wakeWord: String) {
        if (wakeWord != defaultWakeWord) {
            currentWakeWords.remove(wakeWord)
            detectorScope.launch {
                voicePreferences.saveCustomWakeWords(currentWakeWords.filter { it != defaultWakeWord })
            }
        }
    }
    
    /**
     * 获取所有唤醒词
     * @return 唤醒词列表
     */
    fun getAllWakeWords(): List<String> {
        return currentWakeWords.toList()
    }
    
    /**
     * 设置是否使用VAD模式
     * @param enable 是否启用
     */
    fun setVadMode(enable: Boolean) {
        useVadMode = enable
    }
    
    /**
     * 开始监听唤醒词
     * @param timeoutMs 超时时间，如果为null则持续监听
     */
    fun startListening(timeoutMs: Long? = null) {
        if (isListening.getAndSet(true)) {
            return
        }

        detectorScope.launch {
            try {
                _wakeEvents.emit(WakeEvent.ListeningStarted)

                if (useVadMode) {
                    // 使用VAD模式
                    startVadMode()
                } else {
                    // 使用传统模式（直接启动语音识别）
                    startTraditionalMode(timeoutMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection: ${e.message}")
                _wakeEvents.emit(WakeEvent.Error("唤醒词检测错误: ${e.message}"))
                stopListening()
            }
        }
    }
    
    /**
     * 启动VAD模式
     */
    private fun startVadMode() {
        // 启动VAD检测器
        voiceActivityDetector.initialize()
        voiceActivityDetector.startListening()
        
        Log.d(TAG, "VAD模式启动")
    }
    
    /**
     * 启动传统模式
     */
    private suspend fun startTraditionalMode(timeoutMs: Long? = null) {
        withTimeoutOrNull(timeoutMs ?: Long.MAX_VALUE) {
            // 使用语音识别服务进行连续监听
            voiceRecognitionService.startListening(true)

            // 订阅识别结果
            voiceRecognitionService.recognitionResults
                .filter { it.isNotEmpty() }
                .collect { text ->
                    // 检查是否包含唤醒词
                    val detectedWakeWord = checkForWakeWord(text)
                    if (detectedWakeWord != null) {
                        _wakeEvents.emit(WakeEvent.WakeWordDetected(detectedWakeWord))

                        // 获取实际命令（去除唤醒词）
                        val command = extractCommand(text, detectedWakeWord)
                        if (command.isNotBlank()) {
                            _wakeEvents.emit(WakeEvent.CommandAfterWake(command))
                        }
                    }
                }
        }

        if (isListening.get()) {
            stopListening()
        }
    }
    
    /**
     * 停止监听唤醒词
     */
    fun stopListening() {
        if (!isListening.getAndSet(false)) {
            return
        }
        
        try {
            // 停止VAD
            if (useVadMode) {
                voiceActivityDetector.stopListening()
            }
            
            // 停止语音识别
            voiceRecognitionService.stopListening()
            
            // 发送停止事件
            _wakeEvents.tryEmit(WakeEvent.ListeningStopped) // 先尝试非挂起方式
                .takeIf { !it }        // 如果失败（返回 false）
                ?.run {                // 则启动协程挂起 emit
                    CoroutineScope(Dispatchers.Default).launch {
                        _wakeEvents.emit(WakeEvent.ListeningStopped)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }
    
    /**
     * 检查文本中是否包含唤醒词
     * @param text 要检查的文本
     * @return 检测到的唤醒词，如果没有则返回null
     */
    private fun checkForWakeWord(text: String): String? {
        val lowerText = text.lowercase(Locale.getDefault())
        
        for (wakeWord in currentWakeWords) {
            val lowerWakeWord = wakeWord.lowercase(Locale.getDefault())
            if (lowerText.contains(lowerWakeWord)) {
                return wakeWord
            }
        }
        
        return null
    }
    
    /**
     * 从文本中提取命令(去除唤醒词)
     * @param text 完整文本
     * @param wakeWord 检测到的唤醒词
     * @return 提取的命令
     */
    private fun extractCommand(text: String, wakeWord: String): String {
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerWakeWord = wakeWord.lowercase(Locale.getDefault())
        
        val wakeWordIndex = lowerText.indexOf(lowerWakeWord)
        if (wakeWordIndex >= 0) {
            val afterWakeWord = text.substring(wakeWordIndex + wakeWord.length).trim()
            if (afterWakeWord.isNotEmpty()) {
                return afterWakeWord
            }
        }
        
        return ""
    }
    
    /**
     * 唤醒事件
     */
    sealed class WakeEvent {
        data object ListeningStarted : WakeEvent()
        data object ListeningStopped : WakeEvent()
        data class WakeWordDetected(val wakeWord: String) : WakeEvent()
        data class CommandAfterWake(val command: String) : WakeEvent()
        data class Error(val message: String) : WakeEvent()
    }
    
    companion object {
        private const val TAG = "WakeWordDetector"
    }
} 