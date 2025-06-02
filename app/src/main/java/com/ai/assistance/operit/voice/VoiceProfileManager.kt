package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.VoiceProfile
import com.ai.assistance.operit.data.model.VoiceType
import com.ai.assistance.operit.data.preferences.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * 语音配置文件管理器
 * 用于管理用户的语音设置配置
 */
class VoiceProfileManager(
    private val context: Context,
    private val voicePreferences: VoicePreferences
) {
    private val managerScope = CoroutineScope(Dispatchers.Default + Job())
    
    // 当前语音配置文件
    private val _currentProfile = MutableStateFlow<VoiceProfile?>(null)
    val currentProfile: StateFlow<VoiceProfile?> = _currentProfile.asStateFlow()
    
    // 所有可用的语音配置文件
    private val _availableProfiles = MutableStateFlow<List<VoiceProfile>>(emptyList())
    val availableProfiles: StateFlow<List<VoiceProfile>> = _availableProfiles.asStateFlow()
    
    init {
        loadProfiles()
    }
    
    /**
     * 加载所有语音配置文件
     */
    private fun loadProfiles() {
        managerScope.launch {
            try {
                // 从偏好设置获取所有配置文件
                val profiles = voicePreferences.getAllVoiceProfiles()
                _availableProfiles.value = profiles
                
                // 加载当前选中的配置文件
                val currentProfileId = voicePreferences.getCurrentProfileId()
                val profile = profiles.find { it.id == currentProfileId }
                    ?: createDefaultProfile() // 如果找不到，创建一个默认的
                
                _currentProfile.value = profile
            } catch (e: Exception) {
                Log.e(TAG, "Error loading voice profiles: ${e.message}")
                
                // 确保至少有一个默认配置文件
                if (_availableProfiles.value.isEmpty()) {
                    val defaultProfile = createDefaultProfile()
                    _availableProfiles.value = listOf(defaultProfile)
                    _currentProfile.value = defaultProfile
                }
            }
        }
    }
    
    /**
     * 创建默认的语音配置文件
     */
    private fun createDefaultProfile(): VoiceProfile {
        val locale = Locale.getDefault()
        val defaultProfile = VoiceProfile(
            id = UUID.randomUUID().toString(),
            name = "默认配置",
            language = locale.language + (if (locale.country.isEmpty()) "" else "-" + locale.country),
            voiceType = VoiceType.NEUTRAL,
            speechRate = 1.0f,
            pitch = 1.0f,
            volume = 1.0f,
            customizations = emptyMap()
        )
        
        // 保存到偏好设置
        managerScope.launch {
            voicePreferences.saveVoiceProfile(defaultProfile)
            voicePreferences.setCurrentProfileId(defaultProfile.id)
        }
        
        return defaultProfile
    }
    
    /**
     * 创建新的语音配置文件
     * @param name 配置文件名称
     * @param language 语言代码
     * @param voiceType 声音类型
     * @param speechRate 语速
     * @param pitch 音调
     * @param volume 音量
     * @param customizations 自定义设置
     * @return 新创建的配置文件
     */
    fun createProfile(
        name: String,
        language: String = Locale.getDefault().language,
        voiceType: VoiceType = VoiceType.NEUTRAL,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        customizations: Map<String, Any> = emptyMap()
    ): VoiceProfile {
        val newProfile = VoiceProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            language = language,
            voiceType = voiceType,
            speechRate = speechRate,
            pitch = pitch,
            volume = volume,
            customizations = customizations
        )
        
        // 保存到偏好设置
        managerScope.launch {
            voicePreferences.saveVoiceProfile(newProfile)
            
            // 更新本地缓存
            val currentProfiles = _availableProfiles.value.toMutableList()
            currentProfiles.add(newProfile)
            _availableProfiles.value = currentProfiles
        }
        
        return newProfile
    }
    
    /**
     * 更新现有的语音配置文件
     * @param profile 要更新的配置文件
     */
    fun updateProfile(profile: VoiceProfile) {
        managerScope.launch {
            voicePreferences.saveVoiceProfile(profile)
            
            // 更新本地缓存
            val currentProfiles = _availableProfiles.value.toMutableList()
            val index = currentProfiles.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                currentProfiles[index] = profile
                _availableProfiles.value = currentProfiles
                
                // 如果更新的是当前配置文件，也更新当前状态
                if (_currentProfile.value?.id == profile.id) {
                    _currentProfile.value = profile
                }
            }
        }
    }
    
    /**
     * 删除语音配置文件
     * @param profileId 要删除的配置文件ID
     * @return 是否删除成功
     */
    fun deleteProfile(profileId: String): Boolean {
        // 不允许删除当前使用的配置文件
        if (_currentProfile.value?.id == profileId) {
            return false
        }
        
        managerScope.launch {
            voicePreferences.deleteVoiceProfile(profileId)
            
            // 更新本地缓存
            val currentProfiles = _availableProfiles.value.toMutableList()
            currentProfiles.removeAll { it.id == profileId }
            _availableProfiles.value = currentProfiles
        }
        
        return true
    }
    
    /**
     * 切换当前使用的语音配置文件
     * @param profileId 要切换到的配置文件ID
     * @return 是否切换成功
     */
    fun switchProfile(profileId: String): Boolean {
        val profile = _availableProfiles.value.find { it.id == profileId }
        if (profile != null) {
            _currentProfile.value = profile
            
            managerScope.launch {
                voicePreferences.setCurrentProfileId(profileId)
            }
            
            return true
        }
        return false
    }
    
    /**
     * 获取指定的语音配置文件
     * @param profileId 配置文件ID
     * @return 找到的配置文件，如果没有找到则返回null
     */
    fun getProfile(profileId: String): VoiceProfile? {
        return _availableProfiles.value.find { it.id == profileId }
    }
    
    companion object {
        private const val TAG = "VoiceProfileManager"
    }
} 