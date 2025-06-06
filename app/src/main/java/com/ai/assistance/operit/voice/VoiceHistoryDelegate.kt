package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceHistoryDelegate(
    private val context: Context,
    private val voiceModelScope: CoroutineScope,
    private val onTokenStatisticsLoaded: (Int, Int) -> Unit,
    private val getEnhancedAiService: () -> EnhancedAIService?,
    private val getTokenCounts: () -> Pair<Int, Int> = { Pair(0, 0) } // 获取当前token统计数据的回调
) {
    companion object {
        private const val TAG = "VoiceHistoryDelegate"
        // 聊天总结的消息数量阈值和间隔
        private const val SUMMARY_CHUNK_SIZE = 5
    }

    // 聊天历史管理器
    private val chatHistoryManager = ChatHistoryManager.getInstance(context)

    // API设置，用于获取内存优化设置
    private val apiPreferences = ApiPreferences(context)

    // 加载状态跟踪
    private var historiesLoaded = false
    private var currentChatIdLoaded = false

    // 防止重复创建聊天
    private var initialChatCreated = false

    // State flows
    private val _voiceHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val voiceHistory: StateFlow<List<ChatMessage>> = _voiceHistory.asStateFlow()

    private val _showVoiceHistorySelector = MutableStateFlow(false)
    val showVoiceHistorySelector: StateFlow<Boolean> = _showVoiceHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // 跟踪是否已经创建了摘要
    private var summarizationPerformed = false

    init {
        // 初始化数据收集
        initializeDataCollection()
    }

    private fun initializeDataCollection() {
        // 收集聊天历史列表
        voiceModelScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                Log.d(TAG, "收到聊天历史列表更新：${histories.size} 个聊天")
                _chatHistories.value = histories
                historiesLoaded = true

                // 尝试根据当前ID加载对应的聊天
                val currentId = _currentChatId.value
                if (currentId != null) {
                    Log.d(TAG, "尝试加载当前聊天：$currentId")

                    // 直接从数据库加载当前聊天的消息
                    try {
                        val messages = chatHistoryManager.loadChatMessages(currentId)
                        Log.d(TAG, "从数据库加载当前聊天消息：${messages.size} 条")

                        // 无论消息是否为空，都更新聊天历史
                        _voiceHistory.value = messages

                        // 查找对应的聊天元数据，加载token统计
                        val selectedChat = histories.find { it.id == currentId }
                        if (selectedChat != null) {
                            onTokenStatisticsLoaded(
                                selectedChat.inputTokens,
                                selectedChat.outputTokens
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "加载当前聊天消息失败", e)
                    }
                }

                checkIfShouldCreateNewChat()
            }
        }

        // 收集当前聊天ID
        voiceModelScope.launch {
            chatHistoryManager.currentChatIdFlow.collect { chatId ->
                Log.d(TAG, "收到当前聊天ID更新：$chatId")
                val oldId = _currentChatId.value
                _currentChatId.value = chatId

                if (currentChatIdLoaded) {
                    // 如果ID变更，直接从数据库加载消息
                    if (chatId != null && chatId != oldId) {
                        loadChatMessages(chatId)
                    }
                    return@collect
                }

                currentChatIdLoaded = true
                checkIfShouldCreateNewChat()

                if (chatId != null) {
                    // 初始化时从数据库加载消息
                    loadChatMessages(chatId)
                }
            }
        }
    }

    /** 加载聊天消息的辅助方法 */
    private suspend fun loadChatMessages(chatId: String) {
        try {
            // 直接从数据库加载消息
            val messages = chatHistoryManager.loadChatMessages(chatId)
            Log.d(TAG, "加载聊天 $chatId 的消息：${messages.size} 条")

            // 无论消息是否为空，都更新聊天历史
            _voiceHistory.value = messages
            // 查找聊天元数据，更新token统计
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                onTokenStatisticsLoaded(selectedChat.inputTokens, selectedChat.outputTokens)

            }
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天消息失败", e)
        }
    }

    /** 检查是否应该创建新聊天，确保同步 */
    fun checkIfShouldCreateNewChat(): Boolean {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!historiesLoaded || !currentChatIdLoaded || initialChatCreated) {
            return false
        }

        // 如果没有当前对话ID，说明需要创建一个新对话
        if (_currentChatId.value == null) {
            initialChatCreated = true
            return true
        } else {
            // 即使有当前对话ID，也标记为已创建初始对话，避免重复创建
            initialChatCreated = true
            return false
        }
    }

    /** 创建新的聊天 */
    fun createNewChat() {
        voiceModelScope.launch {
            try {
                // 保存当前聊天
                val currentTokenCounts = getCurrentTokenCounts()
                saveCurrentChat(currentTokenCounts.first, currentTokenCounts.second)

                // 创建新聊天
                val newChat = chatHistoryManager.createNewChat()

                // 更新UI状态
                _voiceHistory.value = newChat.messages

                // 通知ViewModel重置token统计
                onTokenStatisticsLoaded(0, 0)

                Log.d(TAG, "新聊天创建完成")
            } catch (e: Exception) {
                Log.e(TAG, "创建新聊天失败", e)
            }
        }
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        voiceModelScope.launch {
            try {
                Log.d(TAG, "开始切换聊天：$chatId")

                // 保存当前聊天
                val currentTokenCounts = getCurrentTokenCounts()
                saveCurrentChat(currentTokenCounts.first, currentTokenCounts.second)

                // 切换到选定的聊天
                chatHistoryManager.setCurrentChatId(chatId)

                // 加载消息
                loadChatMessages(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "切换聊天失败", e)
            }
        }
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        voiceModelScope.launch {
            try {
                // 如果要删除的是当前聊天，先创建一个新的聊天
                if (chatId == _currentChatId.value) {
                    // 当删除当前聊天时，不需要保存统计数据，因为我们会创建一个新的聊天
                    val newChat = chatHistoryManager.createNewChat()
                    _voiceHistory.value = newChat.messages

                    // 通知ViewModel重置token统计
                    onTokenStatisticsLoaded(0, 0)
                } else {
                    // 如果删除的不是当前聊天，先保存当前聊天的统计数据
                    val currentTokenCounts = getCurrentTokenCounts()
                    saveCurrentChat(currentTokenCounts.first, currentTokenCounts.second)
                }

                // 删除聊天历史
                chatHistoryManager.deleteChatHistory(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "删除聊天历史失败", e)
            }
        }
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        voiceModelScope.launch {
            try {
                // 先保存当前聊天（这一步可能是多余的，但为安全起见）
                val currentTokenCounts = getCurrentTokenCounts()
                saveCurrentChat(currentTokenCounts.first, currentTokenCounts.second)

                // 清空聊天历史
                _voiceHistory.value = emptyList()

                Log.d(TAG, "清空聊天")

                // 通知ViewModel重置token统计
                onTokenStatisticsLoaded(0, 0)

                // 创建新的聊天ID
                val newChatId = UUID.randomUUID().toString()
                chatHistoryManager.setCurrentChatId(newChatId)
                _currentChatId.value = newChatId

                Log.d(TAG, "聊天记录已清空")
            } catch (e: Exception) {
                Log.e(TAG, "清空聊天记录失败", e)
            }
        }
    }

    /** 保存当前聊天到持久存储 */
    fun saveCurrentChat(inputTokens: Int = 0, outputTokens: Int = 0) {
        voiceModelScope.launch {
            try {
                val currentId = _currentChatId.value ?: UUID.randomUUID().toString()

                // 仅在有消息时保存
                if (_voiceHistory.value.isNotEmpty()) {
                    // 更新token计数
                    chatHistoryManager.updateChatTokenCounts(currentId, inputTokens, outputTokens)

                    // 设置当前聊天ID（如果尚未设置）
                    if (_currentChatId.value == null) {
                        chatHistoryManager.setCurrentChatId(currentId)
                        _currentChatId.value = currentId
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save chat history", e)
            }
        }
    }

    /** 根据第一条用户消息生成聊天标题 */
    private fun generateChatTitle(): String {
        val firstUserMessage = _voiceHistory.value.firstOrNull { it.sender == "user" }?.content
        return if (firstUserMessage != null) {
            // 截取前20个字符作为标题，并添加省略号
            if (firstUserMessage.length > 20) {
                "${firstUserMessage.take(20)}..."
            } else {
                firstUserMessage
            }
        } else {
            "新对话"
        }
    }

    /** 添加消息到当前聊天 */
    fun addMessageToChat(message: ChatMessage) {
        voiceModelScope.launch {
            try {
                // 获取当前消息列表
                val currentMessages = _voiceHistory.value
                val currentTypeIndex = currentMessages.indexOfLast { it.sender == message.sender }
                val lastUserIndex = currentMessages.indexOfLast { it.sender == "user" }
                val chatId = _currentChatId.value

                // 如果没有当前聊天ID，无法添加消息
                if (chatId == null) {
                    Log.e(TAG, "尝试添加消息但没有当前聊天ID")
                    return@launch
                }

                // 处理AI消息更新
                if (message.sender == "ai" && currentTypeIndex >= 0) {
                    // 获取当前AI消息
                    val currentAiMessage = currentMessages[currentTypeIndex]

                    // 如果内容相同，不需要更新
                    if (currentAiMessage.content == message.content) {
                        return@launch
                    }

                    // 检查是否为同一条消息的更新（使用时间戳）
                    val isSameMessage = currentAiMessage.timestamp == message.timestamp

                    // 更新UI中的消息
                    val updatedMessages = currentMessages.toMutableList()

                    if (isSameMessage || currentTypeIndex > lastUserIndex) {
                        // 如果是同一条消息或者是最后一个用户消息之后的AI消息，则替换
                        Log.d(TAG, "替换现有AI消息: ID=${chatId}, 时间戳=${message.timestamp}")
                        updatedMessages[currentTypeIndex] = message

                        // 更新内存中的聊天历史
                        _voiceHistory.value = updatedMessages

                        // 使用Room更新消息，避免重复创建
                        chatHistoryManager.updateMessage(chatId, message)
                        return@launch
                    }
                }

                // 处理思考消息（替换现有的思考消息或添加新的）
                if (message.sender == "think") {
                    val thinkIndex = currentMessages.indexOfLast { it.sender == "think" }
                    if (thinkIndex >= 0 && thinkIndex > lastUserIndex) {
                        // 已有思考消息且在最后一个用户消息之后，更新它
                        val updatedMessages = currentMessages.toMutableList()
                        updatedMessages[thinkIndex] = message
                        _voiceHistory.value = updatedMessages

                        // 因为思考消息不需要持久化，所以这里不需要保存到数据库
                        return@launch
                    }
                }

                // 如果执行到这里，说明消息是新消息（不是对现有消息的更新），直接添加
                val newMessages = currentMessages + message
                _voiceHistory.value = newMessages

                // 保存到数据库（思考消息除外）
                if (message.sender != "think") {
                    chatHistoryManager.addMessage(chatId, message)

                    // 更新token计数
                    val tokenCounts = getCurrentTokenCounts()
                    chatHistoryManager.updateChatTokenCounts(
                        chatId,
                        tokenCounts.first,
                        tokenCounts.second
                    )

                    // 修复：如果是用户消息且是第一条，自动将其设为对话标题
                    if (message.sender == "user" && currentMessages.none { it.sender == "user" }) {
                        // 生成标题
                        val title = generateChatTitle()
                        // 更新聊天标题
                        chatHistoryManager.updateChatTitle(chatId, title)
                        Log.d(TAG, "自动更新对话标题: $title")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加消息到聊天失败", e)
            }
        }
    }

    /** 更新整个聊天历史 用于编辑或回档等操作 */
    fun updateChatHistory(newHistory: List<ChatMessage>) {
        _voiceHistory.value = newHistory.toList()
    }

    /** 检查是否应该生成总结 */
    private fun shouldGenerateSummary(messages: List<ChatMessage>): Boolean {
        // 获取用户和AI消息
        val userAiMessages = messages.filter { it.sender == "user" || it.sender == "ai" }

        // 检查最近的消息中是否已经有总结消息
        val recentMessages =
            messages
                .filter {
                    it.sender == "user" || it.sender == "ai" || it.sender == "summary"
                }
                .takeLast(minOf(SUMMARY_CHUNK_SIZE * 2, messages.size))
        val hasRecentSummary = recentMessages.any { it.sender == "summary" }

        // 如果最近已经有总结消息，不需要再生成
        if (hasRecentSummary) {
            Log.d(TAG, "最近消息中已有总结，跳过总结生成")
            return false
        }

        // 如果消息数量不足，不需要总结
        if (userAiMessages.size < SUMMARY_CHUNK_SIZE * 2) {
            Log.d(TAG, "消息数量不足，不生成总结. 当前消息数: ${userAiMessages.size}")
            return false
        }

        // 找到最后一条总结消息的位置
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

        // 计算自上次总结后的新消息数量
        val newMessagesCount =
            if (lastSummaryIndex == -1) {
                // 如果没有总结消息，则所有消息都是新消息
                userAiMessages.size
            } else {
                // 计算最后一条总结后的user/ai消息数量
                var count = 0
                for (i in (lastSummaryIndex + 1) until messages.size) {
                    if (messages[i].sender == "user" || messages[i].sender == "ai") {
                        count++
                    }
                }
                count
            }

        // 只有当新消息数量达到阈值时才生成总结
        val shouldSummarize = newMessagesCount >= SUMMARY_CHUNK_SIZE * 2

        if (shouldSummarize) {
            Log.d(TAG, "需要生成总结. 自上次总结后的新消息数量: $newMessagesCount")
        } else {
            Log.d(TAG, "未达到总结条件. 自上次总结后的新消息数量: $newMessagesCount")
        }

        return shouldSummarize
    }

    /** 正常添加消息到聊天 */
    private fun addMessageNormally(
        message: ChatMessage,
        currentMessages: List<ChatMessage>,
        currentTypeIndex: Int,
        lastUserIndex: Int
    ): List<ChatMessage> {
        // 使用原来的逻辑
        when (message.sender) {
            "think" -> {
                // 处理思考消息：如果已有思考消息且在最后一条用户消息之后，则更新它
                if (currentTypeIndex >= 0 && currentTypeIndex > lastUserIndex) {
                    val newMessages = currentMessages.toMutableList()
                    newMessages[currentTypeIndex] = message
                    _voiceHistory.value = newMessages
                } else {
                    // 否则添加新消息
                    _voiceHistory.value = currentMessages + message
                }
            }
            "ai" -> {
                // 处理AI消息：如果已有AI消息且在最后一条用户消息之后，则更新它
                if (currentTypeIndex >= 0 && currentTypeIndex > lastUserIndex) {
                    val newMessages = currentMessages.toMutableList()
                    newMessages[currentTypeIndex] = message
                    _voiceHistory.value = newMessages
                } else {
                    // 否则添加新消息
                    _voiceHistory.value = currentMessages + message
                }
            }
            else -> {
                // 对于其他消息类型（如用户消息），直接添加到列表末尾
                _voiceHistory.value = currentMessages + message
            }
        }

        return _voiceHistory.value
    }

    /** 生成记忆总结 */
    private suspend fun summarizeMemory(messages: List<ChatMessage>) {
        try {
            Log.d(TAG, "开始生成记忆总结...")

            // 只有用户和AI消息才会被总结，过滤其他类型的消息
            val messagesForSummary = messages.filter { it.sender == "user" || it.sender == "ai" }

            // 找到最后一条总结消息
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

            // 获取上一条摘要内容（如果存在）
            val previousSummary =
                if (lastSummaryIndex != -1) {
                    messages[lastSummaryIndex].content.trim()
                } else {
                    null
                }

            // 计算要总结的消息范围
            val messagesToSummarize =
                if (lastSummaryIndex == -1) {
                    // 如果没有总结消息，则总结最前面的SUMMARY_CHUNK_SIZE条
                    messagesForSummary.take(SUMMARY_CHUNK_SIZE)
                } else {
                    // 从最后一条总结后开始，选取最多SUMMARY_CHUNK_SIZE条消息
                    val messagesAfterLastSummary = mutableListOf<ChatMessage>()
                    for (i in (lastSummaryIndex + 1) until messages.size) {
                        if (messages[i].sender == "user" || messages[i].sender == "ai") {
                            messagesAfterLastSummary.add(messages[i])
                            if (messagesAfterLastSummary.size >= SUMMARY_CHUNK_SIZE) {
                                break
                            }
                        }
                    }
                    messagesAfterLastSummary
                }

            if (messagesToSummarize.isEmpty()) {
                Log.d(TAG, "没有新消息需要总结")
                return
            }

            Log.d(TAG, "将总结 ${messagesToSummarize.size} 条消息")
            
            // 等待一段时间以允许创建AI服务
            kotlinx.coroutines.delay(600)

            // 获取API服务实例
            val enhancedAiService = getEnhancedAiService()
            if (enhancedAiService == null) {
                Log.e(TAG, "AI服务不可用，无法生成总结")
                return
            }

            try {
                // 将消息转换为AI可以处理的格式
                val conversationToSummarize =
                    messagesToSummarize.mapIndexed { index, message ->
                        val role = if (message.sender == "user") "user" else "assistant"
                        Pair(role, "#${index + 1}: ${message.content}")
                    }

                Log.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")

                // 如果有上一条摘要，传入它作为上下文
                val summary =
                    if (previousSummary != null) {
                        Log.d(TAG, "使用上一条摘要作为上下文生成新的总结")
                        enhancedAiService.generateSummary(
                            conversationToSummarize,
                            previousSummary
                        )
                    } else {
                        enhancedAiService.generateSummary(conversationToSummarize)
                    }

                Log.d(TAG, "AI生成总结完成: ${summary.take(50)}...")

                if (summary.isBlank()) {
                    Log.e(TAG, "AI生成的总结内容为空，放弃本次总结")
                    return
                }

                // 创建总结消息
                val summaryMessage =
                    ChatMessage(
                        sender = "summary",
                        content = summary.trim(),
                        timestamp = System.currentTimeMillis()
                    )

                // 获取当前最新的消息列表
                val currentMessages = _voiceHistory.value
                val newMessages = currentMessages.toMutableList()

                // 完全重写总结插入位置逻辑，确保总结被插入到正确位置
                val insertPosition = findProperSummaryPosition(newMessages)
                Log.d(TAG, "计算出的总结插入位置: $insertPosition，总消息数量: ${newMessages.size}")

                // 在确定的位置插入总结消息
                newMessages.add(insertPosition, summaryMessage)
                Log.d(TAG, "在索引 $insertPosition 处添加总结消息，总消息数量: ${newMessages.size}")

                // 更新消息列表
                _voiceHistory.value = newMessages

                val totalMessages = newMessages.count { it.sender == "user" || it.sender == "ai" }
                Log.d(TAG, "总结完成，总消息数 $totalMessages")
            } catch (e: Exception) {
                Log.e(TAG, "AI生成总结失败", e)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "记忆总结失败", e)
        }
    }

    /** 找到合适的总结插入位置 对于第一次总结，放在前SUMMARY_CHUNK_SIZE条消息后面 对于后续总结，放在上一次总结之后的SUMMARY_CHUNK_SIZE条消息后面 */
    private fun findProperSummaryPosition(messages: List<ChatMessage>): Int {
        //一条总结消息的位置 找到最后
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

        // 如果没有找到总结消息，说明是第一次总结
        if (lastSummaryIndex == -1) {
            // 找到第SUMMARY_CHUNK_SIZE条user/ai消息在完整消息列表中的位置
            var userAiCount = 0
            for (i in messages.indices) {
                if (messages[i].sender == "user" || messages[i].sender == "ai") {
                    userAiCount++
                    if (userAiCount == SUMMARY_CHUNK_SIZE) {
                        return i + 1 // 在第SUMMARY_CHUNK_SIZE条消息后插入
                    }
                }
            }
            // 如果没有足够的消息，插入到末尾
            return messages.size
        } else {
            // 在最后一条总结之后找SUMMARY_CHUNK_SIZE条user/ai消息
            var userAiCount = 0
            for (i in (lastSummaryIndex + 1) until messages.size) {
                if (messages[i].sender == "user" || messages[i].sender == "ai") {
                    userAiCount++
                    if (userAiCount == SUMMARY_CHUNK_SIZE) {
                        return i + 1 // 在最后一条总结后的第SUMMARY_CHUNK_SIZE条消息后插入
                    }
                }
            }
            // 如果没有足够的消息，插入到末尾
            return messages.size
        }
    }

    /** 切换是否显示聊天历史选择器 */
    fun toggleChatHistorySelector() {
        _showVoiceHistorySelector.value = !_showVoiceHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showVoiceHistorySelector(show: Boolean) {
        _showVoiceHistorySelector.value = show
    }

    /** 获取当前聊天历史的内存记录 只包含user和ai消息，不包含系统消息和思考消息 */
    fun getMemory(): List<Pair<String, String>> {
        // 获取所有消息
        val allMessages = _voiceHistory.value

        // 构建最终返回的消息列表
        val result = mutableListOf<Pair<String, String>>()

        // 查找最新的总结消息位置
        val lastSummaryIndex = allMessages.indexOfLast { it.sender == "summary" }
        // val allSummaryMessage = allMessages.filter { it.sender == "summary" }

        // 如果找到总结消息
        if (lastSummaryIndex != -1) {
            var firstUserMessage = ""
            // for (summaryMessage in allSummaryMessage) {
            val summaryMessage = allMessages[lastSummaryIndex]
            val summaryContent =
                summaryMessage.content.replace(Regex("^#+\\s*对话摘要[：:]*\\s*"), "").trim()

            firstUserMessage += "【历史对话摘要】\n$summaryContent\n"
            Log.d(TAG, "添加最新总结消息到内存记录: ${summaryMessage.content.take(50)}...")
            // }

            firstUserMessage += "【用户本次对话】\n"

            var firstUserAsk = true

            for (i in (lastSummaryIndex + 1) until allMessages.size) {
                val message = allMessages[i]
                if (message.sender == "user" || message.sender == "ai") {
                    if (firstUserAsk && message.sender == "user") {
                        firstUserAsk = false
                        firstUserMessage += message.content
                        result.add(Pair("user", firstUserMessage))
                    } else {
                        val role = if (message.sender == "ai") "assistant" else "user"
                        result.add(Pair(role, message.content))
                    }
                }
            }
        } else {
            // 如果没有总结消息，则正常处理所有消息
            for (message in allMessages) {
                if (message.sender == "user" || message.sender == "ai") {
                    val role = if (message.sender == "ai") "assistant" else "user"
                    result.add(Pair(role, message.content))
                }
            }
        }

        Log.d(TAG, "获取内存记录完成，共 ${result.size} 条消息，包含总结：${lastSummaryIndex != -1}")
        return result
    }

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }

    /** 通过回调获取当前token统计数据 */
    private fun getCurrentTokenCounts(): Pair<Int, Int> {
        // 使用构造函数中传入的回调获取当前token统计数据
        return getTokenCounts()
    }
}
