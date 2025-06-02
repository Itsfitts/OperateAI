package com.ai.assistance.operit.api

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/** 模型列表获取工具，用于从不同API提供商获取可用模型列表 */
object ModelListFetcher {
    private const val TAG = "ModelListFetcher"

    // 使用更长的超时时间
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

    /**
     * 从API端点URL派生出模型列表URL
     *
     * @param apiEndpoint 完整的API端点URL (如 https://api.openai.com/v1/chat/completions)
     * @param apiProviderType API提供商类型
     * @return 用于获取模型列表的URL
     */
    fun getModelsListUrl(apiEndpoint: String, apiProviderType: ApiProviderType): String {
        Log.d(TAG, "生成模型列表URL，API端点: $apiEndpoint, 提供商类型: $apiProviderType")

        val modelsUrl =
                when (apiProviderType) {
                    ApiProviderType.OPENAI -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.ANTHROPIC -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.GOOGLE -> {
                        // 对于Gemini API，直接使用提供的端点或默认端点
                        if (apiEndpoint.contains("generativelanguage.googleapis.com")) {
                            // 如果端点已经是模型列表URL，直接使用
                            if (apiEndpoint.endsWith("/models")) {
                                apiEndpoint
                            } else {
                                // 否则构造标准模型列表URL
                                val version = if (apiEndpoint.contains("/v1/")) "v1" else "v1beta"
                                "https://generativelanguage.googleapis.com/$version/models"
                            }
                        } else if (apiEndpoint.contains("aiplatform.googleapis.com") ||
                                        apiEndpoint.contains("vertex")
                        ) {
                            // Vertex AI格式
                            val projectMatch = Regex("projects/([^/]+)").find(apiEndpoint)
                            val locationMatch = Regex("locations/([^/]+)").find(apiEndpoint)

                            if (projectMatch != null && locationMatch != null) {
                                val project = projectMatch.groupValues[1]
                                val location = locationMatch.groupValues[1]
                                "https://$location-aiplatform.googleapis.com/v1/projects/$project/locations/$location/publishers/google/models"
                            } else {
                                "https://generativelanguage.googleapis.com/v1beta/models"
                            }
                        } else {
                            // 默认使用直接API
                            "https://generativelanguage.googleapis.com/v1beta/models"
                        }
                    }
                    ApiProviderType.DEEPSEEK -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.OPENROUTER -> "${extractBaseUrl(apiEndpoint)}/api/v1/models"
                    ApiProviderType.MOONSHOT -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.SILICONFLOW -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.BAICHUAN -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    // 其他API提供商可能需要特殊处理
                    else -> "${extractBaseUrl(apiEndpoint)}/v1/models" // 默认尝试OpenAI兼容格式
                }

        Log.d(TAG, "生成的模型列表URL: $modelsUrl")
        return modelsUrl
    }

