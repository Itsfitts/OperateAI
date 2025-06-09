package com.ai.assistance.operit.data.model

/** 表示不同功能类型，用于指定不同功能使用的AI配置 */
enum class FunctionType {
    CHAT, // 常规对话
    SUMMARY, // 对话总结
    PERSONA, // 修改性格/提示词
    PROBLEM_LIBRARY, // 问题库处理
    VOICE, // 语音助手
    SPEECH_RECOGNITION // 语音识别
}
