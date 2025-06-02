package com.ai.assistance.operit.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.data.model.VoiceProfile
import com.ai.assistance.operit.data.model.VoiceType
import com.ai.assistance.operit.data.preferences.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 文本到语音服务，负责将文本转换为语音输出
 * 支持多种语音类型、音调和语速调整，以及SSML标记
 */
class TextToSpeechService(
    private val context: Context,
    private val voicePreferences: VoicePreferences
) {
    companion object {
        private const val TAG = "TextToSpeechService"
        private const val DEFAULT_PITCH = 1.0f
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_VOLUME = 1.0f
        private const val UTTERANCE_ID_PREFIX = "tts_"
    }
    
    /**
     * TTS提供商枚举
     */
    enum class TtsProvider {
        ANDROID_BUILTIN,  // Android内置的TextToSpeech
        GOOGLE_CLOUD,     // Google Cloud的TTS服务
        AZURE,            // Microsoft Azure的TTS服务
        ELEVENLABS,       // ElevenLabs的TTS服务
        OPENAI            // OpenAI的TTS服务
    }
    
    // TTS引擎
    private var textToSpeech: TextToSpeech? = null
    
    // 音频管理器
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // 服务状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    // 当前使用的语音配置文件
    private var currentVoiceProfile: VoiceProfile? = null
    
    // TTS事件
    private val _ttsEvents = MutableSharedFlow<TTSEvent>()
    val ttsEvents: SharedFlow<TTSEvent> = _ttsEvents.asSharedFlow()
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // TTS引擎是否已初始化
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // 是否已经获取了音频焦点
    private var hasAudioFocus = false
    
    // 当前使用的TTS提供商
    private var currentProvider: TtsProvider = TtsProvider.ANDROID_BUILTIN
    
    /**
     * TTS事件
     */
    sealed class TTSEvent {
        data class Started(val utteranceId: String) : TTSEvent()
        data class Done(val utteranceId: String) : TTSEvent()
        data class Error(val utteranceId: String, val errorCode: Int) : TTSEvent()
        data object EngineReady : TTSEvent()
        data object EngineStopped : TTSEvent()
    }
    
    init {
        initializeService()
    }
    
    /**
     * 初始化TTS服务
     */
    private fun initializeService() {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 加载TTS提供商设置
            serviceScope.launch {
                try {
                    currentProvider = voicePreferences.getTtsProvider()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading TTS provider, using default", e)
                    currentProvider = TtsProvider.ANDROID_BUILTIN
                }
                
                initializeTtsEngine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeechService", e)
        }
    }
    
    /**
     * 初始化TTS引擎
     */
    private fun initializeTtsEngine() {
        // 根据当前提供商初始化相应的TTS引擎
        when (currentProvider) {
            TtsProvider.ANDROID_BUILTIN -> {
                initializeAndroidTts()
            }
            TtsProvider.GOOGLE_CLOUD -> {
                // TODO
                // 这里可以实现Google Cloud TTS的初始化
                // 暂时fallback到Android内置TTS
                Log.i(TAG, "Google Cloud TTS not implemented yet, falling back to Android TTS")
                initializeAndroidTts()
            }
            TtsProvider.AZURE -> {
                // TODO
                // 这里可以实现Azure TTS的初始化
                // 暂时fallback到Android内置TTS
                Log.i(TAG, "Azure TTS not implemented yet, falling back to Android TTS")
                initializeAndroidTts()
            }
            TtsProvider.ELEVENLABS -> {
                // TODO
                // 这里可以实现ElevenLabs TTS的初始化
                // 暂时fallback到Android内置TTS
                Log.i(TAG, "ElevenLabs TTS not implemented yet, falling back to Android TTS")
                initializeAndroidTts()
            }
            TtsProvider.OPENAI -> {
                // TODO
                // 这里可以实现OpenAI TTS的初始化
                // 暂时fallback到Android内置TTS
                Log.i(TAG, "OpenAI TTS not implemented yet, falling back to Android TTS")
                initializeAndroidTts()
            }
        }
    }
    
    /**
     * 初始化Android内置TTS引擎
     */
    private fun initializeAndroidTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupTTSEngine()
                serviceScope.launch {
                    _isInitialized.value = true
                    _ttsEvents.emit(TTSEvent.EngineReady)
                    
                    // 加载用户配置的语音设置
                    loadVoiceProfile()
                }
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech: $status")
                serviceScope.launch {
                    _isInitialized.value = false
                }
            }
        }
    }
    
    /**
     * 设置TTS引擎参数
     */
    private fun setupTTSEngine() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                serviceScope.launch {
                    _isSpeaking.value = true
                    _ttsEvents.emit(TTSEvent.Started(utteranceId))
                }
            }

            override fun onDone(utteranceId: String) {
                serviceScope.launch {
                    _isSpeaking.value = false
                    _ttsEvents.emit(TTSEvent.Done(utteranceId))
                    releaseAudioFocus()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                serviceScope.launch {
                    _isSpeaking.value = false
                    _ttsEvents.emit(TTSEvent.Error(utteranceId, -1))
                    releaseAudioFocus()
                }
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                super.onError(utteranceId, errorCode)
                serviceScope.launch {
                    _isSpeaking.value = false
                    _ttsEvents.emit(TTSEvent.Error(utteranceId, errorCode))
                    releaseAudioFocus()
                }
            }
        })
    }
    
    /**
     * 加载语音配置文件
     */
    private suspend fun loadVoiceProfile() {
        try {
            val profileId = voicePreferences.getCurrentProfileId()
            if (profileId != null) {
                val profile = voicePreferences.getProfile(profileId)
                if (profile != null) {
                    applyVoiceProfile(profile)
                    currentVoiceProfile = profile
                } else {
                    applyDefaultSettings()
                }
            } else {
                applyDefaultSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load voice profile", e)
            applyDefaultSettings()
        }
    }
    
    /**
     * 应用默认TTS设置
     */
    private suspend fun applyDefaultSettings() {
        withContext(Dispatchers.Main) {
            textToSpeech?.let { tts ->
                tts.language = Locale.getDefault()
                tts.setPitch(DEFAULT_PITCH)
                tts.setSpeechRate(DEFAULT_SPEECH_RATE)
            }
        }
    }
    
    /**
     * 应用语音配置文件设置
     */
    fun applyVoiceProfile(profile: VoiceProfile) {
        textToSpeech?.let { tts ->
            try {
                // 设置语言
                val locale = Locale.forLanguageTag(profile.language)
                tts.language = locale
                
                // 设置音调和语速
                tts.setPitch(profile.pitch)
                tts.setSpeechRate(profile.speechRate)
                
                // 选择适合类型的声音
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    selectVoice(tts, profile.voiceType, locale)
                }
                
                currentVoiceProfile = profile
            } catch (e: Exception) {
                Log.e(TAG, "Error applying voice profile", e)
            }
        }
    }
    
    /**
     * 选择合适的声音类型
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun selectVoice(tts: TextToSpeech, voiceType: VoiceType, locale: Locale) {
        val availableVoices = tts.voices
        if (availableVoices.isNullOrEmpty()) return
        
        // 根据语音类型筛选合适的声音
        val filteredVoices = availableVoices.filter { 
            it.locale.language == locale.language 
        }.let { voices ->
            when (voiceType) {
                VoiceType.MALE -> voices.filter { it.name.contains("male", ignoreCase = true) }
                VoiceType.FEMALE -> voices.filter { it.name.contains("female", ignoreCase = true) }
                VoiceType.CHILD -> voices.filter { 
                    it.name.contains("child", ignoreCase = true) ||
                    it.name.contains("kid", ignoreCase = true) 
                }
                VoiceType.ELDER -> voices.filter { 
                    it.name.contains("elder", ignoreCase = true) ||
                    it.name.contains("old", ignoreCase = true) 
                }
                VoiceType.ROBOT -> voices.filter { 
                    it.name.contains("robot", ignoreCase = true) ||
                    it.name.contains("synth", ignoreCase = true) 
                }
                VoiceType.NEUTRAL -> voices
            }
        }
        
        // 如果找到适合的声音，则选择第一个
        if (filteredVoices.isNotEmpty()) {
            tts.voice = filteredVoices.first()
        } else if (availableVoices.any { it.locale.language == locale.language }) {
            // 如果没有找到符合类型的声音，但有符合语言的声音，则使用第一个
            val defaultVoice = availableVoices.first { it.locale.language == locale.language }
            tts.voice = defaultVoice
        }
    }
    
    /**
     * 获取当前可用的语音列表
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getAvailableVoices(): List<Voice> {
        return textToSpeech?.voices?.toList() ?: emptyList()
    }
    
    /**
     * 获取支持的语言列表
     */
    fun getAvailableLanguages(): Set<Locale> {
        return textToSpeech?.availableLanguages ?: emptySet()
    }
    
    /**
     * 播放文本
     * @param text 要播放的文本
     * @param interrupt 是否中断当前播放
     */
    suspend fun speak(text: String, interrupt: Boolean = false): Boolean {
        if (text.isBlank()) return false
        if (!_isInitialized.value) return false
        
        return suspendCancellableCoroutine { continuation ->
            try {
                // 请求音频焦点
                requestAudioFocus()
                
                val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utteranceId = "$UTTERANCE_ID_PREFIX${UUID.randomUUID()}"
                
                textToSpeech?.let { tts ->
                    val result = if (text.trim().startsWith("<speak>") && text.trim().endsWith("</speak>")) {
                        // 使用SSML
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            tts.speak(
                                text,
                                mode,
                                null,
                                utteranceId
                            )
                        } else {
                            val params = HashMap<String, String>()
                            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                            tts.speak(
                                text.replace("<[^>]*>".toRegex(), ""), // 去掉SSML标签
                                mode,
                                params
                            )
                        }
                    } else {
                        // 普通文本
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            tts.speak(
                                text,
                                mode,
                                null,
                                utteranceId
                            )
                        } else {
                            val params = HashMap<String, String>()
                            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                            tts.speak(
                                text,
                                mode,
                                params
                            )
                        }
                    }
                    
                    val success = (result == TextToSpeech.SUCCESS)
                    if (success) {
                        _isSpeaking.value = true
                    }
                    continuation.resume(success)
                } ?: run {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
                continuation.resume(false)
            }
            
            continuation.invokeOnCancellation {
                stopSpeaking()
            }
        }
    }
    
    /**
     * 停止当前播放
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        releaseAudioFocus()
    }
    
    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            stopSpeaking()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            stopSpeaking()
                        }
                    }
                }
                .build()
                
            audioFocusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            stopSpeaking()
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }
    
    /**
     * 释放音频焦点
     */
    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        
        hasAudioFocus = false
    }
    
    /**
     * 设置语速
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
        currentVoiceProfile = currentVoiceProfile?.copy(speechRate = rate)
    }
    
    /**
     * 设置音调
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
        currentVoiceProfile = currentVoiceProfile?.copy(pitch = pitch)
    }
    
    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            textToSpeech?.speak("", TextToSpeech.QUEUE_ADD, params, "volume_setting")
        }
        currentVoiceProfile = currentVoiceProfile?.copy(volume = volume)
    }
    
    /**
     * 设置语言
     */
    fun setLanguage(languageCode: String): Boolean {
        try {
            val locale = Locale.forLanguageTag(languageCode)
            val result = textToSpeech?.setLanguage(locale) ?: TextToSpeech.ERROR
            val success = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
            
            if (success) {
                currentVoiceProfile = currentVoiceProfile?.copy(language = languageCode)
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language", e)
            return false
        }
    }
    
    /**
     * 检查语言是否支持
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        try {
            val locale = Locale.forLanguageTag(languageCode)
            val result = textToSpeech?.isLanguageAvailable(locale) ?: TextToSpeech.ERROR
            return (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking language support", e)
            return false
        }
    }
    
    /**
     * 设置TTS提供商
     * @param provider 要使用的TTS提供商
     * @return 是否成功切换提供商
     */
    suspend fun setTtsProvider(provider: TtsProvider): Boolean {
        if (currentProvider == provider) return true
        
        try {
            // 保存设置
            voicePreferences.setTtsProvider(provider)
            
            // 清理现有的TTS资源
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            
            // 更新当前提供商
            currentProvider = provider
            
            // 重新初始化TTS引擎
            _isInitialized.value = false
            initializeTtsEngine()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching TTS provider", e)
            return false
        }
    }
    
    /**
     * 获取当前TTS提供商
     */
    fun getCurrentTtsProvider(): TtsProvider {
        return currentProvider
    }
    
    /**
     * 清理资源
     */
    fun destroy() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            
            releaseAudioFocus()
            serviceScope.coroutineContext.cancelChildren()
            
            _isSpeaking.value = false
            _isInitialized.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying TextToSpeechService", e)
        }
    }
} 