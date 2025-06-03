package com.ai.assistance.operit.ui.features.chat.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.util.Log
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatScreenContent(
        paddingValues: PaddingValues,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistory: List<ChatMessage>,
        listState: LazyListState,
        planItems: List<PlanItem>,
        enableAiPlanning: Boolean,
        toolProgress: ToolExecutionProgress,
        isLoading: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        hasBackgroundImage: Boolean,
        isEditMode: MutableState<Boolean>,
        editingMessageIndex: MutableState<Int?>,
        editingMessageContent: MutableState<String>,
        chatScreenGestureConsumed: Boolean,
        onChatScreenGestureConsumed: (Boolean) -> Unit,
        currentDrag: Float,
        onCurrentDragChange: (Float) -> Unit,
        verticalDrag: Float,
        onVerticalDragChange: (Float) -> Unit,
        dragThreshold: Float,
        showScrollButton: Boolean,
        onShowScrollButtonChange: (Boolean) -> Unit,
        autoScrollToBottom: Boolean,
        onAutoScrollToBottomChange: (Boolean) -> Unit,
        coroutineScope: CoroutineScope,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        webViewNeedsRefresh: Boolean = false,
        onWebViewRefreshed: () -> Unit = {}
) {
    // 获取WebView状态
    val showWebView = actualViewModel.showWebView.collectAsState().value

    // 导出相关状态
    val context = LocalContext.current
    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showAndroidExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var showExportCompleteDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportSuccess by remember { mutableStateOf(false) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var webContentDir by remember { mutableStateOf<File?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // 主聊天区域（包括顶部工具栏），确保它一直可见
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                            onDragStart = {
                                                // 当WebView显示时，不处理水平拖动手势
                                                if (!showWebView) {
                                                    onChatScreenGestureConsumed(false)
                                                    onCurrentDragChange(0f)
                                                    onVerticalDragChange(0f)
                                                }
                                            },
                                            onDragEnd = {
                                                // 手势结束后重置累计值
                                                if (!showWebView) {
                                                    onCurrentDragChange(0f)
                                                    onVerticalDragChange(0f)
                                                    // 延迟重置消费状态，确保事件不会传递到全局侧边栏
                                                    onChatScreenGestureConsumed(false)
                                                }
                                            },
                                            onDragCancel = {
                                                if (!showWebView) {
                                                    onCurrentDragChange(0f)
                                                    onVerticalDragChange(0f)
                                                    onChatScreenGestureConsumed(false)
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                // 当WebView显示时，不处理水平拖动手势
                                                if (showWebView) {
                                                    return@detectHorizontalDragGestures
                                                }

                                                // 累加水平拖动距离
                                                val newDrag = currentDrag + dragAmount
                                                onCurrentDragChange(newDrag)

                                                // 保持判定条件简单，与PhoneLayout类似
                                                val dragRight = dragAmount > 0 // 即时判断方向，而不是累计方向

                                                // 添加日志记录手势状态
                                                Log.d(
                                                        "ChatScreenContent",
                                                        "手势状态: 方向=${if(dragRight) "右" else "左"}, 累计=${newDrag}, 垂直=${verticalDrag}, 显示历史=${showChatHistorySelector}"
                                                )

                                                // 简化判定条件：只要是向右滑动且累积量超过阈值就触发
                                                if (!showChatHistorySelector &&
                                                                dragRight &&
                                                                newDrag > dragThreshold &&
                                                                Math.abs(newDrag) >
                                                                        Math.abs(verticalDrag)
                                                ) {
                                                    Log.d(
                                                            "ChatScreenContent",
                                                            "触发打开历史记录：累计=${newDrag}"
                                                    )
                                                    actualViewModel.showChatHistorySelector(true)
                                                    // 告知父组件手势已被消费
                                                    onChatScreenGestureConsumed(true)
                                                    change.consume()
                                                }

                                                // 如果是从右向左滑动，且历史选择器已显示，则关闭历史选择器
                                                if (dragAmount < 0 &&
                                                                showChatHistorySelector &&
                                                                newDrag < -dragThreshold
                                                ) {
                                                    Log.d(
                                                            "ChatScreenContent",
                                                            "触发关闭历史记录：累计=${newDrag}"
                                                    )
                                                    actualViewModel.showChatHistorySelector(false)
                                                    onChatScreenGestureConsumed(true)
                                                    change.consume()
                                                }
                                            }
                                    )
                                }
                                .pointerInput(Unit) {
                                    // 添加垂直方向手势检测，用于记录垂直拖动距离
                                    detectVerticalDragGestures { _, dragAmount ->
                                        // 当WebView显示时，不处理垂直拖动手势（记录）
                                        if (!showWebView) {
                                            onVerticalDragChange(verticalDrag + dragAmount)
                                        }
                                    }
                                }
        ) {
            // 聊天区域
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部工具栏 - 整合聊天历史按钮和统计信息 - 始终显示在顶部
                ChatScreenHeader(
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        isEditMode = isEditMode,
                        showWebView = showWebView,
                        onWebDevClick = { actualViewModel.toggleWebView() }
                )

                // 聊天对话区域
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(
                                                if (hasBackgroundImage) Color.Transparent
                                                else MaterialTheme.colorScheme.background
                                        )
                ) {
                    // 只有在不显示WebView时才显示聊天区域
                    if (!showWebView) {
                        ChatArea(
                                chatHistory = chatHistory,
                                listState = listState,
                                planItems = planItems,
                                enablePlanning = enableAiPlanning,
                                toolProgress = toolProgress,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                hasBackgroundImage = hasBackgroundImage,
                                modifier = Modifier.fillMaxSize(),
                                isEditMode = isEditMode.value,
                                onSelectMessageToEdit = { index, message ->
                                    editingMessageIndex.value = index
                                    editingMessageContent.value = message.content
                                }
                        )

                        // 编辑模式下的操作面板
                        if (isEditMode.value && editingMessageIndex.value != null) {
                            MessageEditPanel(
                                    editingMessageContent = editingMessageContent,
                                    onCancel = {
                                        editingMessageIndex.value = null
                                        editingMessageContent.value = ""
                                    },
                                    onSave = {
                                        val index = editingMessageIndex.value
                                        if (index != null && index < chatHistory.size) {
                                            val editedMessage =
                                                    chatHistory[index].copy(
                                                            content = editingMessageContent.value
                                                    )
                                            actualViewModel.updateMessage(index, editedMessage)

                                            // 重置编辑状态
                                            editingMessageIndex.value = null
                                            editingMessageContent.value = ""
                                            isEditMode.value = false
                                        }
                                    },
                                    onResend = {
                                        val index = editingMessageIndex.value
                                        if (index != null && index < chatHistory.size) {
                                            actualViewModel.rewindAndResendMessage(
                                                    index,
                                                    editingMessageContent.value
                                            )

                                            // 重置编辑状态
                                            editingMessageIndex.value = null
                                            editingMessageContent.value = ""
                                            isEditMode.value = false
                                        }
                                    }
                            )
                        }
                    } else {
                        // 显示WebView
                        var webView by remember { mutableStateOf<WebView?>(null) }

                        AndroidView(
                                factory = { context ->
                                    android.webkit.WebView(context).apply {
                                        // 创建更强大的WebViewClient
                                        webViewClient =
                                                object : android.webkit.WebViewClient() {
                                                    override fun onPageFinished(
                                                            view: android.webkit.WebView?,
                                                            url: String?
                                                    ) {
                                                        super.onPageFinished(view, url)
                                                        // 页面加载完成后执行的操作
                                                    }
                                                }

                                        // 添加WebChromeClient以支持更现代的Web功能
                                        webChromeClient = android.webkit.WebChromeClient()

                                        // 设置WebView的各种配置
                                        settings.apply {
                                            // 启用JavaScript
                                            javaScriptEnabled = true

                                            // 设置适用于现代网页的关键配置
                                            // 视口设置
                                            useWideViewPort = true // 启用宽视口
                                            loadWithOverviewMode = true // 使页面适应屏幕大小

                                            // 缩放控制
                                            setSupportZoom(true) // 支持缩放
                                            builtInZoomControls = true // 启用内置缩放控件
                                            displayZoomControls = false // 隐藏默认缩放控件

                                            // 文本设置
                                            defaultTextEncodingName = "UTF-8" // 设置默认字符集

                                            // 缓存设置
                                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                                            // 使页面适应当前尺寸
                                            layoutAlgorithm =
                                                    android.webkit.WebSettings.LayoutAlgorithm
                                                            .NORMAL

                                            // 设置用户代理，添加自定义标识符以便更好地适配
                                            val defaultUserAgent = userAgentString
                                            userAgentString = "$defaultUserAgent OperitWebView/1.0"

                                            // 启用DOM存储API
                                            domStorageEnabled = true

                                            // 启用应用缓存
                                            databaseEnabled = true
                                        }

                                        // 设置初始比例 - 匹配显示宽度
                                        setInitialScale(0)

                                        // 添加触摸事件监听器优化滑动体验
                                        setOnTouchListener { v, event ->
                                            // 允许WebView处理所有触摸事件
                                            v.parent.requestDisallowInterceptTouchEvent(true)
                                            false // 返回false表示不消费事件，让WebView默认行为继续处理
                                        }

                                        // 加载URL
                                        loadUrl("http://localhost:8080")

                                        // 保存WebView引用以便获取工作目录
                                        webView = this
                                    }
                                },
                                update = { webView ->
                                    // 当需要刷新WebView内容时更新
                                    if (webViewNeedsRefresh) {
                                        webView.reload()
                                        onWebViewRefreshed()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                        )

                        // 在WebView模式下显示导出按钮
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomEnd
                        ) {
                            ExportButton(
                                    onClick = {
                                        // 存储WebView工作目录的数据，使用当前聊天ID
                                        val workDir = getWebContentDir(context, currentChatId)
                                        webContentDir = workDir
                                        
                                        // 记录日志
                                        Log.d("ChatScreenContent", "正在导出工作区: ${workDir.absolutePath}, 聊天ID: $currentChatId")
                                        
                                        showExportPlatformDialog = true
                                    }
                            )
                        }
                    }

                    // 滚动到底部按钮 - 仅在聊天区域显示时显示
                    if (showScrollButton && !showWebView) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomEnd
                        ) {
                            ScrollToBottomButton(
                                    onClick = {
                                        // 点击按钮：启用自动滚动，隐藏按钮，并立即滚动到底部
                                        onAutoScrollToBottomChange(true)
                                        onShowScrollButtonChange(false)

                                        coroutineScope.launch {
                                            if (chatHistory.isNotEmpty()) {
                                                try {
                                                    // 不关心index，直接尝试滚动到底部
                                                    // 使用最大可能的滚动量
                                                    listState.dispatchRawDelta(100000f)
                                                } catch (e: Exception) {
                                                    Log.e("ChatScreenContent", "滚动到底部失败", e)
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // 历史选择器作为浮动层，使用AnimatedVisibility保持动画效果
        AnimatedVisibility(
                visible = showChatHistorySelector,
                enter =
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeIn(animationSpec = tween(300)),
                exit =
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.TopStart)
        ) {
            ChatHistorySelectorPanel(
                    actualViewModel = actualViewModel,
                    chatHistories = chatHistories,
                    currentChatId = currentChatId,
                    showChatHistorySelector = showChatHistorySelector
            )
        }

        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = { showAndroidExportDialog = true },
                    onSelectWindows = { showWindowsExportDialog = true }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = { showExportProgressDialog = false }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { filePath ->
                        try {
                            val file = File(filePath)
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                context.applicationContext.packageName + ".fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(fileUri, "application/vnd.android.package-archive")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e("ChatScreenContent", "无法打开文件: $filePath", e)
                        } catch (e: Exception) {
                            Log.e("ChatScreenContent", "文件操作错误: ${e.message}", e)
                        }
                    }
            )
        }
    }
}

@Composable
fun ExportButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = "打包并导出",
                modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ScrollToBottomButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.padding(end = 16.dp),
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "滚动到底部",
                modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ChatHistorySelectorPanel(
        actualViewModel: ChatViewModel,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        showChatHistorySelector: Boolean
) {
    // 添加一个覆盖整个屏幕的半透明点击区域，用于关闭历史选择器
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明遮罩层，点击右侧空白处关闭历史选择器 - 修改为不拦截滑动事件
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                // 使用pointerInput替代clickable，以便只处理点击事件而不拦截滑动
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        actualViewModel.toggleChatHistorySelector()
                                    }
                                }
                                .background(Color.Black.copy(alpha = 0.1f))
        )

        // 历史选择器面板
        Box(
                modifier =
                        Modifier.width(280.dp)
                                .fillMaxHeight()
                                .background(
                                        color =
                                                MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.95f
                                                ),
                                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
        ) {
            // 直接使用ChatHistorySelector
            ChatHistorySelector(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    onNewChat = {
                        actualViewModel.createNewChat()
                        // 创建新对话后自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onSelectChat = { chatId ->
                        actualViewModel.switchChat(chatId)
                        // 切换聊天后也自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onDeleteChat = { chatId -> actualViewModel.deleteChatHistory(chatId) },
                    chatHistories = chatHistories.sortedByDescending { it.createdAt },
                    currentId = currentChatId
            )

            // 在右侧添加浮动返回按钮
            OutlinedButton(
                    onClick = { actualViewModel.toggleChatHistorySelector() },
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 8.dp)
                                    .height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors =
                            ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                            ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Text("返回", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// 从LocalWebServer获取工作目录
private fun getWebContentDir(context: Context, chatId: String): File {
    // 使用LocalWebServer获取工作区路径
    val workspacePath = LocalWebServer.ensureWorkspaceDirExists(chatId)
    
    // 创建并返回工作区目录
    val webContentDir = File(workspacePath)
    if (!webContentDir.exists()) {
        webContentDir.mkdirs()
        
        // 如果工作区为空，创建一个示例HTML文件
        val indexHtmlFile = File(webContentDir, "index.html")
        if (!indexHtmlFile.exists()) {
            indexHtmlFile.createNewFile()
            indexHtmlFile.writeText(
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Web Content</title>
                    <style>
                        body {
                            font-family: system-ui, -apple-system, Arial, sans-serif;
                            max-width: 800px;
                            margin: 0 auto;
                            padding: 20px;
                        }
                        h1 { color: #2c3e50; }
                    </style>
                </head>
                <body>
                    <h1>网页内容</h1>
                    <p>这是当前工作区的网页内容。您可以将它导出为移动应用或桌面应用。</p>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }
    
    return webContentDir
}
