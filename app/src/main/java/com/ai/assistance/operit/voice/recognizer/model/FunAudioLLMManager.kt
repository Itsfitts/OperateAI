package com.ai.assistance.operit.voice.recognizer.model

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

/**
 * FunAudioLLM模型管理器
 */
class FunAudioLLMManager(
    private val context: Context,
    private val enhancedAIService: EnhancedAIService,
    private val recognitionResults: MutableSharedFlow<String>
    ) {
    companion object {
        private const val TAG = "FunAudioLLMManager"
        private const val REQUEST_TIMEOUT_SECONDS = 30L

        // 单例实例
        @Volatile
        private var instance: FunAudioLLMManager? = null

        fun getInstance(
            context: Context,
            enhancedAIService: EnhancedAIService,
            recognitionResults: MutableSharedFlow<String>
        ): FunAudioLLMManager {
            return instance ?: synchronized(this) {
                instance ?: FunAudioLLMManager(
                    context.applicationContext,
                    enhancedAIService,
                    recognitionResults
                ).also { instance = it }
            }
        }
    }
    
    // 服务是否已初始化
    private val isServiceInitialized = AtomicBoolean(false)
    
    // OkHttp客户端，用于直接发送multipart请求
    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // API端点和密钥
    private var apiEndpoint = "https://api.siliconflow.cn/v1/audio/transcriptions"
    private var apiKey = "sk-fdqgfvtruvjudoyukgklerwvmvkzobzatbiqhefdwwzqpcsj"
    private var modelName = "FunAudioLLM/SenseVoiceSmall"
    
    // 配置管理器
    private val functionalConfigManager = FunctionalConfigManager(context)
    private val modelConfigManager = ModelConfigManager(context)
    
    /**
     * 初始化服务
     * @return 是否成功初始化
     */
    suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        if (isServiceInitialized.get()) {
            Log.d(TAG, "FunAudioLLM API service already initialized")
            return@withContext true
        }
        
        try {
            // 刷新语音识别功能的服务实例
            enhancedAIService.refreshServiceForFunction(FunctionType.SPEECH_RECOGNITION)

            // 获取API配置
            val configId = functionalConfigManager.getConfigIdForFunction(FunctionType.SPEECH_RECOGNITION)
            Log.d(TAG, "使用配置ID: $configId 获取语音识别配置")
            
            val config = modelConfigManager.getModelConfigFlow(configId).first()

            // 保存API端点和密钥
            apiEndpoint = config.apiEndpoint
            apiKey = config.apiKey
            

            Log.d(TAG, "FunAudioLLM API 初始化成功，端点: $apiEndpoint, 模型: $modelName")
            isServiceInitialized.set(true)
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FunAudioLLM API service: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 识别语音
     * @param audioData 音频数据
     * @param sampleRate 采样率
     */
    suspend fun recognizeSpeech(audioData: ByteArray, sampleRate: Int) {
        if (!isServiceInitialized.get()) {
            val initialized = initializeModel()
            if (!initialized) {
                Log.e(TAG, "FunAudioLLM API service not initialized, cannot recognize speech")
                return
            }
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.e(TAG, "Network Error, cannot recognize speech")
            return
        }
        
        if (audioData.isEmpty()) {
            Log.e(TAG, "Empty audio data, cannot recognize speech")
            return
        }
        
        Log.d(TAG, "开始识别语音，音频大小: ${audioData.size} 字节, 采样率: $sampleRate Hz")
        
        try {
            // 将音频数据保存为临时WAV文件
            val tempFile = createTempWavFile(audioData)
            if (tempFile == null) {
                Log.e(TAG, "Failed to create temporary WAV file")
                return
            }

            Log.d(TAG, "创建临时WAV文件成功: ${tempFile.absolutePath}, 大小: ${tempFile.length()} 字节")

            // 创建multipart请求
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "audio.wav",
                    RequestBody.create("audio/wav".toMediaTypeOrNull(), tempFile)
                )
                .addFormDataPart("model", modelName)
                .build()

            val request = Request.Builder()
                .url(apiEndpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "multipart/form-data")
                .post(requestBody)
                .build()

            Log.d(TAG, "发送API请求到: $apiEndpoint, 使用模型: $modelName")

            // 执行请求
            val countDownLatch = CountDownLatch(1)
            var requestSuccess = false
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "API请求失败: ${e.message}", e)
                    tempFile.delete() // 清理临时文件
                    countDownLatch.countDown()
                }
                
                override fun onResponse(call: Call, response: Response) {
                    try {
                        tempFile.delete() // 清理临时文件
                        Log.d(TAG, "临时文件已删除")
                        
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "No response body"
                            Log.e(TAG, "API请求失败，状态码: ${response.code}, 响应: $errorBody")
                            
                            // 尝试从错误响应中提取更多信息
                            var errorMessage = "识别失败 (${response.code})"
                            try {
                                val jsonError = JSONObject(errorBody)
                                if (jsonError.has("error")) {
                                    val error = jsonError.getJSONObject("error")
                                    if (error.has("message")) {
                                        errorMessage = error.getString("message")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "无法解析错误响应为JSON: ${e.message}")
                            }

                            countDownLatch.countDown()
                            return
                        }
                        
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "API响应: $responseBody")
                        
                        // 解析结果
                        val result = parseTranscriptionResult(responseBody)
                        if (result.isNotEmpty()) {
                            Log.d(TAG, "识别结果: $result")
                            recognitionResults.tryEmit(result)
                            requestSuccess = true
                        } else {
                            Log.w(TAG, "未能从响应中解析出文本")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理API响应时出错: ${e.message}", e)
                    } finally {
                        countDownLatch.countDown()
                    }
                }
            })
            
            // 等待请求完成，最多等待REQUEST_TIMEOUT_SECONDS秒
            try {
                val completed = countDownLatch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!completed) {
                    Log.e(TAG, "API请求超时")
                } else {
                    Log.d(TAG, "API请求完成，结果: ${if (requestSuccess) "成功" else "失败"}")
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "等待API响应时被中断", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "识别语音时出错: ${e.message}", e)
        }
    }
    
    /**
     * 创建临时WAV文件
     * @param audioData 音频数据
     * @return 临时文件
     */
    private fun createTempWavFile(audioData: ByteArray): File? {
        try {
            // 创建临时文件
            val tempFile = File(context.cacheDir, "audio_${UUID.randomUUID()}.wav")
            
            // 写入WAV头部
            val outputStream = FileOutputStream(tempFile)
            
            // RIFF头
            outputStream.write("RIFF".toByteArray())
            val fileSize = 36 + audioData.size
            outputStream.write(intToByteArray(fileSize))
            outputStream.write("WAVE".toByteArray())
            
            // fmt子块
            outputStream.write("fmt ".toByteArray())
            outputStream.write(intToByteArray(16)) // 子块大小
            outputStream.write(shortToByteArray(1)) // 音频格式 (PCM)
            outputStream.write(shortToByteArray(1)) // 通道数
            outputStream.write(intToByteArray(16000)) // 采样率
            outputStream.write(intToByteArray(16000 * 2)) // 字节率
            outputStream.write(shortToByteArray(2)) // 块对齐
            outputStream.write(shortToByteArray(16)) // 位深度
            
            // data子块
            outputStream.write("data".toByteArray())
            outputStream.write(intToByteArray(audioData.size))
            
            // 写入音频数据
            outputStream.write(audioData)
            outputStream.close()
            
            Log.d(TAG, "WAV文件创建成功，大小: ${tempFile.length()} 字节")
            
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "创建临时WAV文件失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 将int转换为小端字节数组
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }
    
    /**
     * 将short转换为小端字节数组
     */
    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte()
        )
    }
    
    /**
     * 解析转录结果
     * @param content API返回的内容
     * @return 提取的文本
     */
    private fun parseTranscriptionResult(content: String): String {
        try {
            // 首先尝试解析为JSON
            val jsonObject = JSONObject(content)
            if (jsonObject.has("text")) {
                return jsonObject.getString("text")
            }
        } catch (e: Exception) {
            Log.d(TAG, "无法将响应解析为JSON，尝试使用正则表达式: ${e.message}")
        }
        
        // 如果JSON解析失败，尝试使用正则表达式
        val textRegex = """"text":\s*"([^"]+)"""".toRegex()
        val match = textRegex.find(content)
        
        val result = match?.groupValues?.getOrNull(1) ?: content.trim()
        Log.d(TAG, "使用正则表达式解析结果: $result")
        return result
    }
    
    /**
     * 释放资源
     */
    fun releaseModel() {
        // 清理资源
        Log.d(TAG, "FunAudioLLM API service resources released")
    }
}