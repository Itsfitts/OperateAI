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
    
    /**
     * 开始音频流
     * @return 音频数据Flow
     */
    private fun startAudioStream(): Flow<ByteArray> = flow {
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
                peakAmplitude = Math.max(peakAmplitude, Math.abs(signedSample))
                // 累加平方值用于RMS计算
                sum += signedSample * signedSample.toDouble()
            }
        }
        
        // 样本数量 (16位样本，每个占2字节)
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return
        
        // 计算RMS (均方根)
        val rms = Math.sqrt(sum / sampleCount)
        // 16位音频的最大理论值是32768
        val maxValue = 32768.0
        
        // 计算RMS归一化值 (0.0-1.0)
        val rmsNormalized = Math.min(1.0, rms / maxValue)
        
        // 计算峰值归一化值 (0.0-1.0)
        val peakNormalized = Math.min(1.0, peakAmplitude.toDouble() / maxValue)
        
        // 结合RMS和峰值，更注重峰值以更好反映语音
        val combinedLevel = rmsNormalized * 0.4 + peakNormalized * 0.6
        
        // 应用非线性映射增强对低音量的敏感度
        val enhancedLevel = Math.pow(combinedLevel, 0.6)
        
        // 平滑处理避免数值波动过大
        currentNoiseLevel = (currentNoiseLevel * 0.7f + enhancedLevel.toFloat() * 0.3f)
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
    
    companion object {
        private const val TAG = "AudioStreamManager"
    }
} 