    /** 从完整URL提取基本URL 例如: https://api.openai.com/v1/chat/completions -> https://api.openai.com */
    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val url = URL(fullUrl)
            val path = url.path
            val v1Index = path.indexOf("/v1")
            if (v1Index >= 0) {
                val baseUrl = "${url.protocol}://${url.host}"
                val finalUrl =
                        if (url.port != -1 && url.port != 80 && url.port != 443) {
                            "$baseUrl:${url.port}"
                        } else {
                            baseUrl
                        }
                Log.d(TAG, "从 $fullUrl 提取基本URL: $finalUrl (找到/v1)")
                finalUrl
            } else {
                val finalUrl = fullUrl.substringBefore("/v1")
                Log.d(TAG, "从 $fullUrl 提取基本URL: $finalUrl (未找到/v1)")
                finalUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL解析错误: $e")
            fullUrl
        }
    }

    /**
     * 获取模型列表
     *
     * @param apiKey API密钥
     * @param apiEndpoint 完整的API端点URL
     * @param apiProviderType API提供商类型
     * @return 模型列表结果
     */
    suspend fun getModelsList(
            apiKey: String,
            apiEndpoint: String,
            apiProviderType: ApiProviderType = ApiProviderType.OPENAI
    ): Result<List<ModelOption>> {
        Log.d(TAG, "开始获取模型列表: 端点=$apiEndpoint, 提供商=${apiProviderType.name}")

        return withContext(Dispatchers.IO) {
            val maxRetries = 2
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount <= maxRetries) {
                try {
                    // 根据提供商类型获取模型列表URL
                    val modelsUrl = getModelsListUrl(apiEndpoint, apiProviderType)
                    Log.d(TAG, "准备发送请求到: $modelsUrl, 尝试次数: ${retryCount + 1}/${maxRetries + 1}")

                    val requestBuilder =
                            Request.Builder()
                                    .url(modelsUrl)
                                    .addHeader("Content-Type", "application/json")

                    // 根据不同供应商添加不同的认证头
                    when (apiProviderType) {
                        ApiProviderType.GOOGLE -> {
                            // Google Gemini API 使用 API 密钥作为查询参数
                            val urlWithKey =
                                    if (modelsUrl.contains("?")) {
                                        "$modelsUrl&key=$apiKey"
                                    } else {
                                        "$modelsUrl?key=$apiKey"
                                    }
                            Log.d(
                                    TAG,
                                    "添加Google API密钥，完整URL: ${urlWithKey.replace(apiKey, "API_KEY_HIDDEN")}"
                            )
                            requestBuilder.url(urlWithKey)
                        }
                        ApiProviderType.OPENROUTER -> {
                            // OpenRouter需要添加特定请求头
                            Log.d(TAG, "使用Bearer认证方式并添加OpenRouter特定请求头")
                            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                            requestBuilder.addHeader("HTTP-Referer", "ai.assistance.operit")
                            requestBuilder.addHeader("X-Title", "Assistance App")
                        }
                        else -> {
                            // 大多数API使用Bearer认证
                            Log.d(TAG, "使用Bearer认证方式")
                            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                        }
                    }

                    val request = requestBuilder.get().build()

                    Log.d(TAG, "发送HTTP请求: ${request.url}")
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "无错误详情"
                        Log.e(TAG, "API请求失败: 状态码=${response.code}, 错误=$errorBody")
                        return@withContext Result.failure(
                                IOException("API请求失败: ${response.code}, 错误: $errorBody")
                        )
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        Log.e(TAG, "响应体为空")
                        return@withContext Result.failure(IOException("响应体为空"))
                    }

                    Log.d(
                            TAG,
                            "收到响应: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}"
                    )

                    // 根据提供商类型解析响应
                    val modelOptions =
                            try {
                                when (apiProviderType) {
                                    ApiProviderType.OPENAI,
                                    ApiProviderType.DEEPSEEK,
                                    ApiProviderType.MOONSHOT,
                                    ApiProviderType.SILICONFLOW,
                                    ApiProviderType.BAICHUAN,
                                    ApiProviderType.OPENROUTER ->
                                            parseOpenAIModelResponse(responseBody)
                                    ApiProviderType.ANTHROPIC ->
                                            parseAnthropicModelResponse(responseBody)
                                    ApiProviderType.GOOGLE -> parseGoogleModelResponse(responseBody)

                                    // 其他提供商可能需要单独的解析方法
                                    else -> parseOpenAIModelResponse(responseBody) // 默认尝试OpenAI格式
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "解析响应失败: ${e.message}")
                                return@withContext Result.failure(e)
                            }

                    Log.d(TAG, "成功解析模型列表，共获取 ${modelOptions.size} 个模型")
                    return@withContext Result.success(modelOptions)
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    retryCount++
                    Log.e(TAG, "连接超时: ${e.message}", e)
                    Log.d(TAG, "网络超时，尝试重试 $retryCount/$maxRetries")

                    if (retryCount <= maxRetries) {
                        // 指数退避重试
                        val delayTime = 1000L * retryCount
                        Log.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                } catch (e: IOException) {
                    lastException = e
                    retryCount++
                    Log.e(TAG, "IO异常: ${e.message}", e)
                    Log.d(TAG, "IO异常，尝试重试 $retryCount/$maxRetries")

                    if (retryCount <= maxRetries) {
                        val delayTime = 1000L * retryCount
                        Log.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                } catch (e: UnknownHostException) {
                    Log.e(TAG, "无法连接到服务器，域名解析失败", e)
                    return@withContext Result.failure(IOException("无法连接到服务器，请检查网络连接和API地址是否正确", e))
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    Log.e(TAG, "获取模型列表失败: ${e.message}", e)

                    if (retryCount <= maxRetries) {
                        // 指数退避重试
                        val delayTime = 1000L * retryCount
                        Log.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                }
            }

            // 所有重试都失败
            Log.e(TAG, "超过最大重试次数，获取模型列表失败")
            Result.failure(lastException ?: IOException("获取模型列表失败"))
        }
    }

    /** 解析OpenAI格式的模型响应 格式: {"data": [{"id": "model-id", "object": "model", ...}, ...]} */
    private fun parseOpenAIModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)
            if (!jsonObject.has("data")) {
                Log.e(TAG, "OpenAI响应格式错误: 缺少'data'字段")
                throw JSONException("响应格式错误: 缺少'data'字段")
            }

            val dataArray = jsonObject.getJSONArray("data")
            Log.d(TAG, "解析OpenAI格式响应: 发现 ${dataArray.length()} 个模型")

            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.getJSONObject(i)
                val id = modelObj.getString("id")
                modelList.add(ModelOption(id = id, name = id))
            }
        } catch (e: JSONException) {
            Log.e(TAG, "解析OpenAI格式JSON失败: ${e.message}", e)
            throw e
        }

        // 按照模型名称排序
        return modelList.sortedBy { it.id }
    }

    /** 解析Anthropic格式的模型响应 */
    private fun parseAnthropicModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)
            if (!jsonObject.has("models")) {
                Log.e(TAG, "Anthropic响应格式错误: 缺少'models'字段")
                throw JSONException("响应格式错误: 缺少'models'字段")
            }

            val modelsArray = jsonObject.getJSONArray("models")
            Log.d(TAG, "解析Anthropic格式响应: 发现 ${modelsArray.length()} 个模型")

            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val id = modelObj.getString("name")
                val displayName = modelObj.optString("display_name", id)
                modelList.add(ModelOption(id = id, name = displayName))
            }
        } catch (e: JSONException) {
            Log.e(TAG, "解析Anthropic模型JSON失败: ${e.message}", e)
            throw e
        }

        return modelList.sortedBy { it.id }
    }

    /**
     * 解析Google Gemini API格式的模型响应 Gemini API有两种格式:
     * 1. 直接API格式: {"models": [{model对象}, ...]}
     * 2. Vertex AI格式: {"models" 或 "publisher_models": [{model对象}, ...]}
     */
    private fun parseGoogleModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)

            // 检查是否包含"models"字段（Gemini API格式）
            if (jsonObject.has("models")) {
                val modelsArray = jsonObject.getJSONArray("models")
                Log.d(TAG, "解析Google Gemini API格式响应: 发现 ${modelsArray.length()} 个模型")

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val id = modelObj.getString("name").split("/").last()
                    val displayName = modelObj.optString("displayName", id)
                    val baseModelId = modelObj.optString("baseModelId", "")

                    // 只添加支持generateContent的模型，通过检查supportedGenerationMethods字段
                    val supportedMethods =
                            try {
                                if (modelObj.has("supportedGenerationMethods")) {
                                    val methods =
                                            modelObj.getJSONArray("supportedGenerationMethods")
                                    val methodsList = mutableListOf<String>()
                                    for (j in 0 until methods.length()) {
                                        methodsList.add(methods.getString(j))
                                    }
                                    methodsList
                                } else {
                                    listOf("generateContent") // 假设支持
                                }
                            } catch (e: Exception) {
                                listOf("generateContent") // 出错时默认支持
                            }

                    if (supportedMethods.contains("generateContent")) {
                        // 使用基本模型ID作为下拉列表中的选项
                        val finalId = if (baseModelId.isNotEmpty()) baseModelId else id
                        modelList.add(ModelOption(id = finalId, name = displayName))
                    }
                }
            }
            // 检查Vertex AI格式
            else if (jsonObject.has("models") || jsonObject.has("publisher_models")) {
                val modelsArray =
                        if (jsonObject.has("models")) {
                            jsonObject.getJSONArray("models")
                        } else {
                            jsonObject.getJSONArray("publisher_models")
                        }

                Log.d(TAG, "解析Vertex AI格式响应: 发现 ${modelsArray.length()} 个模型")

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val fullName = modelObj.getString("name")
                    val id = fullName.split("/").last()
                    val displayName = modelObj.optString("displayName", id)

                    // 过滤只添加Gemini模型
                    if (id.contains("gemini")) {
                        modelList.add(ModelOption(id = id, name = displayName))
                    }
                }
            } else {
                Log.e(TAG, "Google响应格式错误: 未找到'models'字段")
                throw JSONException("响应格式错误: 未找到'models'字段")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "解析Google模型JSON失败: ${e.message}", e)
            throw e
        }

        return modelList.sortedBy { it.id }
    }
}
