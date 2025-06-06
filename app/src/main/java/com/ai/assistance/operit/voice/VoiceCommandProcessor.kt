package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.VoiceActionType
import com.ai.assistance.operit.data.model.VoiceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音命令处理器
 * 负责识别和处理语音命令
 */
class VoiceCommandProcessor(
    private val context: Context,
    private val aiService: EnhancedAIService,
    private val coroutineScope: CoroutineScope,
    private val chatHistory: StateFlow<List<ChatMessage>>,
    private val aiToolHandler: AIToolHandler
) {
    // 命令处理事件
    private val _commandEvents = MutableSharedFlow<CommandEvent>()
    val commandEvents: SharedFlow<CommandEvent> = _commandEvents
    
    // 预定义命令列表
    private val predefinedCommands = mutableListOf<VoiceCommand>()

    // 系统操作工具
    private val systemOperationTools = ToolGetter.getSystemOperationTools(context)
    
    init {
        setupPredefinedCommands()
    }
    
    /**
     * 设置预定义命令
     */
    private fun setupPredefinedCommands() {
        // 基础系统控制命令
        predefinedCommands.add(
            VoiceCommand(
                pattern = "停止|停下|取消",
                action = VoiceActionType.STOP_CURRENT_ACTION,
                parameters = emptyMap(),
                confirmationRequired = false
            )
        )
        
        predefinedCommands.add(
            VoiceCommand(
                pattern = "(打开|启动)相机",
                action = VoiceActionType.OPEN_APP,
                parameters = mapOf("appName" to "相机",
                    "packageName" to "com.google.android.GoogleCamera"),  // 这里先拿Pixel测一下
                confirmationRequired = false
            )
        )

        predefinedCommands.add(
            VoiceCommand(
                pattern = "(打开|启动)Spotify",
                action = VoiceActionType.OPEN_APP,
                parameters = mapOf("appName" to "相机",
                    "packageName" to "com.spotify.music"),
                confirmationRequired = false
            )
        )

        // 可以根据需要添加更多预定义命令
    }
    
    /**
     * 注册新的语音命令
     * @param command 要注册的命令
     */
    fun registerCommand(command: VoiceCommand) {
        if (predefinedCommands.none { it.pattern == command.pattern }) {
            predefinedCommands.add(command)
        }
    }
    
    /**
     * 处理语音输入
     * @param text 识别的语音文本
     * @param isDirectCommand 是否作为直接命令处理(不通过AI分析)
     */
    suspend fun processVoiceInput(text: String, isDirectCommand: Boolean = false): CommandProcessResult {
        // 首先检查是否匹配预定义命令
        val directCommand = findMatchingPredefinedCommand(text)
        if (directCommand != null) {
            // 发出命令开始事件
            _commandEvents.emit(CommandEvent.CommandDetected(directCommand))
            
            // 如果需要确认且不是直接命令模式
            if (directCommand.confirmationRequired && !isDirectCommand) {
                _commandEvents.emit(CommandEvent.ConfirmationNeeded(directCommand))
                return CommandProcessResult(
                    wasCommand = true,
                    commandExecuted = false,
                    needsConfirmation = true,
                    command = directCommand
                )
            }
            
            // 执行命令
            val success = executeCommand(directCommand)
            return CommandProcessResult(
                wasCommand = true,
                commandExecuted = success,
                needsConfirmation = false,
                command = directCommand
            )
        }
        
        return CommandProcessResult(
            wasCommand = false,
            commandExecuted = false,
            needsConfirmation = false,
            command = null
        )
    }
    
    /**
     * 检查工具是否需要权限
     *
     * @param toolHandler AI工具处理器
     * @param tool AI工具
     * @return 包含(是否有权限, 如果没有权限则返回错误结果)的对
     */
    private suspend fun checkToolPermission(
        toolHandler: AIToolHandler,
        tool: AITool
    ): Pair<Boolean, ToolResult?> {
        try {
            // 检查权限，如果需要则弹出权限请求界面
            val toolPermissionSystem = toolHandler.getToolPermissionSystem()
            val hasPermission = toolPermissionSystem.checkToolPermission(tool)

            // 如果权限被拒绝，创建错误结果
            if (!hasPermission) {
                val errorResult = ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Permission denied: Operation '${tool.name}' was not authorized by the user"
                )

                return Pair(false, errorResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking tool permission: ${e.message}")
            val errorResult = ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error checking permission: ${e.message}"
            )
            return Pair(false, errorResult)
        }
        
        return Pair(true, null)
    }

    
    /**
     * 查找匹配的预定义命令
     * @param text 要匹配的文本
     * @return 匹配的命令，如果没有匹配则返回null
     */
    private fun findMatchingPredefinedCommand(text: String): VoiceCommand? {
        for (command in predefinedCommands) {
            val regex = command.pattern.toRegex(RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(text)) {
                return command
            }
        }
        return null
    }
    
    /**
     * 执行命令
     * @param command 要执行的命令
     * @return 是否执行成功
     */
    private suspend fun executeCommand(command: VoiceCommand): Boolean {
        coroutineScope.launch {
            _commandEvents.emit(CommandEvent.CommandExecuting(command))
        }
        
        try {
            when (command.action) {
                VoiceActionType.STOP_CURRENT_ACTION -> {
                    // 执行停止当前操作
                    coroutineScope.launch {
                        _commandEvents.emit(CommandEvent.StopRequested)
                    }
                    return true
                }
                
                VoiceActionType.OPEN_APP -> {
                    // 使用工具处理器打开应用
                    val packageName = command.parameters["packageName"] as? String
                    if (packageName != null) {
                        // 创建AITool实例
                        val aiTool = AITool(
                            name = "start_app",
                            description = "打开指定的应用程序",
                            parameters = listOf(
                                ToolParameter("package_name", packageName)
                            )
                        )
                        
                        // 检查权限
                        val (hasPermission, errorResult) = checkToolPermission(aiToolHandler, aiTool)
                        if (!hasPermission) {
                            coroutineScope.launch {
                                _commandEvents.emit(CommandEvent.Error(errorResult?.error ?: "权限被拒绝"))
                                _commandEvents.emit(CommandEvent.CommandCompleted(command, false))
                            }
                            return false
                        }

                        
                        // 执行工具
                        val result = systemOperationTools.startApp(aiTool)
                        coroutineScope.launch {
                            _commandEvents.emit(CommandEvent.CommandCompleted(command, result.success))
                        }
                        return result.success
                    }
                }

                // 添加更多命令处理逻辑
                // val result = aiToolHandler.executeTool(aiTool)
                
                else -> {
                    Log.w(TAG, "Unknown command action: ${command.action}")
                }
            }
            
            coroutineScope.launch {
                _commandEvents.emit(CommandEvent.CommandCompleted(command, false))
            }
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}")
            coroutineScope.launch {
                _commandEvents.emit(CommandEvent.Error("命令执行失败：${e.message}"))
                _commandEvents.emit(CommandEvent.CommandCompleted(command, false))
            }
            return false
        }
    }
    
    /**
     * 确认并执行命令
     * @param command 要确认的命令
     * @param confirmed 是否已确认
     */
    suspend fun confirmAndExecuteCommand(command: VoiceCommand, confirmed: Boolean): Boolean {
        if (!confirmed) {
            coroutineScope.launch {
                _commandEvents.emit(CommandEvent.CommandCancelled(command))
            }
            return false
        }
        
        return executeCommand(command)
    }
    
    /**
     * 命令处理结果
     */
    data class CommandProcessResult(
        val wasCommand: Boolean,
        val commandExecuted: Boolean,
        val needsConfirmation: Boolean,
        val command: VoiceCommand?
    )
    
    /**
     * 命令事件
     */
    sealed class CommandEvent {
        data class CommandDetected(val command: VoiceCommand) : CommandEvent()
        data class ConfirmationNeeded(val command: VoiceCommand) : CommandEvent()
        data class CommandExecuting(val command: VoiceCommand) : CommandEvent()
        data class CommandCompleted(val command: VoiceCommand, val success: Boolean) : CommandEvent()
        data class CommandCancelled(val command: VoiceCommand) : CommandEvent()
        data object StopRequested : CommandEvent()
        data class AIProcessing(val content: String) : CommandEvent()
        data object AICompleted : CommandEvent()
        data class AIConnecting(val status: String) : CommandEvent()
        data class AIConnected(val status: String) : CommandEvent()
        data class Error(val message: String) : CommandEvent()
    }
    
    companion object {
        private const val TAG = "VoiceCommandProcessor"
    }
} 