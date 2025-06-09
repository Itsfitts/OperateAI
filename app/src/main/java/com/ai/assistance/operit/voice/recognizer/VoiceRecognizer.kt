package com.ai.assistance.operit.voice.recognizer

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * 语音识别器接口
 * 定义不同语音识别实现的通用方法
 */
interface VoiceRecognizer {
    /**
     * 开始语音识别
     * @param continuous 是否持续识别
     * @param languageOverride 强制使用特定语言
     */
    suspend fun startRecognition(continuous: Boolean, languageOverride: String?)
    
    /**
     * 停止语音识别
     */
    fun stopRecognition()
    
    /**
     * 取消当前识别
     */
    suspend fun cancelRecognition()
    
    /**
     * 获取识别结果流
     * @return 识别结果的Flow
     */
    fun getRecognitionResultsFlow(): Flow<String>
    
    /**
     * 获取部分识别结果流
     * @return 部分识别结果的Flow
     */
    fun getPartialResultsFlow(): Flow<String>
    
    /**
     * 获取错误流
     * @return 错误的Flow
     */
    fun getErrorsFlow(): Flow<Any>
    
    /**
     * 设置识别意图（如果适用）
     * @param intent 识别意图
     */
    fun setRecognitionIntent(intent: Intent)
    
    /**
     * 释放资源
     */
    fun release()
    
    /**
     * 检查识别器是否可用
     * @return 识别器是否可用
     */
    fun isAvailable(): Boolean
} 