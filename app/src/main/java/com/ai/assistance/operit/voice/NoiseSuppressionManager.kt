package com.ai.assistance.operit.voice

import android.content.Context
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * 噪音抑制管理器
 * 提供音频降噪处理功能
 */
class NoiseSuppressionManager(private val context: Context) {
    
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isNoiseSuppressionEnabled = false
    
    // 降噪设置
    // TODO
    private var noiseThreshold = 500 // 噪音阈值
    private var suppressionStrength = 50 // 抑制强度 (0-100)
    
    init {
        isNoiseSuppressionEnabled = isNoiseSuppressionAvailable()
    }
    
    /**
     * 检查是否支持噪音抑制
     */
    private fun isNoiseSuppressionAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            NoiseSuppressor.isAvailable()
        } else {
            false
        }
    }
    
    /**
     * 为音频会话创建噪音抑制器
     * @param audioSessionId 音频会话ID
     * @return 是否成功创建
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    fun createNoiseSuppressor(audioSessionId: Int): Boolean {
        if (!isNoiseSuppressionAvailable() || audioSessionId == 0) {
            return false
        }
        
        try {
            releaseNoiseSuppressor()
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)
            val success = noiseSuppressor?.enabled ?: false
            if (!success) {
                Log.w(TAG, "Noise suppressor created but could not be enabled")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error creating noise suppressor: ${e.message}")
            return false
        }
    }
    
    /**
     * 释放噪音抑制器资源
     */
    fun releaseNoiseSuppressor() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing noise suppressor: ${e.message}")
        }
    }
    
    /**
     * 应用软件降噪功能
     * @param audioFlow 原始音频流
     * @return 降噪后的音频流
     */
    fun applyNoiseSuppression(audioFlow: Flow<ByteArray>): Flow<ByteArray> {
        // 如果硬件支持降噪，已经在AudioRecord级别应用了
        // 这里提供软件级别的简单降噪处理
        return audioFlow.map { audioData ->
            if (isNoiseSuppressionEnabled) {
                // 硬件降噪已经启用，直接返回原始数据
                audioData
            } else {
                // 应用简单的软件降噪算法
                applySimpleNoiseSuppression(audioData)
            }
        }
    }
    
    /**
     * 处理音频缓冲区
     * 用于VoiceRecognitionService中的音频处理
     * @param audioData 原始音频数据
     * @return 降噪处理后的音频数据
     */
    suspend fun processAudioBuffer(audioData: ByteArray): ByteArray {
        return if (isNoiseSuppressionEnabled && noiseSuppressor != null) {
            // TODO
            // 硬件降噪已启用，此时原始音频应该已经被处理
            // 这里可以添加额外的后处理
            enhanceAudioQuality(audioData)
        } else {
            // 应用软件降噪
            applySimpleNoiseSuppression(audioData)
        }
    }
    
    /**
     * 增强音频质量
     * 在硬件降噪之后可以应用一些额外的处理
     * @param audioData 音频数据
     * @return 增强后的音频数据
     */
    private fun enhanceAudioQuality(audioData: ByteArray): ByteArray {
        // 这里可以实现额外的音频增强处理
        // 例如：动态范围压缩、信号放大等
        
        // 简单示例：如果信号幅度过低，适当放大
        val result = audioData.copyOf()
        var maxAmplitude = 0
        
        // 计算最大幅度
        for (i in 0 until audioData.size step 2) {
            if (i + 1 < audioData.size) {
                val sample = audioData[i].toInt() and 0xFF or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample >= 0x8000) sample - 0x10000 else sample
                maxAmplitude = maxOf(maxAmplitude, abs(signedSample))
            }
        }
        
        // 如果最大幅度低于阈值，适当放大
        if (maxAmplitude > 0 && maxAmplitude < 4000) {
            val gain = minOf(2.0, 4000.0 / maxAmplitude) // 放大2倍或到阈值
            
            for (i in 0 until audioData.size step 2) {
                if (i + 1 < audioData.size) {
                    val sample = audioData[i].toInt() and 0xFF or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                    val signedSample = if (sample >= 0x8000) sample - 0x10000 else sample
                    
                    // 应用增益
                    val enhancedSample = (signedSample * gain).toInt().coerceIn(-32768, 32767)
                    
                    // 转回字节
                    result[i] = (enhancedSample and 0xFF).toByte()
                    result[i + 1] = ((enhancedSample shr 8) and 0xFF).toByte()
                }
            }
        }
        
        return result
    }
    
    /**
     * 简单的软件降噪实现
     * 注意：这只是一个非常基础的示例，实际应用中应使用更复杂的算法
     * @param audioData 原始音频数据
     * @return 处理后的音频数据
     */
    private fun applySimpleNoiseSuppression(audioData: ByteArray): ByteArray {
        // 简单阈值过滤，用于示例
        // 在实际应用中，应使用更高级的降噪算法，如频域过滤、自适应滤波器等
        
        // 假设是16位PCM数据
        val result = audioData.copyOf()
        
        for (i in 0 until audioData.size step 2) {
            if (i + 1 < audioData.size) {
                // 转换为16位有符号整数
                val sample = audioData[i].toInt() and 0xFF or ((audioData[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample >= 0x8000) sample - 0x10000 else sample
                
                // 简单阈值处理
                val processedSample = if (abs(signedSample) < noiseThreshold) {
                    0 // 低于阈值视为噪音，设为0
                } else {
                    // 应用噪音抑制强度
                    val suppressionFactor = 1.0 - (suppressionStrength / 100.0) * (noiseThreshold.toDouble() / abs(signedSample))
                    (signedSample * suppressionFactor).toInt()
                }
                
                // 转回字节
                result[i] = (processedSample and 0xFF).toByte()
                result[i + 1] = ((processedSample shr 8) and 0xFF).toByte()
            }
        }
        
        return result
    }
    
    /**
     * 设置降噪阈值
     * @param threshold 噪音阈值
     */
    fun setNoiseThreshold(threshold: Int) {
        this.noiseThreshold = threshold.coerceIn(0, 5000)
    }
    
    /**
     * 设置降噪强度 (如果支持)
     * @param strength 强度级别 (0-100)
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    fun setNoiseSuppressionStrength(strength: Int) {
        this.suppressionStrength = strength.coerceIn(0, 100)
        
        try {
            noiseSuppressor?.setEnabled(strength > 0)
            // 注意：Android的NoiseSuppressor没有直接设置强度的API
            // 这里仅是示例，实际应用中可能需要其他方法调整强度
        } catch (e: Exception) {
            Log.e(TAG, "Error setting noise suppression strength: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "NoiseSuppressionManager"
    }
} 