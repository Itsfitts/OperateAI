package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.FunctionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为功能配置创建专用的DataStore
private val Context.functionalConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "functional_configs")

/** 管理不同功能使用的模型配置 这个类用于将FunctionType映射到对应的ModelConfigID */
class FunctionalConfigManager(private val context: Context) {

    // 定义key
    companion object {
        private const val TAG = "FunctionalConfigManager"
        
        // 功能配置映射key
        val FUNCTION_CONFIG_MAPPING = stringPreferencesKey("function_config_mapping")

        // 默认映射值
        const val DEFAULT_CONFIG_ID = "default"
        
        // 特定功能类型的默认配置ID
        const val SPEECH_RECOGNITION_CONFIG_ID = "speech_recognition_default"
    }

    // Json解析器
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取ModelConfigManager实例用于配置查询
    private val modelConfigManager = ModelConfigManager(context)

    // 获取功能配置映射
    val functionConfigMappingFlow: Flow<Map<FunctionType, String>> =
            context.functionalConfigDataStore.data.map { preferences ->
                val mappingJson = preferences[FUNCTION_CONFIG_MAPPING] ?: "{}"
                if (mappingJson == "{}") {
                    // 返回默认映射，但为特定功能类型使用特定配置ID
                    createDefaultFunctionMapping()
                } else {
                    try {
                        val rawMap = json.decodeFromString<Map<String, String>>(mappingJson)
                        // 将字符串键转换为FunctionType枚举
                        rawMap.entries.associate { FunctionType.valueOf(it.key) to it.value }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析功能配置映射失败", e)
                        createDefaultFunctionMapping()
                    }
                }
            }
    
    /**
     * 创建默认的功能映射，为特定功能类型使用特定配置ID
     */
    private fun createDefaultFunctionMapping(): Map<FunctionType, String> {
        val mapping = FunctionType.values().associateWith { DEFAULT_CONFIG_ID }.toMutableMap()
        // 为SPEECH_RECOGNITION设置特定的配置ID
        mapping[FunctionType.SPEECH_RECOGNITION] = SPEECH_RECOGNITION_CONFIG_ID
        return mapping
    }

    // 初始化，确保有默认映射
    suspend fun initializeIfNeeded() {
        val mapping = functionConfigMappingFlow.first()

        // 如果映射为空或者缺少某些功能类型的映射，创建完整的默认映射
        val needsInitialization = mapping.size != FunctionType.values().size ||
                !mapping.containsKey(FunctionType.SPEECH_RECOGNITION)
        
        if (needsInitialization) {
            Log.d(TAG, "初始化功能配置映射")
            saveFunctionConfigMapping(createDefaultFunctionMapping())
        }

        // 确保ModelConfigManager也已初始化
        modelConfigManager.initializeIfNeeded()
    }

    // 保存功能配置映射
    suspend fun saveFunctionConfigMapping(mapping: Map<FunctionType, String>) {
        // 将FunctionType枚举转换为字符串键
        val stringMapping = mapping.entries.associate { it.key.name to it.value }

        context.functionalConfigDataStore.edit { preferences ->
            preferences[FUNCTION_CONFIG_MAPPING] = json.encodeToString(stringMapping)
        }
    }

    // 获取指定功能的配置ID
    suspend fun getConfigIdForFunction(functionType: FunctionType): String {
        val mapping = functionConfigMappingFlow.first()
        return mapping[functionType] ?: when(functionType) {
            FunctionType.SPEECH_RECOGNITION -> SPEECH_RECOGNITION_CONFIG_ID
            else -> DEFAULT_CONFIG_ID
        }
    }

    // 设置指定功能的配置ID
    suspend fun setConfigForFunction(functionType: FunctionType, configId: String) {
        val mapping = functionConfigMappingFlow.first().toMutableMap()
        mapping[functionType] = configId
        saveFunctionConfigMapping(mapping)
    }

    // 重置指定功能的配置为默认
    suspend fun resetFunctionConfig(functionType: FunctionType) {
        val defaultConfigId = when(functionType) {
            FunctionType.SPEECH_RECOGNITION -> SPEECH_RECOGNITION_CONFIG_ID
            else -> DEFAULT_CONFIG_ID
        }
        setConfigForFunction(functionType, defaultConfigId)
    }

    // 重置所有功能配置为默认
    suspend fun resetAllFunctionConfigs() {
        saveFunctionConfigMapping(createDefaultFunctionMapping())
    }
}
