package com.ai.assistance.operit.voice.recognizer.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.voice.AudioStreamManager
import com.ai.assistance.operit.voice.NoiseSuppressionManager
import com.ai.assistance.operit.voice.VoiceRecognitionService
import com.ai.assistance.operit.voice.VoiceRecognitionService.Companion
import com.ai.assistance.operit.voice.recognizer.VoiceRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Android内置语音识别器
 * 封装Android SpeechRecognizer功能
 */
class AndroidBuiltInRecognizer(
    private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val audioStreamManager: AudioStreamManager,
    private val noiseSuppressionManager: NoiseSuppressionManager,
) : VoiceRecognizer {
    companion object {
        private const val TAG = "AndroidBuiltInRecognizer"
        private const val MAX_RESULTS = 3
    }

    // 语音识别器
    private var speechRecognizer: SpeechRecognizer? = null

    // 识别意图
    private var recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

    // 识别结果
    private val recognitionResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 部分识别结果
    private val partialResults = MutableSharedFlow<String>()

    // 错误
    private val errors = MutableSharedFlow<VoiceRecognitionService.RecognitionError>()

    // 是否已初始化
    private var isInitialized = false

    // 是否正在监听
    private var isListening = false

    init {
        initializeRecognizer()
    }

    /**
     * 初始化语音识别器
     */
    private fun initializeRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)

                // 准备默认识别意图
                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                }
                isInitialized = true
            } else {
                Log.e(TAG, "Speech recognition is not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer", e)
        }
    }

    override suspend fun startRecognition(continuous: Boolean, languageOverride: String?) = withContext(Dispatchers.Main) {
        if (!isInitialized) {
            initializeRecognizer()
            if (!isInitialized) {
                errors.emit(VoiceRecognitionService.RecognitionError.ServiceNotAvailable)
                return@withContext
            }
        }

        try {
            // 设置语言
            val language = languageOverride ?: voicePreferences.getPreferredLanguage()
            if (language != null && voicePreferences.getAutoDetectLanguageEnabled()) {
                val languageTag = when (language) {
                    "zh" -> "zh-CN"
                    "en" -> "en-US"
                    "jp" -> "ja-JP"
                    else -> language
                }

                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            } else {
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            }

            // 启动音频捕获
            // audioStreamManager.startAudioCapture()

            // 开始识别
            speechRecognizer?.startListening(recognizerIntent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            errors.emit(VoiceRecognitionService.RecognitionError.AudioError)
        }
    }

    override fun stopRecognition() {
        try {
            speechRecognizer?.stopListening()
            audioStreamManager.stopAudioStream()
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    override suspend fun cancelRecognition() = withContext(Dispatchers.Main) {
        try {
            speechRecognizer?.cancel()
            audioStreamManager.stopAudioStream()
            isListening = false
            return@withContext
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
            return@withContext
        }
    }

    override fun getRecognitionResultsFlow(): Flow<String> = recognitionResults.asSharedFlow()

    override fun getPartialResultsFlow(): Flow<String> = partialResults.asSharedFlow()

    override fun getErrorsFlow(): Flow<Any> = errors.asSharedFlow()

    override fun setRecognitionIntent(intent: Intent) {
        recognizerIntent = intent
    }

    override fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isInitialized = false
    }

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 语音识别监听器
     */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // RMS变化，可用于显示音量
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            buffer?.let { audioData ->
                noiseSuppressionManager.processAudioBuffer(audioData)
            }
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            val errorType = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceRecognitionService.RecognitionError.NoMatch
                SpeechRecognizer.ERROR_NETWORK -> VoiceRecognitionService.RecognitionError.NetworkError
                SpeechRecognizer.ERROR_AUDIO -> VoiceRecognitionService.RecognitionError.AudioError
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceRecognitionService.RecognitionError.InsufficientPermissions
                SpeechRecognizer.ERROR_CLIENT -> VoiceRecognitionService.RecognitionError.UnknownError(error)
                SpeechRecognizer.ERROR_SERVER -> VoiceRecognitionService.RecognitionError.ServerError(error)
                else -> VoiceRecognitionService.RecognitionError.UnknownError(error)
            }

            try {
                errors.tryEmit(errorType)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting error event", e)
            }

            isListening = false
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val recognition = matches[0]
                    try {
                        recognitionResults.tryEmit(recognition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error emitting recognition result", e)
                    }
                }
            }

            isListening = false
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val partialRecognition = matches[0]
                    try {
                        this@AndroidBuiltInRecognizer.partialResults.tryEmit(partialRecognition)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error emitting partial result", e)
                    }
                }
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // 处理其他事件
        }
    }
} 