package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.abs

/**
 * 语音活动检测器
 * 使用能量检测和简单的状态机来检测音频中的语音活动
 */
class VoiceActivityDetector(
    private val context: Context,
    private val audioStreamManager: AudioStreamManager
) {
    companion object {
        private const val TAG = "VoiceActivityDetector"
        private const val SAMPLE_RATE = 16000 // 16kHz，适合语音识别
        private const val FRAME_SIZE = 480 // 30ms @ 16kHz
        private const val BUFFER_SIZE = FRAME_SIZE * 2 // 16-bit PCM
        private const val ENERGY_THRESHOLD = 500.0 // 能量阈值，大于此值视为有声音
        private const val HIGH_ENERGY_THRESHOLD = 1500.0 // 高能量阈值，用于确认语音
        private const val SPEECH_TIMEOUT_MS = 1000L // 语音超时时间（静音判定）
        private const val MIN_SPEECH_DURATION_MS = 300L // 最小语音持续时间
        private const val SPEECH_PROB_THRESHOLD = 0.5 // 语音概率阈值
    }
    
    // 语音活动状态
    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()
    
    // 噪声级别
    private val _noiseLevel = MutableStateFlow(0f)
    val noiseLevel: StateFlow<Float> = _noiseLevel.asStateFlow()
    
    // 是否正在运行
    private val isRunning = AtomicBoolean(false)
    
    // 语音缓冲区
    private val speechBuffer = ArrayList<ShortArray>()
    
    // 最后一次检测到语音的时间
    private var lastSpeechTime = 0L
    
    // 语音开始时间
    private var speechStartTime = 0L
    
    // 历史能量水平，用于计算动态阈值
    private val energyHistory = ArrayList<Double>(30)
    private var backgroundEnergy = 0.0
    
    // 协程作用域
    private val vadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vadJob: Job? = null
    
    /**
     * 语音状态枚举
     */
    enum class SpeechState {
        IDLE,           // 空闲状态，没有检测到语音
        SPEECH_DETECTED, // 检测到语音
        SPEECH_PROCESSING // 语音处理中
    }
    
    /**
     * 语音事件数据类
     */
    data class SpeechEvent(
        val state: SpeechState,
        val audioData: ShortArray? = null
    )
    
    /**
     * 初始化VAD
     */
    fun initialize() {
        try {
            // 重置能量历史和背景能量估计
            energyHistory.clear()
            backgroundEnergy = 0.0
            
            Log.d(TAG, "VAD initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VAD: ${e.message}")
        }
    }
    
    /**
     * 开始VAD监听
     */
    fun startListening() {
        if (isRunning.getAndSet(true)) {
            return
        }
        
        initialize()
        
        vadJob = vadScope.launch {
            try {
                val buffer = ShortArray(FRAME_SIZE)
                speechBuffer.clear()
                _speechState.value = SpeechState.IDLE
                lastSpeechTime = 0L
                speechStartTime = 0L
                
                // 开始音频捕获
                val audioRecord = audioStreamManager.getAudioRecord()
                
                if (audioRecord != null) {
                    Log.d(TAG, "Starting VAD listening loop")
                    
                    while (isRunning.get()) {
                        // 读取音频数据
                        val readResult = audioRecord.read(buffer, 0, buffer.size)
                        
                        if (readResult > 0) {
                            // 处理音频帧
                            processAudioFrame(buffer)
                        }
                        
                        // 短暂延迟，减少CPU使用
                        delay(10)
                    }
                } else {
                    Log.e(TAG, "Failed to get AudioRecord instance")
                    isRunning.set(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in VAD listening loop: ${e.message}")
                isRunning.set(false)
            }
        }
    }
    
    /**
     * 处理音频帧
     */
    private suspend fun processAudioFrame(buffer: ShortArray) {
        try {
            // 计算音频能量
            val energy = calculateEnergy(buffer)
            updateNoiseLevel(energy)
            
            // 更新背景能量估计
            updateEnergyHistory(energy)
            
            // 计算动态阈值
            val threshold = calculateDynamicThreshold()
            
            // 检测语音活动
            val hasSpeech = energy > threshold
            
            // 处理语音状态
            when (_speechState.value) {
                SpeechState.IDLE -> {
                    if (hasSpeech) {
                        // 检测到语音开始
                        _speechState.value = SpeechState.SPEECH_DETECTED
                        speechStartTime = System.currentTimeMillis()
                        lastSpeechTime = speechStartTime
                        speechBuffer.clear()
                        speechBuffer.add(buffer.clone())
                        Log.d(TAG, "Speech started")
                    }
                }
                SpeechState.SPEECH_DETECTED -> {
                    if (hasSpeech) {
                        // 继续收集语音
                        lastSpeechTime = System.currentTimeMillis()
                        speechBuffer.add(buffer.clone())
                    } else {
                        // 检查是否超过语音超时时间
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSpeechTime > SPEECH_TIMEOUT_MS) {
                            // 语音结束，检查语音是否足够长
                            if (currentTime - speechStartTime >= MIN_SPEECH_DURATION_MS && 
                                speechBuffer.size > 5) { // 至少有5帧
                                _speechState.value = SpeechState.SPEECH_PROCESSING
                                // 处理收集到的语音
                                processSpeech()
                            } else {
                                // 语音太短，重置状态
                                _speechState.value = SpeechState.IDLE
                                speechBuffer.clear()
                                Log.d(TAG, "Speech too short, discarded")
                            }
                        }
                    }
                }
                SpeechState.SPEECH_PROCESSING -> {
                    // 等待处理完成，不做任何操作
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio frame: ${e.message}")
        }
    }
    
    /**
     * 更新能量历史并估计背景能量
     */
    private fun updateEnergyHistory(energy: Double) {
        // 限制历史大小
        if (energyHistory.size >= 30) {
            energyHistory.removeAt(0)
        }
        
        energyHistory.add(energy)
        
        // 使用较低的能量值估计背景噪音
        if (energyHistory.size >= 10) {
            val sortedEnergies = energyHistory.sorted()
            // 使用最低的20%能量作为背景噪音估计
            val lowEnergyCount = (energyHistory.size * 0.2).toInt().coerceAtLeast(1)
            backgroundEnergy = sortedEnergies.take(lowEnergyCount).average()
        }
    }
    
    /**
     * 计算动态能量阈值
     */
    private fun calculateDynamicThreshold(): Double {
        // 根据背景噪音计算阈值，确保有足够的信噪比
        return backgroundEnergy * 3.0 + ENERGY_THRESHOLD
    }
    
    /**
     * 应用简单的噪声抑制
     */
    private fun applyNoiseSuppression(buffer: ShortArray): ShortArray {
        try {
            // 实现简单的噪声抑制 - 低于背景噪音的部分设置为0
            val output = buffer.clone()
            val noiseFloor = Math.sqrt(backgroundEnergy).toInt()
            
            for (i in output.indices) {
                if (abs(output[i].toInt()) < noiseFloor) {
                    output[i] = 0
                }
            }
            
            return output
        } catch (e: Exception) {
            Log.e(TAG, "Error applying noise suppression: ${e.message}")
            return buffer
        }
    }
    
    /**
     * 处理收集到的语音
     */
    private suspend fun processSpeech() {
        vadScope.launch {
            try {
                Log.d(TAG, "Processing speech with ${speechBuffer.size} frames")
                
                // 将收集到的语音数据转换为单个数组
                val combinedSpeech = combineAudioBuffers(speechBuffer)
                
                // 通知收集到有效语音数据
                onSpeechDetected(combinedSpeech)
                
                // 重置状态
                speechBuffer.clear()
                _speechState.value = SpeechState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech: ${e.message}")
                _speechState.value = SpeechState.IDLE
            }
        }
    }
    
    /**
     * 处理静音
     */
    private suspend fun handleSilence() {
        if (_speechState.value == SpeechState.SPEECH_DETECTED) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSpeechTime > SPEECH_TIMEOUT_MS) {
                // 检查语音是否足够长
                if (currentTime - speechStartTime >= MIN_SPEECH_DURATION_MS && 
                    speechBuffer.size > 5) {
                    _speechState.value = SpeechState.SPEECH_PROCESSING
                    processSpeech()
                } else {
                    // 语音太短，重置状态
                    _speechState.value = SpeechState.IDLE
                    speechBuffer.clear()
                }
            }
        }
    }
    
    /**
     * 停止VAD监听
     */
    fun stopListening() {
        if (!isRunning.getAndSet(false)) {
            return
        }
        
        vadJob?.cancel()
        vadJob = null
        speechBuffer.clear()
        _speechState.value = SpeechState.IDLE
    }
    
    /**
     * 计算音频能量
     */
    private fun calculateEnergy(buffer: ShortArray): Double {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        return sum / buffer.size
    }
    
    /**
     * 更新噪声级别
     */
    private fun updateNoiseLevel(energy: Double) {
        // 将能量转换为分贝
        val db = if (energy > 0) 10 * log10(energy) else 0.0
        // 标准化到0-1范围
        val normalizedDb = (db / 60.0).coerceIn(0.0, 1.0)
        _noiseLevel.value = normalizedDb.toFloat()
    }
    
    /**
     * 合并音频缓冲区
     */
    private fun combineAudioBuffers(buffers: List<ShortArray>): ShortArray {
        val totalSize = buffers.sumOf { it.size }
        val result = ShortArray(totalSize)
        var offset = 0
        
        for (buffer in buffers) {
            buffer.copyInto(result, offset)
            offset += buffer.size
        }
        
        return result
    }
    
    /**
     * 当检测到语音时调用
     */
    private suspend fun onSpeechDetected(speechData: ShortArray) {
        // 这里实现检测到语音后的逻辑
        Log.d(TAG, "Speech detected with ${speechData.size} samples")
        
        // 可以在这里实现回调通知其他组件
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        energyHistory.clear()
    }
} 