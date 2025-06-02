package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getShowThinking: () -> Boolean,
        private val getChatHistory: () -> List<ChatMessage>,
        private val getMemory: (Boolean) -> List<Pair<String, String>>,
        private val addMessageToChat: (ChatMessage) -> Unit,
        private val updateChatStatistics: () -> Unit,
        private val saveCurrentChat: () -> Unit,
        private val showErrorMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
        private const val UI_UPDATE_INTERVAL = 500L // 0.5 seconds in milliseconds
    }

    // State flows
    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessingInput = MutableStateFlow(false)
    val isProcessingInput: StateFlow<Boolean> = _isProcessingInput.asStateFlow()

    private val _inputProcessingMessage = MutableStateFlow("")
    val inputProcessingMessage: StateFlow<String> = _inputProcessingMessage.asStateFlow()

    // 用于批处理UI更新的变量
    private var batchedAiContent: String = ""
    private var lastAiUpdateTime: Long = 0
    private var updateJob: Job? = null

    // 上一条AI消息的引用，用于直接修改而不是创建新对象
    private var lastAiMessage: ChatMessage? = null

    /** 更新用户消息 */
    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    /** 向AI发送用户消息(无附件版本) 为了保持向后兼容性 */
    fun sendUserMessage(chatId: String? = null) {
        sendUserMessage(emptyList(), chatId)
    }

    /**
     * 向AI发送用户消息(带附件版本)
     * @param attachments 要发送的附件列表
     * @param chatId 当前聊天ID，用于Web工作区
     */
    fun sendUserMessage(attachments: List<AttachmentInfo> = emptyList(), chatId: String? = null) {
        if (_userMessage.value.isBlank() && attachments.isEmpty()) {
            return
        }

        if (_isLoading.value) {
            return
        }

        // 获取消息文本
        val messageText = _userMessage.value.trim()

        // 清空输入并设置加载状态
        _userMessage.value = ""
        _isLoading.value = true

        // 确保输入处理状态可见 - 修复进度条不显示问题
        _isProcessingInput.value = true
        _inputProcessingMessage.value = "正在处理消息..."

        // 如果有附件，将其添加到消息中
        val finalMessage =
                if (attachments.isNotEmpty()) {
                    val attachmentTexts =
                            attachments.joinToString(" ") { attachment ->
                                val attachmentRef = StringBuilder("<attachment ")
                                attachmentRef.append("id=\"${attachment.filePath}\" ")
                                attachmentRef.append("filename=\"${attachment.fileName}\" ")
                                attachmentRef.append("type=\"${attachment.mimeType}\" ")

                                // 添加大小属性
                                if (attachment.fileSize > 0) {
                                    attachmentRef.append("size=\"${attachment.fileSize}\" ")
                                }

                                // 添加内容属性（如果存在）
                                if (attachment.content.isNotEmpty()) {
                                    attachmentRef.append("content=\"${attachment.content}\" ")
                                }

                                attachmentRef.append("/>")
                                attachmentRef.toString()
                            }

                    // 根据用户消息是否为空，决定如何组合最终消息
                    if (messageText.isBlank()) {
                        // 如果用户没有输入消息，直接使用附件引用
                        attachmentTexts
                    } else {
                        // 否则，将用户消息和附件引用组合在一起
                        "$messageText $attachmentTexts"
                    }
                } else {
                    messageText
                }

        // 添加用户消息到聊天历史
        addMessageToChat(ChatMessage(sender = "user", content = finalMessage))

        // 使用viewModelScope启动协程
        viewModelScope.launch {
            try {
                // 检查网络连接
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    showErrorMessage("网络连接不可用，请检查网络设置")
                    _isLoading.value = false
                    return@launch
                }

                // 获取聊天历史记录
                val history = getMemory(true)

                // 获取AI服务
                val service =
                        getEnhancedAiService()
                                ?: run {
                                    showErrorMessage("AI服务未初始化")
                                    _isLoading.value = false
                                    return@launch
                                }

                // 发送消息到AI服务
                service.sendMessage(
                        message = finalMessage,
                        onPartialResponse = { content, thinking ->
                            handlePartialResponse(content, thinking)
                        },
                        chatHistory = history,
                        onComplete = { handleResponseComplete() },
                        chatId = chatId // 传递chatId参数
                )
            } catch (e: Exception) {
                Log.e(TAG, "发送消息时出错", e)
                showErrorMessage("发送消息失败: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    var lastThinking: String? = null

    /** 处理AI部分响应 */
    private fun handlePartialResponse(content: String, thinking: String?) {
        try {
            // 如果不再加载状态，跳过响应处理
            if (!_isLoading.value) {
                Log.d(TAG, "已取消加载，跳过响应处理")
                return
            }

            if (thinking != null && getShowThinking() && thinking != lastThinking) {
                lastThinking = thinking
                // 更新或添加思考消息
                val chatHistory = getChatHistory()
                val lastUserIndex = chatHistory.indexOfLast { it.sender == "user" }
                val thinkIndex = chatHistory.indexOfLast { it.sender == "think" }

                if (thinkIndex >= 0 && thinkIndex > lastUserIndex) {
                    // 已有思考消息，更新它
                    val updatedThinkMessage = ChatMessage("think", thinking)
                    addMessageToChat(updatedThinkMessage)
                } else {
                    // 添加新的思考消息
                    addMessageToChat(ChatMessage("think", thinking))
                }
            }

            // 处理AI响应内容（现在content是完整内容而非增量内容）
            if (content.isNotEmpty()) {
                // 输出消息日志
                Log.d(TAG, "收到消息: 长度=${content.length}")

                // 获取当前时间
                val currentTime = System.currentTimeMillis()

                // 如果没有活跃的更新任务或者距离上次更新已经超过了间隔时间，则创建新的更新任务
                if (updateJob == null || currentTime - lastAiUpdateTime >= UI_UPDATE_INTERVAL) {
                    // 取消现有的更新任务（如果有）
                    updateJob?.cancel()

                    // 创建新的更新任务
                    updateJob =
                            viewModelScope.launch {
                                // 更新最后更新时间
                                lastAiUpdateTime = System.currentTimeMillis()

                                // 处理新内容(非增量)
                                appendAiContent(content)

                                // 等待下一个更新间隔
                                delay(UI_UPDATE_INTERVAL)

                                // 任务完成
                                updateJob = null
                            }
                } else {
                    // 保存最新内容到批处理缓冲区(非增量模式下直接替换而不是追加)
                    batchedAiContent = content // 直接替换为最新内容
                    Log.d(TAG, "批处理缓冲区更新: 内容长度=${batchedAiContent.length}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理AI响应时发生未处理错误", e)
            showErrorMessage("处理AI响应时发生错误: ${e.message}")
        }
    }

    /** 追加AI内容到消息 */
    private fun appendAiContent(newContent: String) {
        // 如果没有内容，直接返回
        if (newContent.isEmpty()) return

        // 获取当前聊天历史
        val chatHistory = getChatHistory()
        val lastUserIndex = chatHistory.indexOfLast { it.sender == "user" }

        val contentToUse =
                if (batchedAiContent.isNotEmpty()) {
                    batchedAiContent = ""
                    newContent
                } else {
                    newContent
                }

        if (lastAiMessage != null) {
            val newMessage =
                    ChatMessage(
                            sender = "ai",
                            content = contentToUse,
                            timestamp = lastAiMessage?.timestamp ?: System.currentTimeMillis()
                    )
            lastAiMessage = newMessage
            addMessageToChat(newMessage)
        } else {
            val lastAiIndex = chatHistory.indexOfLast { it.sender == "ai" }
            if (lastAiIndex > lastUserIndex && lastAiIndex >= 0) {
                val existingMessage = chatHistory[lastAiIndex]
                val newMessage =
                        ChatMessage(
                                sender = "ai",
                                content = contentToUse,
                                timestamp = existingMessage.timestamp
                        )
                lastAiMessage = newMessage
                addMessageToChat(newMessage)
            } else {
                val newMessage = ChatMessage("ai", contentToUse)
                lastAiMessage = newMessage
                addMessageToChat(newMessage)
            }
        }
    }

    /** 处理AI响应完成 */
    private fun handleResponseComplete() {
        // 取消任何待处理的更新任务
        updateJob?.cancel()
        updateJob = null

        try {
            // 确保最终内容被更新到UI
            if (batchedAiContent.isNotBlank()) {
                appendAiContent(batchedAiContent)
                batchedAiContent = "" // 清空批处理缓冲区
            }

            // 重置变量前，先更新聊天统计和保存
            _isLoading.value = false

            // 更新统计数据并保存
            updateChatStatistics()
            saveCurrentChat() // 恢复保存，因为我们现在不会重复创建消息
        } catch (e: Exception) {
            Log.e(TAG, "处理响应完成时发生错误", e)
        } finally {
            // 无论如何都要重置状态变量
            lastAiUpdateTime = 0
            lastAiMessage = null // 重置消息引用
        }
    }

    /** 取消当前对话 */
    fun cancelCurrentMessage() {
        viewModelScope.launch {
            // 取消任何待处理的更新任务
            updateJob?.cancel()
            updateJob = null

            // 重置批处理变量
            batchedAiContent = ""
            lastAiUpdateTime = 0
            lastAiMessage = null // 重置消息引用

            // 首先设置标志，避免其他操作继续处理
            _isLoading.value = false
            _isProcessingInput.value = false
            _inputProcessingMessage.value = ""

            // 取消当前的AI响应
            try {
                getEnhancedAiService()?.cancelConversation()
                Log.d(TAG, "成功取消AI对话")
            } catch (e: Exception) {
                Log.e(TAG, "取消对话时发生错误", e)
                showErrorMessage("取消对话时发生错误: ${e.message}")
            }

            // 保存当前对话
            saveCurrentChat()

            Log.d(TAG, "取消流程完成")
        }
    }

    /** 收集输入处理状态 */
    fun setupInputProcessingStateCollection() {
        viewModelScope.launch {
            getEnhancedAiService()?.inputProcessingState?.collect { state ->
                when (state) {
                    InputProcessingState.Idle -> {
                        _isProcessingInput.value = false
                        _inputProcessingMessage.value = ""
                    }
                    is InputProcessingState.Processing -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    is InputProcessingState.Connecting -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    is InputProcessingState.Receiving -> {
                        _isProcessingInput.value = true
                        _inputProcessingMessage.value = state.message
                    }
                    InputProcessingState.Completed -> {
                        _isProcessingInput.value = false
                    }
                    is InputProcessingState.Error -> {
                        _isProcessingInput.value = false
                        _inputProcessingMessage.value = "错误: ${state.message}"

                        // 关键修复: 将服务层错误传递到错误弹窗
                        showErrorMessage(state.message)
                    }
                }
            }
        }
    }

    /** 手动设置输入处理状态 用于在附件处理等操作过程中显示进度条 */
    fun setInputProcessingState(isProcessing: Boolean, message: String = "") {
        _isProcessingInput.value = isProcessing
        _inputProcessingMessage.value = message
    }

    /** 设置输入处理状态（新版本） 接受ToolExecutionState和AITool类型的参数，用于与工具执行状态集成 */
    fun setInputProcessingState(state: ToolExecutionState, tool: AITool? = null) {
        when (state) {
            ToolExecutionState.IDLE -> {
                _isProcessingInput.value = false
                _inputProcessingMessage.value = ""
            }
            ToolExecutionState.COMPLETED -> {
                _isProcessingInput.value = false
                _inputProcessingMessage.value = "已完成"
            }
            else -> {
                _isProcessingInput.value = true
                _inputProcessingMessage.value =
                        when (state) {
                            ToolExecutionState.EXTRACTING -> "正在解析工具..."
                            ToolExecutionState.EXECUTING -> "正在执行工具: ${tool?.name ?: ""}"
                            ToolExecutionState.FAILED -> "工具执行失败"
                            else -> "处理中..."
                        }
            }
        }
    }
}
