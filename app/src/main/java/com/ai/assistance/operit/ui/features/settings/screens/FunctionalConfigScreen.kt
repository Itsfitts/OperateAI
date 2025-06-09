package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalConfigScreen(
        onBackPressed: () -> Unit = {},
        enhancedAIService: EnhancedAIService? = null
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // 配置管理器
        val functionalConfigManager = remember { FunctionalConfigManager(context) }
        val modelConfigManager = remember { ModelConfigManager(context) }

        // 配置映射状态
        val configMapping =
                functionalConfigManager.functionConfigMappingFlow.collectAsState(
                        initial = emptyMap()
                )

        // 配置摘要列表
        var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }

        // UI状态
        var isLoading by remember { mutableStateOf(true) }
        var showSaveSuccess by remember { mutableStateOf(false) }

        // 加载配置摘要
        LaunchedEffect(Unit) {
                isLoading = true
                configSummaries = modelConfigManager.getAllConfigSummaries()
                isLoading = false
        }

        Scaffold() { paddingValues ->
                if (isLoading) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(paddingValues),
                                contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                } else {
                        LazyColumn(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 16.dp)
                        ) {
                                item {
                                        Card(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 8.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.7f)
                                                        )
                                        ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                bottom = 8.dp
                                                                        )
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .Settings,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        modifier =
                                                                                Modifier.size(24.dp)
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(8.dp)
                                                                )
                                                                Text(
                                                                        text = "功能模型配置设置",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium,
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        }

                                                        Text(
                                                                text =
                                                                        "为不同功能设置单独的模型配置，使每个功能都能使用最适合的API设置。",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                bottom = 8.dp
                                                                        )
                                                        )
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                }

                                // 功能类型列表
                                items(FunctionType.values()) { functionType ->
                                        val currentConfigId =
                                                configMapping.value[functionType]
                                                        ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
                                        val currentConfig =
                                                configSummaries.find { it.id == currentConfigId }

                                        FunctionConfigCard(
                                                functionType = functionType,
                                                currentConfig = currentConfig,
                                                availableConfigs = configSummaries,
                                                onConfigSelected = { configId ->
                                                        scope.launch {
                                                                functionalConfigManager
                                                                        .setConfigForFunction(
                                                                                functionType,
                                                                                configId
                                                                        )
                                                                // 刷新服务实例
                                                                enhancedAIService
                                                                        ?.refreshServiceForFunction(
                                                                                functionType
                                                                        )
                                                                showSaveSuccess = true
                                                        }
                                                }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                }

                                item {
                                        // 重置按钮
                                        OutlinedButton(
                                                onClick = {
                                                        scope.launch {
                                                                functionalConfigManager
                                                                        .resetAllFunctionConfigs()
                                                                // 刷新所有服务实例
                                                                enhancedAIService
                                                                        ?.refreshAllServices()
                                                                showSaveSuccess = true
                                                        }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outline
                                                        )
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("重置所有功能至默认配置")
                                        }

                                        // 成功提示
                                        AnimatedVisibility(
                                                visible = showSaveSuccess,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically()
                                        ) {
                                                Card(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 16.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                )
                                                ) {
                                                        Row(
                                                                modifier = Modifier.padding(16.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(8.dp)
                                                                )
                                                                Text(
                                                                        text = "配置已保存",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        }
                                                }

                                                LaunchedEffect(showSaveSuccess) {
                                                        kotlinx.coroutines.delay(2000)
                                                        showSaveSuccess = false
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun FunctionConfigCard(
        functionType: FunctionType,
        currentConfig: ModelConfigSummary?,
        availableConfigs: List<ModelConfigSummary>,
        onConfigSelected: (String) -> Unit
) {
        var expanded by remember { mutableStateOf(false) }

        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border =
                        BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        // 功能标题和描述
                        Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        text = getFunctionDisplayName(functionType),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                Text(
                                        text = getFunctionDescription(functionType),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 当前配置
                                Surface(
                                        modifier =
                                                Modifier.fillMaxWidth().clickable {
                                                        expanded = !expanded
                                                },
                                        shape = RoundedCornerShape(8.dp),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                ) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Column {
                                                        Text(
                                                                text =
                                                                        "当前配置: ${currentConfig?.name ?: "默认配置"}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                fontWeight = FontWeight.Medium
                                                        )

                                                        if (currentConfig != null) {
                                                                Text(
                                                                        text =
                                                                                "模型: ${currentConfig.modelName}",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }

                                                Icon(
                                                        imageVector =
                                                                if (expanded)
                                                                        Icons.Default
                                                                                .KeyboardArrowUp
                                                                else
                                                                        Icons.Default
                                                                                .KeyboardArrowDown,
                                                        contentDescription = "展开",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                        }

                        // 配置列表
                        AnimatedVisibility(visible = expanded) {
                                Column(
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp
                                                )
                                ) {
                                        Text(
                                                text = "选择配置",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        availableConfigs.forEach { config ->
                                                val isSelected =
                                                        config.id ==
                                                                (currentConfig?.id
                                                                        ?: FunctionalConfigManager
                                                                                .DEFAULT_CONFIG_ID)

                                                Surface(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 4.dp)
                                                                        .clickable {
                                                                                onConfigSelected(
                                                                                        config.id
                                                                                )
                                                                                expanded = false
                                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color =
                                                                if (isSelected)
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .surface,
                                                        border =
                                                                BorderStroke(
                                                                        width =
                                                                                if (isSelected) 0.dp
                                                                                else 0.5.dp,
                                                                        color =
                                                                                if (isSelected)
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .outlineVariant
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.5f
                                                                                                )
                                                                )
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(12.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                if (isSelected) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .Check,
                                                                                contentDescription =
                                                                                        "已选择",
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                }

                                                                Column(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                ) {
                                                                        Text(
                                                                                text = config.name,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodyMedium,
                                                                                fontWeight =
                                                                                        if (isSelected
                                                                                        )
                                                                                                FontWeight
                                                                                                        .Bold
                                                                                        else
                                                                                                FontWeight
                                                                                                        .Normal,
                                                                                color =
                                                                                        if (isSelected
                                                                                        )
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                        else
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurface
                                                                        )

                                                                        Text(
                                                                                text =
                                                                                        config.modelName,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                }
                        }

                        Divider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
                        )
                }
        }
}

// 获取功能类型的显示名称
fun getFunctionDisplayName(functionType: FunctionType): String {
        return when (functionType) {
                FunctionType.CHAT -> "对话功能"
                FunctionType.SUMMARY -> "对话总结"
                FunctionType.PERSONA -> "性格配置处理"
                FunctionType.PROBLEM_LIBRARY -> "问题库管理"
                FunctionType.VOICE -> "语音对话"
                FunctionType.SPEECH_RECOGNITION -> "语音识别"
        }
}

// 获取功能类型的描述
fun getFunctionDescription(functionType: FunctionType): String {
        return when (functionType) {
                FunctionType.CHAT -> "主要的对话功能，用于与用户进行日常对话交互"
                FunctionType.SUMMARY -> "生成对话总结，方便追踪重要信息"
                FunctionType.PERSONA -> "处理用户提示词和性格设置的调整"
                FunctionType.PROBLEM_LIBRARY -> "用于问题库的分析和管理"
                FunctionType.VOICE -> "主要通过语音进行对话，用于和用户进行语音交互"
                FunctionType.SPEECH_RECOGNITION -> "主要通过语音进行识别，返回识别文本"
        }
}
