package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.VoiceProfile
import com.ai.assistance.operit.data.model.VoiceType
import com.ai.assistance.operit.voice.TextToSpeechService
import com.ai.assistance.operit.voice.VoiceRecognitionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 语音偏好设置管理类
 */
class VoicePreferences(private val context: Context) {
    
    private val Context.voiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_preferences")
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 偏好设置键
    companion object {
        private val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val CUSTOM_WAKE_WORDS = stringPreferencesKey("custom_wake_words")
        private val VOICE_PROFILES = stringPreferencesKey("voice_profiles")
        private val CURRENT_PROFILE_ID = stringPreferencesKey("current_profile_id")
        private val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        private val RECOGNITION_PROVIDER = stringPreferencesKey("recognition_provider")
        private val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        private val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening")
        private val READ_RESPONSES = booleanPreferencesKey("read_responses")
        private val READ_RESPONSE_MODE = stringPreferencesKey("read_response_mode")
        private val AUTO_DETECT_LANGUAGE = booleanPreferencesKey("auto_detect_language")
        private val VAD_MODE_ENABLED = booleanPreferencesKey("vad_mode_enabled")
        private val TAG = "VoicePreferences"
    }
    
    /**
     * 是否启用语音功能
     */
    suspend fun isVoiceEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[VOICE_ENABLED] ?: false
        }.first()
    }
    
    /**
     * 设置是否启用语音功能
     * @param enabled 是否启用
     */
    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[VOICE_ENABLED] = enabled
        }
    }
    
    /**
     * 是否启用唤醒词功能
     */
    suspend fun isWakeWordEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[WAKE_WORD_ENABLED] ?: false
        }.first()
    }
    
    /**
     * 设置是否启用唤醒词功能
     * @param enabled 是否启用
     */
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[WAKE_WORD_ENABLED] = enabled
        }
    }
    
    /**
     * 获取自定义唤醒词列表
     */
    suspend fun getCustomWakeWords(): List<String> {
        return context.voiceDataStore.data.map { preferences ->
            val wakeWordsJson = preferences[CUSTOM_WAKE_WORDS] ?: "[]"
            try {
                json.decodeFromString<List<String>>(wakeWordsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }.first()
    }
    
    /**
     * 保存自定义唤醒词列表
     * @param wakeWords 唤醒词列表
     */
    suspend fun saveCustomWakeWords(wakeWords: List<String>) {
        context.voiceDataStore.edit { preferences ->
            preferences[CUSTOM_WAKE_WORDS] = json.encodeToString(wakeWords)
        }
    }
    
    /**
     * 获取所有语音配置文件
     */
    suspend fun getAllVoiceProfiles(): List<VoiceProfile> {
        return context.voiceDataStore.data.map { preferences ->
            val profilesJson = preferences[VOICE_PROFILES] ?: "[]"
            try {
                json.decodeFromString<List<VoiceProfile>>(profilesJson)
            } catch (e: Exception) {
                emptyList()
            }
        }.first()
    }
    
    /**
     * 根据ID获取特定的语音配置文件
     * @param profileId 配置文件ID
     * @return 找到的配置文件，如果没有找到则返回null
     */
    suspend fun getProfile(profileId: String): VoiceProfile? {
        return getAllVoiceProfiles().find { it.id == profileId }
    }
    
    /**
     * 保存语音配置文件
     * @param profile 要保存的配置文件
     */
    suspend fun saveVoiceProfile(profile: VoiceProfile) {
        context.voiceDataStore.edit { preferences ->
            val profiles = getAllVoiceProfiles().toMutableList()
            val index = profiles.indexOfFirst { it.id == profile.id }
            
            if (index >= 0) {
                // 更新现有配置
                profiles[index] = profile
            } else {
                // 添加新配置
                profiles.add(profile)
            }
            
            preferences[VOICE_PROFILES] = json.encodeToString(profiles)
        }
    }
    
    /**
     * 删除语音配置文件
     * @param profileId 要删除的配置文件ID
     */
    suspend fun deleteVoiceProfile(profileId: String) {
        context.voiceDataStore.edit { preferences ->
            val profiles = getAllVoiceProfiles().filter { it.id != profileId }
            preferences[VOICE_PROFILES] = json.encodeToString(profiles)
        }
    }
    
    /**
     * 获取当前配置文件ID
     */
    suspend fun getCurrentProfileId(): String? {
        return context.voiceDataStore.data.map { preferences ->
            preferences[CURRENT_PROFILE_ID]
        }.first()
    }
    
    /**
     * 设置当前配置文件ID
     * @param profileId 配置文件ID
     */
    suspend fun setCurrentProfileId(profileId: String) {
        context.voiceDataStore.edit { preferences ->
            preferences[CURRENT_PROFILE_ID] = profileId
        }
    }
    
    /**
     * 获取默认语音配置文件
     * 如果没有设置，则返回一个新的默认配置
     */
    suspend fun getDefaultVoiceProfile(): VoiceProfile {
        val currentId = getCurrentProfileId()
        if (currentId != null) {
            val profiles = getAllVoiceProfiles()
            profiles.find { it.id == currentId }?.let { return it }
        }
        
        // 如果没有找到配置文件，创建一个默认的
        val locale = Locale.getDefault()
        return VoiceProfile(
            id = "default",
            name = "默认配置",
            language = locale.language + (if (locale.country.isEmpty()) "" else "-" + locale.country),
            voiceType = VoiceType.NEUTRAL,
            speechRate = 1.0f,
            pitch = 1.0f,
            volume = 1.0f,
            customizations = emptyMap()
        )
    }
    
    /**
     * 获取首选语言
     */
    suspend fun getPreferredLanguage(): String? {
        return context.voiceDataStore.data.map { preferences ->
            preferences[PREFERRED_LANGUAGE]
        }.first()
    }
    
    /**
     * 设置首选语言
     * @param languageCode 语言代码
     */
    suspend fun setPreferredLanguage(languageCode: String) {
        context.voiceDataStore.edit { preferences ->
            preferences[PREFERRED_LANGUAGE] = languageCode
        }
    }
    
    /**
     * 获取识别提供商
     */
    suspend fun getRecognitionProvider(): VoiceRecognitionService.RecognitionProvider {
        return context.voiceDataStore.data.map { preferences ->
            val providerName = preferences[RECOGNITION_PROVIDER] 
                ?: VoiceRecognitionService.RecognitionProvider.GOOGLE_MLKIT.name
            
            try {
                VoiceRecognitionService.RecognitionProvider.valueOf(providerName)
            } catch (e: Exception) {
                VoiceRecognitionService.RecognitionProvider.GOOGLE_MLKIT
            }
        }.first()
    }
    
    /**
     * 设置识别提供商
     * @param provider 提供商
     */
    suspend fun setRecognitionProvider(provider: VoiceRecognitionService.RecognitionProvider) {
        context.voiceDataStore.edit { preferences ->
            preferences[RECOGNITION_PROVIDER] = provider.name
        }
    }
    
    /**
     * 获取TTS提供商
     */
    suspend fun getTtsProvider(): TextToSpeechService.TtsProvider {
        return context.voiceDataStore.data.map { preferences ->
            val providerName = preferences[TTS_PROVIDER] 
                ?: TextToSpeechService.TtsProvider.ANDROID_BUILTIN.name
            
            try {
                TextToSpeechService.TtsProvider.valueOf(providerName)
            } catch (e: Exception) {
                TextToSpeechService.TtsProvider.ANDROID_BUILTIN
            }
        }.first()
    }
    
    /**
     * 设置TTS提供商
     * @param provider 提供商
     */
    suspend fun setTtsProvider(provider: TextToSpeechService.TtsProvider) {
        context.voiceDataStore.edit { preferences ->
            preferences[TTS_PROVIDER] = provider.name
        }
    }
    
    /**
     * 是否启用连续监听模式
     */
    suspend fun getContinuousListeningEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[CONTINUOUS_LISTENING] ?: false
        }.first()
    }

    /**
     * 是否启用连续监听模式
     */
    suspend fun getReadResponsesEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[CONTINUOUS_LISTENING] ?: false
        }.first()
    }
    
    /**
     * 设置是否启用连续监听模式
     * @param enabled 是否启用
     */
    suspend fun setContinuousListeningEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[CONTINUOUS_LISTENING] = enabled
        }
    }
    
    /**
     * 是否朗读AI响应
     */
    suspend fun isReadResponsesEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[READ_RESPONSES] ?: true
        }.first()
    }
    
    /**
     * 设置是否朗读AI响应
     * @param enabled 是否启用
     */
    suspend fun setReadResponsesEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[READ_RESPONSES] = enabled
        }
    }
    
    /**
     * 获取朗读响应模式
     */
    suspend fun getReadResponseMode(): ReadResponseMode {
        return context.voiceDataStore.data.map { preferences ->
            val modeName = preferences[READ_RESPONSE_MODE] ?: ReadResponseMode.FULL.name
            try {
                ReadResponseMode.valueOf(modeName)
            } catch (e: Exception) {
                ReadResponseMode.FULL
            }
        }.first()
    }
    
    /**
     * 设置朗读响应模式
     * @param mode 模式
     */
    suspend fun setReadResponseMode(mode: ReadResponseMode) {
        context.voiceDataStore.edit { preferences ->
            preferences[READ_RESPONSE_MODE] = mode.name
        }
    }
    
    /**
     * 是否自动检测语言
     */
    suspend fun getAutoDetectLanguageEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[AUTO_DETECT_LANGUAGE] ?: true
        }.first()
    }
    
    /**
     * 设置是否自动检测语言
     * @param enabled 是否启用
     */
    suspend fun setAutoDetectLanguageEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[AUTO_DETECT_LANGUAGE] = enabled
        }
    }
    
    /**
     * 是否启用VAD模式
     */
    suspend fun isVadModeEnabled(): Boolean {
        return context.voiceDataStore.data.map { preferences ->
            preferences[VAD_MODE_ENABLED] ?: true // 默认启用
        }.first()
    }
    
    /**
     * 设置是否启用VAD模式
     * @param enabled 是否启用
     */
    suspend fun setVadModeEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[VAD_MODE_ENABLED] = enabled
        }
    }
    
    /**
     * 朗读响应模式
     */
    enum class ReadResponseMode {
        FULL,       // 完整朗读
        SUMMARY,    // 仅朗读摘要
        SMART,       // 智能模式（根据响应长度和内容决定）
        STREAMING       // 流式阅读（增量阅读）
    }
} 