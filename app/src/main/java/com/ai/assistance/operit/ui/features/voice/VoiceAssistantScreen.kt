package com.ai.assistance.operit.ui.features.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import kotlinx.coroutines.launch

/**
 * 语音助手屏幕
 * 展示与语音助手交互的UI界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    onNavigateToSettings: () -> Unit
) {
    // 获取应用上下文
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    
    // 获取或创建依赖项
    val aiService = remember { EnhancedAIService(context) }
    val aiToolHandler = remember { AIToolHandler.getInstance(context) }
    
    // 创建ViewModel工厂
    val factory = remember { 
        VoiceAssistantViewModel.Factory(
            application = application,
            aiService = aiService,
            aiToolHandler = aiToolHandler
        ) 
    }
    
    // 使用工厂创建ViewModel
    val viewModel = viewModel<VoiceAssistantViewModel>(factory = factory)
    
    val voiceState by viewModel.voiceState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // 检查是否有语音权限
    CheckVoicePermissions { viewModel.initializeVoiceModule() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音助手") },
                actions = {
                    // 设置按钮
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }

                    // 语音输出开关 - 根据初始化状态禁用
                    IconButton(
                        onClick = {
                            if (voiceState.isInitialized) {
                                coroutineScope.launch {
                                    viewModel.toggleReadResponses()
                                }
                            }
                        },
                        enabled = voiceState.isInitialized
                    ) {
                        Icon(
                            if (voiceState.isReadResponsesEnabled) Icons.Default.VolumeUp
                            else Icons.Default.VolumeOff,
                            contentDescription = "语音输出"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 监听按钮 - 根据初始化状态禁用
            ListeningButton(
                isListening = voiceState.isListening,
                isInitialized = voiceState.isInitialized,
                onStartListening = {
                    if (voiceState.isInitialized) {
                        viewModel.startListening()
                    }
                },
                onStopListening = {
                    coroutineScope.launch {
                        viewModel.stopListening()
                    }
                }
            )
        }
    ) { paddingValues ->
        // 显示初始化状态或错误消息
        if (!voiceState.isInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在初始化语音助手...", style = MaterialTheme.typography.bodyLarge)

                    if (voiceState.error != null) {
                        Text(
                            text = "错误: ${voiceState.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            VoiceAssistantContent(
                voiceState = voiceState,
                onToggleWakeWord = {
                    coroutineScope.launch {
                        viewModel.toggleWakeWord()
                    }
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

/**
 * 语音助手主内容
 */
@Composable
fun VoiceAssistantContent(
    voiceState: VoiceAssistantViewModel.UiState,
    onToggleWakeWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 状态信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "语音助手状态",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "唤醒词模式:",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = voiceState.isWakeWordEnabled,
                        onCheckedChange = { onToggleWakeWord() },
                        enabled = voiceState.isInitialized
                    )
                }
                
                Text(
                    text = if (voiceState.isInitialized) "系统已就绪" else "系统正在初始化...",
                    color = if (voiceState.isInitialized) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                
                if (voiceState.error != null) {
                    Text(
                        text = "错误: ${voiceState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // 当前状态信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "当前状态",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (voiceState.isListening) {
                    Text(
                        text = "正在收听...",
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    VoiceWaveform(
                        noiseLevel = voiceState.noiseLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    )
                    
                    if (voiceState.partialText.isNotEmpty()) {
                        Text(
                            text = voiceState.partialText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Text(
                        text = "置信度: ${(voiceState.recognitionConfidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (voiceState.isSpeaking) {
                    Text(
                        text = "正在说话...",
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "空闲中",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // 识别结果卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "最近识别内容",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (voiceState.lastRecognizedText.isNotEmpty()) {
                    Text(
                        text = voiceState.lastRecognizedText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "尚未识别任何内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // 唤醒词信息
        if (voiceState.isWakeWordEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "唤醒词信息",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "默认唤醒词: \"你好助手\"",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (voiceState.lastWakeWord.isNotEmpty()) {
                        Text(
                            text = "上次检测到: \"${voiceState.lastWakeWord}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // 使用指南
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "使用指南",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "1. 点击下方麦克风按钮开始收听\n" +
                        "2. 或者开启唤醒词模式，说出\"你好助手\"唤醒\n" +
                        "3. 尝试以下命令:\n" +
                        "   - \"打开相机\"\n" +
                        "   - \"停止\" 或 \"取消\"\n" +
                        "   - 或任何其他问题",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp)) // 为FAB留出空间
    }
}

/**
 * 语音波形可视化
 */
@Composable
fun VoiceWaveform(
    noiseLevel: Float,
    modifier: Modifier = Modifier
) {
    val bars = 20
    val maxHeight = 50.dp
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until bars) {
            val infiniteTransition = rememberInfiniteTransition(label = "wave")
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500 + (i * 50), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_bar"
            )
            
            // 最终高度结合了噪音水平和动画值
            val height = maxHeight * noiseLevel * animatedHeight
            
            Box(
                modifier = Modifier
                    .height(height)
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * 听取按钮
 */
@Composable
fun ListeningButton(
    isListening: Boolean,
    isInitialized: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_animation"
    )

    FloatingActionButton(
        onClick = {
            if (isListening) onStopListening() else onStartListening()
        },
        modifier = Modifier
            .scale(scale)
            .size(64.dp),
        shape = CircleShape,
        containerColor = if (isListening)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "停止收听" else "开始收听",
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * 语音权限检查
 */
@Composable
fun CheckVoicePermissions(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    
    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // 权限被授予，执行回调
                onPermissionsGranted()
            } else {
                // 权限被拒绝，可能需要显示说明
                showRationale = !shouldShowRationale(context, Manifest.permission.RECORD_AUDIO)
            }
        }
    )
    
    // 在Composable首次加载时检查权限
    LaunchedEffect(Unit) {
        when {
            // 如果已经有权限，直接执行回调
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionsGranted()
            }
            // 否则请求权限
            else -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    // 显示权限说明对话框
    if (showRationale) {
        Dialog(onDismissRequest = { showRationale = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要麦克风权限",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "语音助手需要麦克风权限才能听取您的语音指令。请在系统设置中授予此权限。",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    androidx.compose.material3.Button(
                        onClick = { 
                            // 打开应用设置页面
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            showRationale = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text("打开设置")
                    }
                }
            }
        }
    }
}

/**
 * 检查是否应该显示权限说明
 */
private fun shouldShowRationale(context: Context, permission: String): Boolean {
    val activity = context as? androidx.activity.ComponentActivity
    return activity?.shouldShowRequestPermissionRationale(permission) ?: false
} 