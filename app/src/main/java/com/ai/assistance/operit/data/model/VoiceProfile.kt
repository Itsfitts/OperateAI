package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 语音配置文件数据类
 * 存储用户语音交互的个性化设置
 */
@Serializable
data class VoiceProfile(
    val id: String,
    val name: String,
    val language: String, // 语言代码，例如 "zh-CN"
    val voiceType: VoiceType,
    val speechRate: Float, // 语速，正常为1.0
    val pitch: Float, // 音调，正常为1.0
    val volume: Float, // 音量，正常为1.0
    val customizations: Map<String, @Serializable(with = AnySerializer::class) Any> = emptyMap() // 自定义设置
)

/**
 * 声音类型枚举
 */
@Serializable
enum class VoiceType {
    NEUTRAL, // 中性声音
    MALE,    // 男性声音
    FEMALE,  // 女性声音
    CHILD,   // 儿童声音
    ELDER,   // 老年声音
    ROBOT    // 机器人声音
}

/**
 * 语音输入状态数据类
 */
data class VoiceInputState(
    val isListening: Boolean = false,
    val recognitionConfidence: Float = 0.0f, // 0.0 - 1.0
    val detectedLanguage: String? = null,
    val partialResult: String = "",
    val noiseLevel: Float = 0.0f // 0.0 - 1.0
)

/**
 * 语音命令数据类
 */
@Serializable
data class VoiceCommand(
    val pattern: String, // 命令匹配模式，可以是正则表达式
    val action: VoiceActionType,
    val parameters: Map<String, @Serializable(with = AnySerializer::class) Any> = emptyMap(),
    val confirmationRequired: Boolean = false // 是否需要用户确认
)

/**
 * 语音动作类型枚举
 */
@Serializable
enum class VoiceActionType {
    STOP_CURRENT_ACTION, // 停止当前操作
    OPEN_APP,           // 打开应用
    EXECUTE_TOOL,       // 执行工具
    NAVIGATION,         // 导航到指定位置
    SEARCH,             // 搜索
    SYSTEM_CONTROL,     // 系统控制（亮度、音量等）
    MEDIA_CONTROL,      // 媒体控制（播放、暂停等）
    CUSTOM             // 自定义动作
}

/**
 * 用于序列化复杂类型
 */
@Serializable
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Any) {
        // 简单实现，实际应用中需要完整的Any类型序列化
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Any {
        // 简单实现，实际应用中需要完整的Any类型反序列化
        return decoder.decodeString()
    }
} 