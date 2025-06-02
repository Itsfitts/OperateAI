package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Google Gemini API的实现 支持标准Gemini接口流式传输 */
class GeminiProvider(
        private val apiEndpoint: String,
        private val apiKey: String,
        private val modelName: String
) : AIService {
    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEBUG = true // 开启调试日志
    }

    // HTTP客户端
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val requestId = System.currentTimeMillis().toString()
                        Log.d(TAG, "发送请求: ${request.method} ${request.url}")

                        val startTime = System.currentTimeMillis()
                        val response = chain.proceed(request)
                        val duration = System.currentTimeMillis() - startTime

                        Log.d(TAG, "收到响应: ${response.code}, 耗时: ${duration}ms")
                        response
                    }
                    .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 活跃请求，用于取消流式请求
    private var activeCall: Call? = null

    // Token计数
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0

    override val inputTokenCount: Int
        get() = _inputTokenCount
    override val outputTokenCount: Int
        get() = _outputTokenCount

    // 取消当前流式传输
    override fun cancelStreaming() {
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d(TAG, "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    // 重置Token计数
    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
    }

    // 工具函数：分块打印大型文本日志
    private fun logLargeString(tag: String, message: String, prefix: String = "") {
        // 设置单次日志输出的最大长度（Android日志上限约为4000字符）
        val maxLogSize = 3000

        // 如果消息长度超过限制，分块打印
        if (message.length > maxLogSize) {
            // 计算需要分多少块打印
            val chunkCount = message.length / maxLogSize + 1

            for (i in 0 until chunkCount) {
                val start = i * maxLogSize
                val end = minOf((i + 1) * maxLogSize, message.length)
                val chunkMessage = message.substring(start, end)

                // 打印带有编号的日志
                Log.d(tag, "$prefix Part ${i+1}/$chunkCount: $chunkMessage")
            }
        } else {
            // 消息长度在限制之内，直接打印
            Log.d(tag, "$prefix$message")
        }
    }

    // 日志辅助方法
    private fun logDebug(message: String) {
        if (DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /** 发送消息到Gemini API */
    override suspend fun sendMessage(
            message: String,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            chatHistory: List<Pair<String, String>>,
            onComplete: () -> Unit,
            onConnectionStatus: ((status: String) -> Unit)?,
            modelParameters: List<ModelParameter<*>>
    ) =
            withContext(Dispatchers.IO) {
                val requestId = System.currentTimeMillis().toString()
                // 重置token计数
                resetTokenCounts()

                Log.d(TAG, "发送消息到Gemini API, 模型: $modelName")

                val maxRetries = 3
                var retryCount = 0
                var lastException: Exception? = null

                val requestBody = createRequestBody(message, chatHistory, modelParameters)
                val request = createRequest(requestBody, true, requestId) // 使用流式请求

                onConnectionStatus?.invoke("连接到Gemini服务...")

                while (retryCount < maxRetries) {
                    try {
                        val call = client.newCall(request)
                        activeCall = call

                        onConnectionStatus?.invoke("建立连接中...")

                        val startTime = System.currentTimeMillis()
                        call.execute().use { response ->
                            val duration = System.currentTimeMillis() - startTime
                            Log.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                            onConnectionStatus?.invoke("连接成功，处理响应...")

                            if (!response.isSuccessful) {
                                val errorBody = response.body?.string() ?: "无错误详情"
                                logError("API请求失败: ${response.code}, $errorBody")
                                throw IOException("API请求失败: ${response.code}, $errorBody")
                            }

                            // 处理响应
                            processResponse(response, onPartialResponse, onComplete, requestId)
                        }

                        activeCall = null
                        return@withContext
                    } catch (e: SocketTimeoutException) {
                        lastException = e
                        retryCount++
                        logError("连接超时，尝试重试 $retryCount/$maxRetries", e)

                        onConnectionStatus?.invoke("连接超时，正在重试 $retryCount...")
                        delay(1000L * retryCount)
                    } catch (e: UnknownHostException) {
                        logError("无法连接到服务器，可能是网络问题", e)
                        onConnectionStatus?.invoke("无法连接到服务器，请检查网络")
                        throw IOException("无法连接到服务器，请检查网络连接")
                    } catch (e: Exception) {
                        logError("发送消息时发生异常", e)
                        onConnectionStatus?.invoke("连接失败: ${e.message}")
                        throw IOException("AI响应获取失败: ${e.message}")
                    }
                }

                logError("重试${maxRetries}次后仍然失败", lastException)
                onConnectionStatus?.invoke("重试失败，请检查网络连接")
                throw IOException("连接超时，已重试 $maxRetries 次")
            }

    /** 创建请求体 */
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>
    ): RequestBody {
        val requestId = System.currentTimeMillis().toString()
        val json = JSONObject()
        val contentsArray = JSONArray()

        logDebug("开始创建请求体，历史消息: ${chatHistory.size}条")

        // 标准化历史消息
        val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)

        // 调试输出所有历史消息
        for ((index, pair) in standardizedHistory.withIndex()) {
            logDebug("历史消息[$index]: role=${pair.first}, content长度=${pair.second.length}")
        }

        // 处理历史消息
        if (standardizedHistory.isNotEmpty()) {
            var lastRole: String? = null
            var hasSystemMessage = false
            var systemContent: String? = null

            // 首先检查是否有系统消息
            for ((index, pair) in standardizedHistory.withIndex()) {
                if (pair.first == "system") {
                    hasSystemMessage = true
                    systemContent = pair.second
                    logDebug("发现系统消息: ${systemContent.take(50)}...")

                    // 估算token
                    val tokens = estimateTokenCount(systemContent) + 20 // 增加一些token计数以包含额外的格式标记
                    _inputTokenCount += tokens
                    break // 只处理第一条系统消息
                }
            }

            // 如果有系统消息，使用专用的system_instruction字段
            if (hasSystemMessage && systemContent != null) {
                val systemInstruction = JSONObject()
                val systemPartsArray = JSONArray()
                val systemTextPart = JSONObject()
                
                systemTextPart.put("text", systemContent)
                systemPartsArray.put(systemTextPart)
                
                systemInstruction.put("parts", systemPartsArray)
                json.put("systemInstruction", systemInstruction)
            }

            // 处理其余消息
            for ((role, content) in standardizedHistory) {
                // 跳过已处理的系统消息
                if (role == "system") {
                    continue
                }

                // 如果与上一条消息角色相同，跳过
                if (role == lastRole) {
                    logDebug("跳过连续相同角色消息: $role")
                    continue
                }

                lastRole = role

                val contentObject = JSONObject()
                val partsArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("text", content)
                partsArray.put(textPart)

                contentObject.put("role", if (role == "assistant") "model" else role)
                contentObject.put("parts", partsArray)
                contentsArray.put(contentObject)

                // 估算token
                val tokens = estimateTokenCount(content)
                _inputTokenCount += tokens
            }
        }

        // 添加当前用户消息
        val userContentObject = JSONObject()
        val userPartsArray = JSONArray()
        val userTextPart = JSONObject()
        userTextPart.put("text", message)
        userPartsArray.put(userTextPart)

        userContentObject.put("role", "user")
        userContentObject.put("parts", userPartsArray)
        contentsArray.put(userContentObject)

        // 估算token
        val tokens = estimateTokenCount(message)
        _inputTokenCount += tokens

        // 添加contents到请求体
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 添加模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            generationConfig.put(
                                    "temperature",
                                    (param.currentValue as Number).toFloat()
                            )
                    "top_p" ->
                            generationConfig.put("topP", (param.currentValue as Number).toFloat())
                    "top_k" -> generationConfig.put("topK", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            generationConfig.put(
                                    "maxOutputTokens",
                                    (param.currentValue as Number).toInt()
                            )
                    else -> generationConfig.put(param.apiName, param.currentValue)
                }
            }
        }

        json.put("generationConfig", generationConfig)

        val jsonString = json.toString()
        // 使用分块日志函数记录完整的请求体
        logLargeString(TAG, jsonString, "请求体JSON: ")

        return jsonString.toRequestBody(JSON)
    }

    /** 创建HTTP请求 */
    private fun createRequest(
            requestBody: RequestBody,
            isStreaming: Boolean,
            requestId: String
    ): Request {
        // 确定请求URL
        val baseUrl = determineBaseUrl(apiEndpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val requestUrl = "$baseUrl/v1beta/models/$modelName:$method"

        Log.d(TAG, "请求URL: $requestUrl")

        // 创建Request Builder
        val builder = Request.Builder()

        // 添加API密钥
        val finalUrl =
                if (requestUrl.contains("?")) {
                    "$requestUrl&key=$apiKey"
                } else {
                    "$requestUrl?key=$apiKey"
                }

        return builder.url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
    }

    /** 确定基础URL */
    private fun determineBaseUrl(endpoint: String): String {
        return try {
            val url = URL(endpoint)
            "${url.protocol}://${url.host}"
        } catch (e: Exception) {
            logError("解析API端点失败", e)
            "https://generativelanguage.googleapis.com"
        }
    }

    /** 处理API响应 */
    private suspend fun processResponse(
            response: Response,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            onComplete: () -> Unit,
            requestId: String
    ) {
        Log.d(TAG, "开始处理响应流")
        val responseBody = response.body ?: throw IOException("响应为空")
        val reader = responseBody.charStream().buffered()

        // 用于累积内容
        val fullContent = StringBuilder()
        var lineCount = 0
        var dataCount = 0
        var jsonCount = 0
        var contentCount = 0
        var hasContent = false

        // 针对非SSE格式的完整JSON响应
        val completeJsonBuilder = StringBuilder()
        var isCollectingJson = false
        var jsonDepth = 0
        var jsonStartSymbol = ' ' // 记录JSON是以 { 还是 [ 开始的

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    // 检查是否已取消
                    if (activeCall?.isCanceled() == true) {
                        return@forEach
                    }

                    // 处理SSE数据
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        dataCount++

                        // 跳过结束标记
                        if (data == "[DONE]") {
                            logDebug("收到流结束标记 [DONE]")
                            return@forEach
                        }

                        try {
                            // 解析JSON
                            val json = JSONObject(data)
                            jsonCount++
                            logDebug("成功解析SSE数据为JSON")

                            val content = extractContentFromJson(json, requestId)

                            if (content.isNotEmpty()) {
                                contentCount++
                                fullContent.append(content)
                                hasContent = true
                                logDebug("提取内容成功，当前累积内容长度: ${fullContent.length}")

                                // 发送累积内容
                                onPartialResponse(fullContent.toString(), null)
                            }
                        } catch (e: Exception) {
                            logError("解析SSE响应数据失败: ${e.message}", e)
                        }
                    } else if (line.trim().isNotEmpty()) {
                        // 检查是否开始收集JSON
                        if (!isCollectingJson &&
                                        (line.trim().startsWith("{") || line.trim().startsWith("["))
                        ) {
                            isCollectingJson = true
                            jsonDepth = 0
                            completeJsonBuilder.clear()
                            jsonStartSymbol = line.trim()[0]
                            logDebug("开始收集JSON，起始符号: $jsonStartSymbol")
                        }

                        if (isCollectingJson) {
                            completeJsonBuilder.append(line.trim())

                            // 更新JSON深度
                            for (char in line) {
                                if (char == '{' || char == '[') jsonDepth++
                                if (char == '}' || char == ']') jsonDepth--
                            }

                            // 检查是否JSON已完成
                            val isJsonComplete =
                                    jsonDepth <= 0 &&
                                            ((jsonStartSymbol == '{' &&
                                                    (line.endsWith("}") || line.endsWith("},"))) ||
                                                    (jsonStartSymbol == '[' &&
                                                            (line.endsWith("]") ||
                                                                    line.endsWith("],"))))

                            if (isJsonComplete) {
                                val completeJson = completeJsonBuilder.toString()

                                // 处理逗号结尾情况
                                val cleanJson =
                                        if (completeJson.endsWith(",")) {
                                            completeJson.substring(0, completeJson.length - 1)
                                        } else {
                                            completeJson
                                        }

                                try {
                                    logDebug("解析完整JSON，长度: ${cleanJson.length}")
                                    logLargeString(TAG, cleanJson, "完整JSON响应: ")

                                    // 解析完整的JSON
                                    val jsonContent: Any? =
                                            if (cleanJson.startsWith("[")) {
                                                JSONArray(cleanJson)
                                            } else {
                                                JSONObject(cleanJson)
                                            }

                                    when (jsonContent) {
                                        is JSONArray -> {
                                            // 处理JSON数组
                                            for (i in 0 until jsonContent.length()) {
                                                val jsonObject = jsonContent.optJSONObject(i)
                                                if (jsonObject != null) {
                                                    jsonCount++

                                                    val content =
                                                            extractContentFromJson(
                                                                    jsonObject,
                                                                    requestId
                                                            )
                                                    if (content.isNotEmpty()) {
                                                        contentCount++
                                                        fullContent.append(content)
                                                        hasContent = true
                                                        logDebug(
                                                                "从JSON数组[$i]提取内容，长度: ${content.length}"
                                                        )

                                                        // 发送累积内容
                                                        onPartialResponse(
                                                                fullContent.toString(),
                                                                null
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        is JSONObject -> {
                                            // 处理单个JSON对象
                                            jsonCount++
                                            logDebug("处理单个JSON对象")

                                            val content =
                                                    extractContentFromJson(jsonContent, requestId)
                                            if (content.isNotEmpty()) {
                                                contentCount++
                                                fullContent.append(content)
                                                hasContent = true
                                                logDebug("从JSON对象提取内容，长度: ${content.length}")

                                                // 发送累积内容
                                                onPartialResponse(fullContent.toString(), null)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    logError("解析完整JSON失败: ${e.message}", e)
                                }

                                // 检查下一行是否开始新的JSON
                                isCollectingJson = false
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "响应处理完成: 共${lineCount}行, ${jsonCount}个JSON块, 提取${contentCount}个内容块")

            // 确保发送最终内容
            if (!hasContent) {
                // 如果没有内容，发送一个空格以确保回调触发
                logDebug("未检测到内容，发送空格以触发回调")
                onPartialResponse(" ", null)
            } else {
                logDebug("发送最终内容，长度: ${fullContent.length}")
                onPartialResponse(fullContent.toString(), null)
            }

            // 调用完成回调
            onComplete()
        } catch (e: Exception) {
            logError("处理响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private fun extractContentFromJson(json: JSONObject, requestId: String): String {
        val contentBuilder = StringBuilder()

        try {
            // 检查是否有错误信息
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = error.optString("message", "未知错误")
                logError("API返回错误: $errorMsg")
                return "" // 有错误时返回空字符串
            }

            // 提取候选项
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                logDebug("未找到候选项")
                return ""
            }

            // 处理第一个candidate
            val candidate = candidates.getJSONObject(0)

            // 检查finish_reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                logDebug("收到完成原因: $finishReason")
            }

            // 提取content对象
            val content = candidate.optJSONObject("content")
            if (content == null) {
                logDebug("未找到content对象")
                return ""
            }

            // 提取parts数组
            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                logDebug("未找到parts数组或为空")
                return ""
            }

            // 遍历parts，提取text内容
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")

                if (text.isNotEmpty()) {
                    contentBuilder.append(text)

                    // 估算token
                    val tokens = estimateTokenCount(text)
                    _outputTokenCount += tokens
                    logDebug("提取文本，长度=${text.length}, tokens=$tokens")
                }
            }

            return contentBuilder.toString()
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            return ""
        }
    }

    /** 估算Token数量 */
    private fun estimateTokenCount(text: String): Int {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount

        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                apiProviderType = ApiProviderType.GOOGLE
        )
    }
}
