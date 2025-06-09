package com.ai.assistance.operit.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 音频流管理器
 * 管理音频录制、音频焦点及音频流
 */
class AudioStreamManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isRecording = false
    
    // 录音配置
    private val sampleRate = 16000 // Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // 当前噪音水平 (0.0f - 1.0f)
    private var currentNoiseLevel = 0.0f
    
    // 人声检测相关常量
    companion object {
        private const val TAG = "AudioStreamManager"
        private const val ENERGY_THRESHOLD = 100.0 // 能量阈值
        private const val ZCR_THRESHOLD = 0.15 // 零交叉率阈值
        private const val HUMAN_VOICE_MIN_FREQ = 85.0 // 人声最低频率 (Hz)
        private const val HUMAN_VOICE_MAX_FREQ = 255.0 // 人声最高频率 (Hz)
        private const val FREQ_CONFIDENCE_THRESHOLD = 0.6 // 频率置信度阈值
    }
    
    // 背景噪音能量基线
    private var backgroundNoiseLevel = 0.0
    
    /**
     * 开始音频流
     * @return 音频数据Flow
     */
    fun startAudioStream(): Flow<ByteArray> = flow {
        if (isRecording) {
            stopAudioStream()
        }
        
        try {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("RECORD_AUDIO permission not granted")
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed!")
            }
            
            val buffer = ByteArray(bufferSize)
            audioRecord?.startRecording()
            isRecording = true
            
            while (isRecording) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readBytes > 0) {
                    // 计算噪音级别
                    updateNoiseLevel(buffer, readBytes)
                    
                    // 发送数据
                    emit(buffer.copyOf(readBytes))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio stream: ${e.message}")
            throw e
        }
    }
    
    /**
     * 停止音频流
     */
    fun stopAudioStream() {
        if (!isRecording) return
        
        try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            currentNoiseLevel = 0.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio stream: ${e.message}")
        }
    }
    
    /**
     * 开始音频捕获
     * 为语音识别服务提供的简化方法，初始化音频录制但不返回数据流
     */
    fun startAudioCapture() {
        if (isRecording) return
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing RECORD_AUDIO permission")
                return
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            // 启动一个线程来更新噪音级别
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    try {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (readBytes > 0) {
                            updateNoiseLevel(buffer, readBytes)
                        }
                        Thread.sleep(20) // 50Hz更新率
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading audio data: ${e.message}")
                    }
                }
            }.start()
            
            Log.d(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio capture: ${e.message}")
        }
    }
    
    /**
     * 计算当前噪音级别
     * @param buffer 音频数据
     * @param bytesRead 读取的字节数
     */
    private fun updateNoiseLevel(buffer: ByteArray, bytesRead: Int) {
        // 计算RMS值作为噪音等级的估计
        var sum = 0.0
        var peakAmplitude = 0
        
        // 分析音频样本计算RMS和峰值
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                // 将两个字节组合成一个16位样本
                val sample = buffer[i].toInt() and 0xFF or ((buffer[i + 1].toInt() and 0xFF) shl 8)
                // 将有符号16位转换为有符号整数(-32768 to 32767)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                // 更新峰值
                peakAmplitude = max(peakAmplitude, abs(signedSample))
                // 累加平方值用于RMS计算
                sum += signedSample * signedSample.toDouble()
            }
        }
        
        // 样本数量 (16位样本，每个占2字节)
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return
        
        // 计算RMS (均方根)
        val rms = sqrt(sum / sampleCount)
        // 16位音频的最大理论值是32768
        val maxValue = 32768.0
        
        // 计算RMS归一化值 (0.0-1.0)
        val rmsNormalized = min(1.0, rms / maxValue)
        
        // 计算峰值归一化值 (0.0-1.0)
        val peakNormalized = min(1.0, peakAmplitude.toDouble() / maxValue)
        
        // 结合RMS和峰值，更注重峰值以更好反映语音
        val combinedLevel = rmsNormalized * 0.4 + peakNormalized * 0.6
        
        // 应用非线性映射增强对低音量的敏感度
        val enhancedLevel = combinedLevel.pow(0.6)
        
        // 平滑处理避免数值波动过大
        currentNoiseLevel = (currentNoiseLevel * 0.9f + enhancedLevel.toFloat() * 0.3f)
    }
    
    /**
     * 获取当前噪音级别
     * @return 噪音级别 (0.0f - 1.0f)
     */
    fun getCurrentNoiseLevel(): Float {
        return currentNoiseLevel
    }
    
    /**
     * 请求音频焦点
     * @return 是否成功获取音频焦点
     */
    fun requestAudioFocus(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { focusChange ->
                // 处理音频焦点变化
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        // 完全失去音频焦点
                        Log.d(TAG, "Audio focus lost completely")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // 暂时失去音频焦点
                        Log.d(TAG, "Audio focus lost temporarily")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        // 暂时失去音频焦点，但可以降低音量继续播放
                        Log.d(TAG, "Audio focus lost temporarily but can duck")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        // 获得音频焦点
                        Log.d(TAG, "Audio focus gained")
                    }
                }
            }
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    
    /**
     * 放弃音频焦点
     */
    fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }
    
    /**
     * 拍下音频快照，用于语音识别
     * @param durationMs 录制时长，毫秒
     */
    suspend fun captureAudioSnapshot(durationMs: Int = 5000): ByteArray = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val outputStream = ByteArrayOutputStream()
        
        startAudioStream().collect { buffer ->
            outputStream.write(buffer)
            
            // 达到指定时长后停止
            if (System.currentTimeMillis() - startTime >= durationMs) {
                stopAudioStream()
            }
        }
        
        outputStream.toByteArray()
    }
    
    /**
     * 检查是否有录音权限
     * @return 是否有权限
     */
    fun hasRecordPermission(): Boolean {
        try {
            // 尝试创建AudioRecord来检查权限
            val testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            val hasPermission = testRecord.state == AudioRecord.STATE_INITIALIZED
            testRecord.release()
            return hasPermission
        } catch (e: SecurityException) {
            // 没有权限
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking record permission: ${e.message}")
            return false
        }
    }
    
    /**
     * 获取AudioRecord实例，供VAD使用
     * 注意：调用者负责释放资源
     * @return AudioRecord实例，如果无法创建则返回null
     */
    fun getAudioRecord(): AudioRecord? {
        if (isRecording && audioRecord != null) {
            return audioRecord
        }
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing RECORD_AUDIO permission")
                return null
            }
            
            // 创建专用于VAD的AudioRecord实例
            val vadAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (vadAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                vadAudioRecord.release()
                return null
            }
            
            // 启动录音
            vadAudioRecord.startRecording()
            
            Log.d(TAG, "Created AudioRecord instance for VAD")
            return vadAudioRecord
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioRecord for VAD: ${e.message}")
            return null
        }
    }
    
    /**
     * 收集背景噪音水平
     * @param durationMs 收集时长(毫秒)
     */
    suspend fun collectBackgroundNoise(durationMs: Int = 500) {
        try {
            var samples = 0
            var totalEnergy = 0.0
            val startTime = System.currentTimeMillis()
            
            // 收集指定时长的背景噪音
            startAudioStream().collect { audioData ->
                if (System.currentTimeMillis() - startTime > durationMs) {
                    stopAudioStream()
                    return@collect
                }
                
                val energy = calculateEnergy(audioData)
                totalEnergy += energy
                samples++
            }
            
            if (samples > 0) {
                backgroundNoiseLevel = totalEnergy / samples
                // 设置噪音阈值为背景噪音的1.5倍
                val adjustedThreshold = max(ENERGY_THRESHOLD, backgroundNoiseLevel * 1.5)
                Log.d(TAG, "背景噪音水平: $backgroundNoiseLevel, 调整后的阈值: $adjustedThreshold")
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集背景噪音时出错: ${e.message}")
        }
    }
    
    /**
     * 检测音频数据是否包含人声
     * 使用多种特征进行检测：能量、零交叉率、频率特征
     * @param audioData 音频数据
     * @return 是否包含人声
     */
    fun hasHumanVoice(audioData: ByteArray): Boolean {
        // 1. 能量检测
        val energy = calculateEnergy(audioData)
        val energyThreshold = max(ENERGY_THRESHOLD, backgroundNoiseLevel * 1.5)
        val hasEnoughEnergy = energy > energyThreshold
        
        if (!hasEnoughEnergy) {
            return false // 如果能量不足，直接返回false
        }
        
        // 2. 零交叉率检测 - 人声通常有一定范围的零交叉率
        val zcr = calculateZeroCrossingRate(audioData)
        val hasValidZCR = zcr > ZCR_THRESHOLD
        
        // 3. 频率特征检测 - 检测是否包含人声频率范围内的主要频率
        val frequencyConfidence = calculateFrequencyConfidence(audioData)
        val hasHumanFrequency = frequencyConfidence > FREQ_CONFIDENCE_THRESHOLD
        
        // 综合判断
        val isHumanVoice = hasEnoughEnergy && (hasValidZCR || hasHumanFrequency)
        
        if (isHumanVoice) {
            Log.d(TAG, "检测到人声 - 能量: $energy (阈值: $energyThreshold), " +
                    "零交叉率: $zcr (阈值: $ZCR_THRESHOLD), " +
                    "频率置信度: $frequencyConfidence (阈值: $FREQ_CONFIDENCE_THRESHOLD)")
        }
        
        return isHumanVoice
    }
    
    /**
     * 计算音频数据的能量
     */
    fun calculateEnergy(audioData: ByteArray): Double {
        var energy = 0.0
        val samples = ShortArray(audioData.size / 2)
        
        // 转换为short样本
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                samples[i / 2] = signedSample.toShort()
                energy += signedSample * signedSample
            }
        }
        
        // 计算平均能量
        return if (samples.isNotEmpty()) energy / samples.size else 0.0
    }
    
    /**
     * 计算零交叉率 (Zero Crossing Rate)
     * 零交叉率是信号从正变为负或从负变为正的比率
     * 人声通常有一定范围的零交叉率
     */
    fun calculateZeroCrossingRate(audioData: ByteArray): Double {
        val samples = ShortArray(audioData.size / 2)
        
        // 转换为short样本
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                samples[i / 2] = signedSample.toShort()
            }
        }
        
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) || 
                (samples[i - 1] < 0 && samples[i] >= 0)) {
                crossings++
            }
        }
        
        return if (samples.size > 1) crossings.toDouble() / samples.size else 0.0
    }
    
    /**
     * 计算频率置信度
     * 使用简化的频谱分析来检测是否包含人声频率范围内的主要频率
     */
    fun calculateFrequencyConfidence(audioData: ByteArray): Double {
        val samples = ShortArray(audioData.size / 2)
        
        // 转换为short样本
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                samples[i / 2] = signedSample.toShort()
            }
        }
        
        if (samples.isEmpty()) return 0.0
        
        // 使用简化的自相关法估计基频
        val maxLag = sampleRate / HUMAN_VOICE_MIN_FREQ.toInt()
        val minLag = sampleRate / HUMAN_VOICE_MAX_FREQ.toInt()
        
        var maxCorrelation = 0.0
        var bestLag = 0
        
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var count = 0
            
            for (i in 0 until samples.size - lag) {
                correlation += samples[i] * samples[i + lag]
                count++
            }
            
            if (count > 0) {
                correlation /= count
                
                if (correlation > maxCorrelation) {
                    maxCorrelation = correlation
                    bestLag = lag
                }
            }
        }
        
        // 如果没有找到有效的相关性，返回0
        if (bestLag == 0 || maxCorrelation <= 0) {
            return 0.0
        }
        
        // 计算估计的基频
        val estimatedFrequency = sampleRate.toDouble() / bestLag
        
        // 检查是否在人声频率范围内
        if (estimatedFrequency >= HUMAN_VOICE_MIN_FREQ && 
            estimatedFrequency <= HUMAN_VOICE_MAX_FREQ) {
            
            // 计算置信度 - 基于相关性强度和频率位置
            val frequencyConfidence = min(1.0, maxCorrelation / 1000000.0)
            
            return frequencyConfidence
        }
        
        return 0.0
    }
}