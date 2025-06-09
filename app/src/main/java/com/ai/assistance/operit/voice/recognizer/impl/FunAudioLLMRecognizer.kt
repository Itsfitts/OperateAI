package com.ai.assistance.operit.voice.recognizer.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.voice.AudioStreamManager
import com.ai.assistance.operit.voice.NoiseSuppressionManager
import com.ai.assistance.operit.voice.VoiceRecognitionService
import com.ai.assistance.operit.voice.recognizer.VoiceRecognizer
import com.ai.assistance.operit.voice.recognizer.model.FunAudioLLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FunAudioLLM语音识别器
 * 使用FunAudioLLM API进行语音识别
 */
class FunAudioLLMRecognizer(
    private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val audioStreamManager: AudioStreamManager,
    private val noiseSuppressionManager: NoiseSuppressionManager,
    private val enhancedAIService: EnhancedAIService

) : VoiceRecognizer {
    companion object {
        private const val TAG = "FunAudioLLMRecognizer"
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val RECOGNITION_INTERVAL_MS = 300L // 识别间隔，毫秒
        private const val SILENCE_THRESHOLD_MS = 800L // 静音阈值，毫秒
        private const val MIN_AUDIO_SIZE = SAMPLE_RATE / 1 // 最小音频大小阈值 (1秒)
    }

    // 部分识别结果
    private val partialResults = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    // FunAudioLLM模型管理器 - 现在使用API调用方式
    private val funAudioLLMManager = FunAudioLLMManager.getInstance(
        context, enhancedAIService, partialResults)
    
    // 识别结果
    private val recognitionResults = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 错误
    private val errors = MutableSharedFlow<VoiceRecognitionService.RecognitionError>()
    
    // 协程作用域
    private val recognizerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 识别任务
    private var recognitionJob: Job? = null
    
    // 是否正在监听
    private val isListening = AtomicBoolean(false)
    
    // 是否是连续模式
    private var continuousMode = false
    
    // 上次检测到声音的时间
    private var lastSoundTime = 0L
    
    // 当前累积的音频数据
    private val audioBuffer = ByteArrayOutputStream()
    
    // 模型是否已初始化
    private var isModelInitialized = false
    
    // 是否检测到有效语音
    private var hasDetectedSpeech = false

    // 缓存识别结果
    private var cacheVoiceText = StringBuilder()
    
    // 连续检测到人声的次数
    private var consecutiveVoiceDetections = 0
    
    // 连续未检测到人声的次数
    private var consecutiveSilenceDetections = 0
    
    init {
        // 在初始化时异步加载模型
        recognizerScope.launch {
            isModelInitialized = funAudioLLMManager.initializeModel()
            if (!isModelInitialized) {
                Log.e(TAG, "Failed to initialize FunAudioLLM API service")
                errors.emit(VoiceRecognitionService.RecognitionError.ServiceNotAvailable)
            } else {
                Log.d(TAG, "FunAudioLLM API service initialized successfully")
            }
        }
        
        // 订阅partialResults，将结果转发到recognitionResults
        partialResults.onEach { result ->
            if (hasDetectedSpeech) {
                cacheVoiceText.append(result).append(" ")
            }
        }.launchIn(recognizerScope)
        
        Log.d(TAG, "FunAudioLLMRecognizer初始化完成")
    }
    
    override suspend fun startRecognition(continuous: Boolean, languageOverride: String?) = withContext(Dispatchers.Main) {
        if (isListening.get()) {
            stopRecognition()
        }
        
        if (!isModelInitialized) {
            isModelInitialized = funAudioLLMManager.initializeModel()
            if (!isModelInitialized) {
                errors.emit(VoiceRecognitionService.RecognitionError.ServiceNotAvailable)
                return@withContext
            }
        }
        
        try {
            continuousMode = continuous
            isListening.set(true)
            audioBuffer.reset()
            lastSoundTime = System.currentTimeMillis()
            hasDetectedSpeech = false
            consecutiveVoiceDetections = 0
            consecutiveSilenceDetections = 0
            
            Log.d(TAG, "开始语音识别，连续模式: $continuous")
            
            // 启动音频捕获
            audioStreamManager.startAudioCapture()
            
            // 启动识别任务
            recognitionJob = recognizerScope.launch {
                // 先收集一段时间的音频来确定背景噪音水平
                audioStreamManager.collectBackgroundNoise(500)
                
                // 然后开始处理音频流
                processAudioStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting FunAudioLLM recognition", e)
            errors.emit(VoiceRecognitionService.RecognitionError.AudioError)
        }
    }
    
    /**
     * 处理音频流
     */
    private suspend fun processAudioStream() {
        try {
            // 订阅音频流
            audioStreamManager.startAudioStream().collect { audioData ->
                if (!isListening.get()) {
                    return@collect
                }
                
                // 应用噪声抑制
                val processedData = noiseSuppressionManager.processAudioBuffer(audioData)
                
                // 检测是否有人声 - 使用AudioStreamManager中的方法
                val voiceDetected = audioStreamManager.hasHumanVoice(processedData)
                
                if (voiceDetected) {
                    lastSoundTime = System.currentTimeMillis()
                    consecutiveVoiceDetections++
                    consecutiveSilenceDetections = 0
                    
                    // 连续检测到3次人声才认为是真正的语音开始
                    if (consecutiveVoiceDetections >= 3 && !hasDetectedSpeech) {
                        hasDetectedSpeech = true
                        Log.d(TAG, "检测到语音开始 (连续3次检测到人声)")
                    }
                } else {
                    consecutiveVoiceDetections = 0
                    consecutiveSilenceDetections++
                }
                
                // 累积音频数据
                audioBuffer.write(processedData)
                
                // 检查是否需要进行识别
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSoundTime > SILENCE_THRESHOLD_MS && hasDetectedSpeech) {
                    // 检测到静音，进行识别
                    val bufferSize = audioBuffer.size()
                    if (bufferSize > MIN_AUDIO_SIZE) {
                        Log.d(TAG, "检测到静音，进行识别，音频大小: $bufferSize 字节")
                        recognizeAudio(audioBuffer.toByteArray())
                        
                        // 重置缓冲区和语音检测状态
                        audioBuffer.reset()
                        hasDetectedSpeech = false
                    } else {
                        Log.d(TAG, "音频大小不足，跳过识别: $bufferSize 字节")
                        audioBuffer.reset()
                    }
                    
                    // 如果不是连续模式，停止监听
                    if (!continuousMode) {
                        Log.d(TAG, "非连续模式，停止监听")
                        stopRecognition()

                        if (!hasDetectedSpeech && cacheVoiceText.isNotBlank()) {
                            recognitionResults.tryEmit(cacheVoiceText.toString())
                            cacheVoiceText.clear()
                        }
                    } else {
                        // 连续模式下，重置计时器
                        Log.d(TAG, "连续模式，重置计时器")
                        lastSoundTime = currentTime
                    }
                }

                // TODO: 完善长语音识别
                // else if (audioBuffer.size() > SAMPLE_RATE * 2 && hasDetectedSpeech) {
                //     // 缓冲区达到2秒且检测到语音，进行部分识别
                //     Log.d(TAG, "缓冲区达到2秒，进行部分识别，音频大小: ${audioBuffer.size()} 字节")
                //     recognizeAudio(audioBuffer.toByteArray())
                //
                //     // 清空缓冲区，避免重复识别相同的音频
                //     audioBuffer.reset()
                // }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio stream", e)
            errors.emit(VoiceRecognitionService.RecognitionError.AudioError)
        }
    }
    
    /**
     * 识别音频
     * @param audioData 音频数据
     * @return 识别结果
     */
    private suspend fun recognizeAudio(audioData: ByteArray) {
        try {
            Log.d(TAG, "开始识别音频，大小: ${audioData.size} 字节")
            // 使用FunAudioLLMManager进行API调用识别
            funAudioLLMManager.recognizeSpeech(audioData, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing audio", e)
        }
    }
    
    override fun stopRecognition() {
        if (!isListening.getAndSet(false)) {
            return
        }
        
        Log.d(TAG, "停止语音识别")
        
        recognitionJob?.cancel()
        recognitionJob = null
        audioStreamManager.stopAudioStream()
        
        // // 如果有累积的音频数据，进行最后一次识别
        // val finalAudio = audioBuffer.toByteArray()
        // audioBuffer.reset()
        //
        // if (finalAudio.isNotEmpty() && finalAudio.size > MIN_AUDIO_SIZE && hasDetectedSpeech) {
        //     Log.d(TAG, "进行最后一次识别，音频大小: ${finalAudio.size} 字节")
        //     recognizerScope.launch {
        //         recognizeAudio(finalAudio)
        //     }
        // } else {
        //     Log.d(TAG, "没有足够的音频数据进行最后识别，或未检测到语音")
        // }
    }
    
    override suspend fun cancelRecognition() = withContext(Dispatchers.Main) {
        if (!isListening.getAndSet(false)) {
            return@withContext
        }
        
        Log.d(TAG, "取消语音识别")
        
        recognitionJob?.cancel()
        recognitionJob = null
        audioStreamManager.stopAudioStream()
        audioBuffer.reset()
    }
    
    override fun getRecognitionResultsFlow(): Flow<String> = recognitionResults.asSharedFlow()
    
    override fun getPartialResultsFlow(): Flow<String> = partialResults.asSharedFlow()
    
    override fun getErrorsFlow(): Flow<Any> = errors.asSharedFlow()
    
    override fun setRecognitionIntent(intent: Intent) {
        // FunAudioLLM不使用Intent，这里不做任何操作
    }
    
    override fun release() {
        Log.d(TAG, "释放语音识别器资源")
        
        recognitionJob?.cancel()
        recognitionJob = null
        recognizerScope.coroutineContext.cancelChildren()
        audioBuffer.reset()
        isListening.set(false)
        funAudioLLMManager.releaseModel()
    }
    
    override fun isAvailable(): Boolean {
        return isModelInitialized
    }
}