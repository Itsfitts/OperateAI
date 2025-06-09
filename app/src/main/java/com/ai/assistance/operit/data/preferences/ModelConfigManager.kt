package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.CustomParameterData
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为ModelConfig创建专用的DataStore
private val Context.modelConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "model_configs")

// 获取ApiPreferences的DataStore
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ModelConfigManager(private val context: Context) {

    // 定义key
    companion object {
        private const val TAG = "ModelConfigManager"
        
        // 配置相关key
        val CONFIG_LIST = stringPreferencesKey("config_list")
        // 删除活跃配置ID

        // 默认值
        const val DEFAULT_CONFIG_ID = "default"
        const val DEFAULT_CONFIG_NAME = "默认配置"
        
        // 语音识别配置
        const val SPEECH_RECOGNITION_CONFIG_ID = "speech_recognition_default"
        const val SPEECH_RECOGNITION_CONFIG_NAME = "语音识别配置"
    }

    // Json解析器，支持宽松模式
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取ApiPreferences实例用于默认配置
    private val apiPreferences = ApiPreferences(context)

    // 获取所有配置ID列表
    val configListFlow: Flow<List<String>> =
            context.modelConfigDataStore.data.map { preferences ->
                val configList = preferences[CONFIG_LIST] ?: ""
                if (configList.isEmpty()) listOf(DEFAULT_CONFIG_ID, SPEECH_RECOGNITION_CONFIG_ID)
                else json.decodeFromString<List<String>>(configList)
            }

    // 初始化，确保至少有一个默认配置
    suspend fun initializeIfNeeded() {
        // 确保API提供商类型已初始化
        apiPreferences.ensureApiProviderTypeInitialized()
        
        val configList = configListFlow.first()
        val updatedList = configList.toMutableList()
        var needsUpdate = false

        // 检查默认配置
        if (!configList.contains(DEFAULT_CONFIG_ID)) {
            // 创建默认配置（从现有ApiPreferences导入）
            val defaultConfig = createDefaultConfigFromApiPreferences()
            saveModelConfig(defaultConfig)
            updatedList.add(DEFAULT_CONFIG_ID)
            needsUpdate = true
            Log.d(TAG, "创建默认配置")
        }
        
        // 检查语音识别配置
        if (!configList.contains(SPEECH_RECOGNITION_CONFIG_ID)) {
            // 创建语音识别配置
            val speechRecognitionConfig = createSpeechRecognitionConfig()
            saveModelConfig(speechRecognitionConfig)
            updatedList.add(SPEECH_RECOGNITION_CONFIG_ID)
            needsUpdate = true
            Log.d(TAG, "创建语音识别配置")
        }

        // 保存更新后的配置列表
        if (needsUpdate) {
            context.modelConfigDataStore.edit { preferences ->
                preferences[CONFIG_LIST] = json.encodeToString(updatedList)
            }
        }
    }

    // 从原有ApiPreferences创建默认配置
    private suspend fun createDefaultConfigFromApiPreferences(): ModelConfigData {
        // 获取API设置
        val apiKey = apiPreferences.apiKeyFlow.first()
        val apiEndpoint = apiPreferences.apiEndpointFlow.first()
        val modelName = apiPreferences.modelNameFlow.first()
        val apiProviderType = apiPreferences.apiProviderTypeFlow.first()

        // 获取参数启用状态
        val maxTokensEnabled = apiPreferences.maxTokensEnabledFlow.first()
        val temperatureEnabled = apiPreferences.temperatureEnabledFlow.first()
        val topPEnabled = apiPreferences.topPEnabledFlow.first()
        val topKEnabled = apiPreferences.topKEnabledFlow.first()
        val presencePenaltyEnabled = apiPreferences.presencePenaltyEnabledFlow.first()
        val frequencyPenaltyEnabled = apiPreferences.frequencyPenaltyEnabledFlow.first()
        val repetitionPenaltyEnabled = apiPreferences.repetitionPenaltyEnabledFlow.first()

        // 获取参数值
        val maxTokens = apiPreferences.maxTokensFlow.first()
        val temperature = apiPreferences.temperatureFlow.first()
        val topP = apiPreferences.topPFlow.first()
        val topK = apiPreferences.topKFlow.first()
        val presencePenalty = apiPreferences.presencePenaltyFlow.first()
        val frequencyPenalty = apiPreferences.frequencyPenaltyFlow.first()
        val repetitionPenalty = apiPreferences.repetitionPenaltyFlow.first()

        // 获取自定义参数
        val customParamsJson =
                try {
                    context.apiDataStore.data.first()[ApiPreferences.CUSTOM_PARAMETERS] ?: "[]"
                } catch (e: Exception) {
                    "[]"
                }

        return ModelConfigData(
                id = DEFAULT_CONFIG_ID,
                name = DEFAULT_CONFIG_NAME,
                // 移除isActive = true
                apiKey = apiKey,
                apiEndpoint = apiEndpoint,
                modelName = modelName,
                apiProviderType = apiProviderType,
                hasCustomParameters = customParamsJson != "[]",
                maxTokensEnabled = maxTokensEnabled,
                temperatureEnabled = temperatureEnabled,
                topPEnabled = topPEnabled,
                topKEnabled = topKEnabled,
                presencePenaltyEnabled = presencePenaltyEnabled,
                frequencyPenaltyEnabled = frequencyPenaltyEnabled,
                repetitionPenaltyEnabled = repetitionPenaltyEnabled,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                topK = topK,
                presencePenalty = presencePenalty,
                frequencyPenalty = frequencyPenalty,
                repetitionPenalty = repetitionPenalty,
                customParameters = customParamsJson
        )
    }
    
    /**
     * 创建语音识别专用配置
     */
    private suspend fun createSpeechRecognitionConfig(): ModelConfigData {
        // 首先获取默认配置作为基础
        val defaultConfig = getModelConfigFlow(DEFAULT_CONFIG_ID).first()

        return defaultConfig.copy(
            id = SPEECH_RECOGNITION_CONFIG_ID,
            name = SPEECH_RECOGNITION_CONFIG_NAME,
            apiEndpoint = "https://api.siliconflow.cn/v1/audio/transcriptions",
            modelName = "FunAudioLLM/SenseVoiceSmall",
            apiProviderType = ApiProviderType.SILICONFLOW,
            // 禁用标准参数，因为语音转写API不使用这些参数
            maxTokensEnabled = false,
            temperatureEnabled = false,
            topPEnabled = false,
            topKEnabled = false,
            presencePenaltyEnabled = false,
            frequencyPenaltyEnabled = false,
            repetitionPenaltyEnabled = false,
            // 添加语音识别相关的自定义参数
            hasCustomParameters = true,
            customParameters = """
                [
                    {
                        "id": "content_type",
                        "name": "Content-Type",
                        "apiName": "Content-Type",
                        "description": "请求内容类型",
                        "defaultValue": "multipart/form-data",
                        "currentValue": "multipart/form-data",
                        "isEnabled": true,
                        "valueType": "STRING",
                        "category": "SYSTEM"
                    },
                    {
                        "id": "model",
                        "name": "模型",
                        "apiName": "model",
                        "description": "使用的语音识别模型",
                        "defaultValue": "FunAudioLLM/SenseVoiceSmall",
                        "currentValue": "FunAudioLLM/SenseVoiceSmall",
                        "isEnabled": true,
                        "valueType": "STRING",
                        "category": "SYSTEM"
                    },
                    {
                        "id": "file_param_name",
                        "name": "文件参数名",
                        "apiName": "file_param_name",
                        "description": "上传音频文件的参数名",
                        "defaultValue": "file",
                        "currentValue": "file",
                        "isEnabled": true,
                        "valueType": "STRING",
                        "category": "SYSTEM"
                    },
                    {
                        "id": "response_format",
                        "name": "响应格式",
                        "apiName": "response_format",
                        "description": "API响应的格式",
                        "defaultValue": "json",
                        "currentValue": "json",
                        "isEnabled": false,
                        "valueType": "STRING",
                        "category": "GENERATION"
                    },
                    {
                        "id": "language",
                        "name": "语言",
                        "apiName": "language",
                        "description": "识别的目标语言",
                        "defaultValue": "zh",
                        "currentValue": "zh",
                        "isEnabled": false,
                        "valueType": "STRING",
                        "category": "GENERATION"
                    }
                ]
            """.trimIndent()
        )
    }

    // 保存配置
    suspend fun saveModelConfig(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        context.modelConfigDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }

    // 获取指定ID的配置
    fun getModelConfigFlow(configId: String): Flow<ModelConfigData> {
        val configKey = stringPreferencesKey("config_${configId}")
        return context.modelConfigDataStore.data.map { preferences ->
            val configJson = preferences[configKey]
            if (configJson != null) {
                try {
                    json.decodeFromString<ModelConfigData>(configJson)
                } catch (e: Exception) {
                    // 如果解析失败，回退到创建一个新配置
                    when (configId) {
                        DEFAULT_CONFIG_ID -> createDefaultConfigFromApiPreferences()
                        SPEECH_RECOGNITION_CONFIG_ID -> createSpeechRecognitionConfig()
                        else -> ModelConfigData(id = configId, name = "配置 $configId")
                    }
                }
            } else {
                when (configId) {
                    DEFAULT_CONFIG_ID -> createDefaultConfigFromApiPreferences()
                    SPEECH_RECOGNITION_CONFIG_ID -> createSpeechRecognitionConfig()
                    else -> ModelConfigData(id = configId, name = "配置 $configId")
                }
            }
        }
    }

    // 获取所有配置的摘要信息
    suspend fun getAllConfigSummaries(): List<ModelConfigSummary> {
        val configIds = configListFlow.first()
        val summaries = mutableListOf<ModelConfigSummary>()

        for (id in configIds) {
            val config = getModelConfigFlow(id).first()
            summaries.add(
                    ModelConfigSummary(
                            id = config.id,
                            name = config.name,
                            modelName = config.modelName,
                            apiEndpoint = config.apiEndpoint
                    )
            )
        }

        return summaries
    }

    // 创建新配置
    suspend fun createConfig(name: String): String {
        val configId = UUID.randomUUID().toString()
        val configList = configListFlow.first().toMutableList()

        // 复制当前活跃配置作为新配置的基础
        val baseConfig = getModelConfigFlow(DEFAULT_CONFIG_ID).first()

        val newConfig = baseConfig.copy(id = configId, name = name)

        // 保存新配置
        saveModelConfig(newConfig)

        // 更新配置列表
        configList.add(configId)
        context.modelConfigDataStore.edit { preferences ->
            preferences[CONFIG_LIST] = json.encodeToString(configList)
        }

        return configId
    }

    // 删除配置
    suspend fun deleteConfig(configId: String) {
        if (configId == DEFAULT_CONFIG_ID) {
            // 不允许删除默认配置
            return
        }

        val configList = configListFlow.first().toMutableList()

        // 从列表中移除
        configList.remove(configId)
        context.modelConfigDataStore.edit { preferences ->
            // 删除配置记录 - 修复null赋值问题
            preferences.remove(stringPreferencesKey("config_${configId}"))
            // 更新配置列表
            preferences[CONFIG_LIST] = json.encodeToString(configList)
        }
    }

    // 获取配置参数列表
    suspend fun getModelParametersForConfig(configId: String): List<ModelParameter<*>> {
        // 获取配置
        val config = getModelConfigFlow(configId).first()
        return createParametersFromConfig(config)
    }

    // 从配置创建参数列表
    private fun createParametersFromConfig(config: ModelConfigData): List<ModelParameter<*>> {
        val parameters = mutableListOf<ModelParameter<*>>()

        // 添加标准参数
        parameters.add(
                ModelParameter(
                        id = "max_tokens",
                        name = "最大生成Token数",
                        apiName = "max_tokens",
                        description = "控制AI每次最多生成的Token数量",
                        defaultValue = ApiPreferences.DEFAULT_MAX_TOKENS,
                        currentValue = config.maxTokens,
                        isEnabled = config.maxTokensEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 1,
                        maxValue = 16000,
                        category = ParameterCategory.GENERATION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "temperature",
                        name = "温度",
                        apiName = "temperature",
                        description = "控制输出的随机性。较低的值更确定性，较高的值更随机",
                        defaultValue = ApiPreferences.DEFAULT_TEMPERATURE,
                        currentValue = config.temperature,
                        isEnabled = config.temperatureEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "top_p",
                        name = "Top-P 采样",
                        apiName = "top_p",
                        description = "作为温度的替代方案，模型仅考虑概率最高的Top-P比例的token",
                        defaultValue = ApiPreferences.DEFAULT_TOP_P,
                        currentValue = config.topP,
                        isEnabled = config.topPEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "top_k",
                        name = "Top-K 采样",
                        apiName = "top_k",
                        description = "模型仅考虑概率最高的K个token。0表示禁用",
                        defaultValue = ApiPreferences.DEFAULT_TOP_K,
                        currentValue = config.topK,
                        isEnabled = config.topKEnabled,
                        valueType = ParameterValueType.INT,
                        minValue = 0,
                        maxValue = 100,
                        category = ParameterCategory.CREATIVITY
                )
        )

        parameters.add(
                ModelParameter(
                        id = "presence_penalty",
                        name = "存在惩罚",
                        apiName = "presence_penalty",
                        description = "增强模型谈论新主题的倾向。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_PRESENCE_PENALTY,
                        currentValue = config.presencePenalty,
                        isEnabled = config.presencePenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "frequency_penalty",
                        name = "频率惩罚",
                        apiName = "frequency_penalty",
                        description = "减少模型重复同一词语的可能性。值越高，惩罚越大",
                        defaultValue = ApiPreferences.DEFAULT_FREQUENCY_PENALTY,
                        currentValue = config.frequencyPenalty,
                        isEnabled = config.frequencyPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = -2.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        parameters.add(
                ModelParameter(
                        id = "repetition_penalty",
                        name = "重复惩罚",
                        apiName = "repetition_penalty",
                        description = "进一步减少重复。1.0表示不惩罚，大于1.0会降低重复可能性",
                        defaultValue = ApiPreferences.DEFAULT_REPETITION_PENALTY,
                        currentValue = config.repetitionPenalty,
                        isEnabled = config.repetitionPenaltyEnabled,
                        valueType = ParameterValueType.FLOAT,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        category = ParameterCategory.REPETITION
                )
        )

        // 如果有自定义参数，添加到列表中
        if (config.hasCustomParameters && config.customParameters != "[]") {
            try {
                val customParamsList =
                        json.decodeFromString<List<CustomParameterData>>(config.customParameters)
                for (customParam in customParamsList) {
                    val param =
                            when (ParameterValueType.valueOf(customParam.valueType)) {
                                ParameterValueType.INT -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toInt(),
                                            currentValue = customParam.currentValue.toInt(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.INT,
                                            minValue = customParam.minValue?.toInt(),
                                            maxValue = customParam.maxValue?.toInt(),
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.FLOAT -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toFloat(),
                                            currentValue = customParam.currentValue.toFloat(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.FLOAT,
                                            minValue = customParam.minValue?.toFloat(),
                                            maxValue = customParam.maxValue?.toFloat(),
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.STRING -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue,
                                            currentValue = customParam.currentValue,
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.STRING,
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                                ParameterValueType.BOOLEAN -> {
                                    ModelParameter(
                                            id = customParam.id,
                                            name = customParam.name,
                                            apiName = customParam.apiName,
                                            description = customParam.description,
                                            defaultValue = customParam.defaultValue.toBoolean(),
                                            currentValue = customParam.currentValue.toBoolean(),
                                            isEnabled = customParam.isEnabled,
                                            valueType = ParameterValueType.BOOLEAN,
                                            category =
                                                    ParameterCategory.valueOf(customParam.category),
                                            isCustom = true
                                    )
                                }
                            }
                    parameters.add(param)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return parameters
    }

    // 更新配置基本信息（名称等）
    suspend fun updateConfigBase(configId: String, name: String): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig = config.copy(name = name)
        saveModelConfig(updatedConfig)
        return updatedConfig
    }

    // 更新模型配置
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String
    ): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(apiKey = apiKey, apiEndpoint = apiEndpoint, modelName = modelName)

        // 保存更新后的配置
        saveModelConfig(updatedConfig)
        
        // 如果是更新默认配置，同步更新ApiPreferences
        if (configId == DEFAULT_CONFIG_ID) {
            apiPreferences.saveApiSettings(apiKey, apiEndpoint, modelName)
        }

        return updatedConfig
    }

    // 更新模型配置 - 包含API提供商类型
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: com.ai.assistance.operit.data.model.ApiProviderType
    ): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(
                        apiKey = apiKey,
                        apiEndpoint = apiEndpoint,
                        modelName = modelName,
                        apiProviderType = apiProviderType
                )

        // 保存更新后的配置
        saveModelConfig(updatedConfig)
        
        // 如果是更新默认配置，同步更新ApiPreferences
        if (configId == DEFAULT_CONFIG_ID) {
            apiPreferences.saveApiSettings(apiKey, apiEndpoint, modelName, apiProviderType)
        }

        return updatedConfig
    }

    // 更新自定义参数
    suspend fun updateCustomParameters(configId: String, parametersJson: String): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(
                        customParameters = parametersJson,
                        hasCustomParameters = parametersJson != "[]"
                )

        // 保存更新后的配置
        saveModelConfig(updatedConfig)

        return updatedConfig
    }
}
