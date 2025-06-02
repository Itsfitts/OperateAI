package com.ai.assistance.operit.voice

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.data.preferences.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 唤醒词检测器
 * 负责监听和检测特定的唤醒词
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
    
    init {
        loadWakeWords()
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
     * 开始监听唤醒词
     * @param timeoutMs 超时时间，如果为null则持续监听
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun startListening(timeoutMs: Long? = null) {
        if (isListening.getAndSet(true)) {
            return
        }
        
        detectorScope.launch {
            try {
                _wakeEvents.emit(WakeEvent.ListeningStarted)
                
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
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection: ${e.message}")
                _wakeEvents.emit(WakeEvent.Error("唤醒词检测错误: ${e.message}"))
                stopListening()
            }
        }
    }
    
    /**
     * 停止监听唤醒词
     */
    suspend fun stopListening() {
        if (!isListening.getAndSet(false)) {
            return
        }
        
        try {
            // voiceRecognitionService.stopRecognition()
            _wakeEvents.emit(WakeEvent.ListeningStopped)
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