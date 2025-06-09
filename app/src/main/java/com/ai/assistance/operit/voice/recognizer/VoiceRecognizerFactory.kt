package com.ai.assistance.operit.voice.recognizer

import android.content.Context
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.preferences.VoicePreferences
import com.ai.assistance.operit.voice.AudioStreamManager
import com.ai.assistance.operit.voice.NoiseSuppressionManager
import com.ai.assistance.operit.voice.VoiceRecognitionService.RecognitionProvider
import com.ai.assistance.operit.voice.recognizer.impl.AndroidBuiltInRecognizer
import com.ai.assistance.operit.voice.recognizer.impl.FunAudioLLMRecognizer

/**
 * 语音识别器工厂类
 * 根据配置创建适当的语音识别器实例
 */
class VoiceRecognizerFactory(
    private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val audioStreamManager: AudioStreamManager,
    private val noiseSuppressionManager: NoiseSuppressionManager,
    private val enhancedAIService: EnhancedAIService
) {
    /**
     * 创建语音识别器
     * @param provider 识别提供商
     * @return 语音识别器实例
     */
    fun createRecognizer(provider: RecognitionProvider): VoiceRecognizer {
        return when (provider) {
            RecognitionProvider.ANDROID_BUILTIN -> {
                AndroidBuiltInRecognizer(
                    context,
                    voicePreferences,
                    audioStreamManager,
                    noiseSuppressionManager
                )
            }
            RecognitionProvider.FUN_AUDIO_LLM -> {
                FunAudioLLMRecognizer(
                    context,
                    voicePreferences,
                    audioStreamManager,
                    noiseSuppressionManager,
                    enhancedAIService
                )
            }
            // 对于其他提供商，目前仍使用Android内置识别
            else -> {
                AndroidBuiltInRecognizer(
                    context,
                    voicePreferences,
                    audioStreamManager,
                    noiseSuppressionManager
                )
            }
        }
    }
} 