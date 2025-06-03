package com.ai.assistance.operit.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
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

    // 流式朗读相关属性
    private var streamingSessionId: String? = null
    private val sentenceDelimiters = arrayOf('.', '。', '!', '！', '?', '？', ';', '；', '\n')
    private val pendingStreamText = StringBuilder()
    private var isStreamActive = false

    /**
     * TTS事件
     */
    sealed class TTSEvent {
        data class Started(val utteranceId: String) : TTSEvent()
        data class Done(val utteranceId: String) : TTSEvent()
        data class Error(val utteranceId: String, val errorCode: Int) : TTSEvent()
        data class StreamingUpdate(val utteranceId: String, val textChunk: String, val isFirst: Boolean, val isLast: Boolean) : TTSEvent()
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
                selectVoice(tts, profile.voiceType, locale)

                currentVoiceProfile = profile
            } catch (e: Exception) {
                Log.e(TAG, "Error applying voice profile", e)
            }
        }
    }

    /**
     * 选择合适的声音类型
     */
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
        if (filteredVoices.isNotEmpty()) { tts.voice = filteredVoices.first()
        } else if (availableVoices.any { it.locale.language == locale.language }) {
            // 如果没有找到符合类型的声音，但有符合语言的声音，则使用第一个
            val defaultVoice = availableVoices.first { it.locale.language == locale.language }
            tts.voice = defaultVoice
        }
    }

    /**
     * 获取当前可用的语音列表
     */
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
    fun speak(text: String, interrupt: Boolean = false) {
        if (text.isBlank()) return
        if (!_isInitialized.value) return

        serviceScope.launch(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    // 请求音频焦点
                    requestAudioFocus()

                    // val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val mode = TextToSpeech.QUEUE_FLUSH
                    val utteranceId = "$UTTERANCE_ID_PREFIX${UUID.randomUUID()}"

                    textToSpeech?.let { tts ->
                        val result = if (text.trim().startsWith("<speak>") && text.trim().endsWith("</speak>")) {
                            // 使用SSML
                            tts.speak(
                                text,
                                mode,
                                null,
                                utteranceId
                            )
                        } else {
                            // 普通文本
                            tts.speak(
                                text,
                                mode,
                                null,
                                utteranceId
                            )
                        }

                        val success = (result == TextToSpeech.SUCCESS)
                        if (success) {
                            _isSpeaking.value = true
                        }
                    } ?: run {
                        Log.e(TAG, "Error speaking text")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error speaking text", e)
                }

                continuation.invokeOnCancellation {
                    stopSpeaking()
                }
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
    }

    /**
     * 释放音频焦点
     */
    private fun releaseAudioFocus() {
        if (!hasAudioFocus) return

        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
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
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        textToSpeech?.speak("", TextToSpeech.QUEUE_ADD, params, "volume_setting")
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

    /**
     * 流式播放文本
     * 用于处理增量文本的流式阅读，可以处理实时生成的文本
     *
     * @param textChunk 新增的文本块
     * @param isFirst 是否是第一个文本块
     * @param isLast 是否是最后一个文本块
     * @param interrupt 是否中断当前播放(仅在isFirst=true时有效)
     * @return 操作是否成功
     */
    suspend fun streamingSpeak(textChunk: String, isFirst: Boolean, isLast: Boolean, interrupt: Boolean = false): Boolean {
        if (textChunk.isBlank()) return true // 空文本块被视为成功
        if (!_isInitialized.value) return false

        return suspendCancellableCoroutine { continuation ->
            try {
                val streamUtteranceId = if (isFirst) {
                    "$UTTERANCE_ID_PREFIX${UUID.randomUUID()}"
                } else {
                    "$UTTERANCE_ID_PREFIX${System.currentTimeMillis()}"
                }

                // 请求音频焦点(如果是第一个块)
                if (isFirst) {
                    requestAudioFocus()
                }

                val mode = if (isFirst && interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

                // 发送流式更新事件
                serviceScope.launch {
                    _ttsEvents.emit(TTSEvent.StreamingUpdate(
                        utteranceId = streamUtteranceId,
                        textChunk = textChunk,
                        isFirst = isFirst,
                        isLast = isLast
                    ))
                }

                // 处理文本块
                textToSpeech?.let { tts ->
                    val result = if (textChunk.trim().startsWith("<speak>") && textChunk.trim().endsWith("</speak>")) {
                        // 使用SSML
                        tts.speak(
                            textChunk,
                            mode,
                            null,
                            streamUtteranceId
                        )
                    } else {
                        // 普通文本
                        tts.speak(
                            textChunk,
                            mode,
                            null,
                            streamUtteranceId
                        )
                    }

                    val success = (result == TextToSpeech.SUCCESS)
                    if (success && isFirst) {
                        _isSpeaking.value = true
                    }

                    // 如果是最后一个文本块并且失败了，更新状态
                    if (isLast && !success) {
                        _isSpeaking.value = false
                    }

                    continuation.resume(success)
                } ?: run {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in streaming speak", e)
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                if (isLast) {
                    stopSpeaking()
                }
            }
        }
    }

    /**
     * 处理流式文本
     * 此方法接收AI生成的增量文本，并将其智能分割成适合阅读的句子块
     *
     * @param newText 新生成的文本（增量）
     * @param isComplete 文本生成是否已完成
     * @param interrupt 是否中断当前朗读
     * @return 操作是否成功
     */
    fun handleStreamingText(newText: String, isComplete: Boolean = false, interrupt: Boolean = false): Boolean {
        // 忽略空文本
        if (newText.isBlank() && !isComplete) return true

        // 首次调用时创建新的会话ID
        if (streamingSessionId == null || interrupt) {
            streamingSessionId = UUID.randomUUID().toString()
            pendingStreamText.clear()
            isStreamActive = true
        }

        // 添加新文本到缓存
        pendingStreamText.append(newText)

        // 检索可朗读的完整句子
        val speakableText = extractSpeakableSentences(pendingStreamText.toString())

        // 更新缓存，移除已处理的文本
        if (speakableText.isNotEmpty()) {
            pendingStreamText.delete(0, speakableText.length)
        }

        var success = true

        if (speakableText.isNotEmpty() || isComplete) {
            // 如果有可朗读的文本或者是最后一个文本块
            val textToSpeak = if (isComplete) {
                // 如果是最后一个块，朗读所有剩余文本
                speakableText + pendingStreamText.toString()
            } else {
                speakableText
            }

            if (textToSpeak.isNotEmpty()) {
                // 朗读文本
                serviceScope.launch {
                    try {
                        success = streamingSpeak(
                            textChunk = textToSpeak,
                            isFirst = !isStreamActive,
                            isLast = isComplete,
                            interrupt = interrupt
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling streaming text", e)
                    }
                }

                isStreamActive = true
            }
        }

        // 如果完成，重置状态
        if (isComplete) {
            streamingSessionId = null
            pendingStreamText.clear()
            isStreamActive = false
        }

        return success
    }

    /**
     * 从文本中提取完整的句子
     *
     * @param text 要处理的文本
     * @return 可朗读的完整句子
     */
    private fun extractSpeakableSentences(text: String): String {
        if (text.isEmpty()) return ""

        // 查找最后一个句子结束符
        var lastDelimiterPos = -1
        for (delimiter in sentenceDelimiters) {
            val pos = text.lastIndexOf(delimiter)
            if (pos > lastDelimiterPos) {
                lastDelimiterPos = pos
            }
        }

        // 如果找不到结束符，或者文本太短，不分割
        if (lastDelimiterPos <= 0 || text.length < 10) {
            return ""
        }

        // 返回从开始到最后一个句子结束符的文本(包括结束符)
        return text.substring(0, lastDelimiterPos + 1)
    }
}