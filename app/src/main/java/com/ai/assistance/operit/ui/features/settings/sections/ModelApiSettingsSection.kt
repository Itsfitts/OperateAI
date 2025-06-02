package com.ai.assistance.operit.ui.features.settings.sections

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.api.ModelListFetcher
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch

val TAG = "ModelApiSettings"

@Composable
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 获取每个提供商的默认模型名称
    fun getDefaultModelName(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "gpt-4o"
            ApiProviderType.ANTHROPIC -> "claude-3-opus-20240229"
            ApiProviderType.GOOGLE -> "gemini-2.0-flash"
            ApiProviderType.DEEPSEEK -> "deepseek-chat"
            ApiProviderType.BAIDU -> "ernie-bot-4"
            ApiProviderType.ALIYUN -> "qwen-max"
            ApiProviderType.XUNFEI -> "spark3.5"
            ApiProviderType.ZHIPU -> "chatglm_pro"
            ApiProviderType.BAICHUAN -> "baichuan4"
            ApiProviderType.MOONSHOT -> "moonshot-v1-128k"
            ApiProviderType.SILICONFLOW -> "yi-1.5-34b"
            ApiProviderType.OPENROUTER -> "google/gemini-pro"
            ApiProviderType.OTHER -> ""
        }
    }

    // 检查当前模型名称是否是某个提供商的默认值
    fun isDefaultModelName(modelName: String): Boolean {
        return ApiProviderType.values().any { getDefaultModelName(it) == modelName }
    }

    // API编辑状态
    var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
    var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
    var modelNameInput by remember(config.id) { mutableStateOf(config.modelName) }
    var selectedApiProvider by remember(config.id) { mutableStateOf(config.apiProviderType) }

    // 根据API提供商获取默认的API端点URL
    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
            ApiProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            ApiProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/models"
            ApiProviderType.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            ApiProviderType.BAIDU ->
                    "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            ApiProviderType.ALIYUN ->
                    "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            ApiProviderType.XUNFEI -> "https://spark-api.xf-yun.com/v2.1/chat"
            ApiProviderType.ZHIPU ->
                    "https://open.bigmodel.cn/api/paas/v3/model-api/chatglm_pro/invoke"
            ApiProviderType.BAICHUAN -> "https://api.baichuan-ai.com/v1/chat/completions"
            ApiProviderType.MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions"
            ApiProviderType.SILICONFLOW -> "https://api.lingyiwanwu.com/v1/chat/completions"
            ApiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            ApiProviderType.OTHER -> ""
        }
    }

    // 添加一个函数检查当前API端点是否为某个提供商的默认端点
    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return ApiProviderType.values().any { getDefaultApiEndpoint(it) == endpoint }
    }

    // 当API提供商改变时更新端点
    LaunchedEffect(selectedApiProvider) {
        if (apiEndpointInput.isEmpty() || isDefaultApiEndpoint(apiEndpointInput)) {
            apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider)
        }
    }

    // 模型列表状态
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }
    var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    // 检查是否使用默认API密钥
    val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY
    var showModelRestrictionInfo by remember { mutableStateOf(isUsingDefaultApiKey) }

    // 当使用默认API密钥时限制模型名称
    LaunchedEffect(apiKeyInput) {
        if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
            modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
            showModelRestrictionInfo = true
        } else {
            showModelRestrictionInfo = false
        }
    }

    // API设置卡片
    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Api,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "API设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
            }

            // API端点输入
            OutlinedTextField(
                    value = apiEndpointInput,
                    onValueChange = { apiEndpointInput = it },
                    label = { Text("API端点") },
                    placeholder = { Text("例如: https://api.openai.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
            )

            // API密钥输入
            OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it

                        // 当API密钥改变时检查是否需要限制模型
                        if (it == ApiPreferences.DEFAULT_API_KEY) {
                            modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                            showModelRestrictionInfo = true
                        } else {
                            showModelRestrictionInfo = false
                        }
                    },
                    label = { Text("API密钥") },
                    placeholder = { Text("输入API密钥") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
            )

            // 模型名称输入和模型列表按钮
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                        value = modelNameInput,
                        onValueChange = {
                            // 只有在不使用默认API密钥时才允许更改
                            if (!isUsingDefaultApiKey) {
                                modelNameInput = it
                            }
                        },
                        label = { Text("模型名称") },
                        placeholder = { Text("例如: gpt-4, claude-3-opus-20240229...") },
                        modifier = Modifier.weight(1f),
                        enabled = !isUsingDefaultApiKey,
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        disabledTextColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.38f
                                                        ),
                                        disabledBorderColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.12f
                                                        ),
                                        disabledLabelColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.7f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.38f)
                                ),
                        singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 获取模型列表按钮
                IconButton(
                        onClick = {
                            Log.d(
                                    TAG,
                                    "模型列表按钮被点击 - API端点: $apiEndpointInput, API类型: ${selectedApiProvider.name}"
                            )
                            showNotification("正在获取模型列表...")

                            scope.launch {
                                if (apiEndpointInput.isNotBlank() &&
                                                apiKeyInput.isNotBlank() &&
                                                !isUsingDefaultApiKey
                                ) {
                                    isLoadingModels = true
                                    modelLoadError = null
                                    Log.d(
                                            TAG,
                                            "开始获取模型列表: 端点=$apiEndpointInput, API类型=${selectedApiProvider.name}"
                                    )

                                    try {
                                        val result =
                                                ModelListFetcher.getModelsList(
                                                        apiKeyInput,
                                                        apiEndpointInput,
                                                        selectedApiProvider
                                                )
                                        if (result.isSuccess) {
                                            val models = result.getOrThrow()
                                            Log.d(TAG, "模型列表获取成功，共 ${models.size} 个模型")
                                            modelsList = models
                                            showModelsDialog = true
                                            showNotification("成功获取 ${models.size} 个模型")
                                        } else {
                                            val errorMsg =
                                                    result.exceptionOrNull()?.message ?: "未知错误"
                                            Log.e(TAG, "模型列表获取失败: $errorMsg")
                                            modelLoadError = "获取模型列表失败: $errorMsg"
                                            showNotification(modelLoadError ?: "获取模型列表失败")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "获取模型列表发生异常", e)
                                        modelLoadError = "获取模型列表失败: ${e.message}"
                                        showNotification(modelLoadError ?: "获取模型列表失败")
                                    } finally {
                                        isLoadingModels = false
                                        Log.d(TAG, "模型列表获取流程完成")
                                    }
                                } else if (isUsingDefaultApiKey) {
                                    Log.d(TAG, "使用默认配置，不获取模型列表")
                                    showNotification("使用默认配置时无法获取模型列表")
                                } else {
                                    Log.d(TAG, "API端点或密钥为空")
                                    showNotification("请先填写API端点和密钥")
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        colors =
                                IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                ),
                        enabled = !isUsingDefaultApiKey
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "获取模型列表",
                                tint =
                                        if (!isUsingDefaultApiKey) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }

            // 添加API提供商选择
            var apiProviderDropdownExpanded by remember { mutableStateOf(false) }

            // API提供商选择
            Text(
                    "API提供商",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
            )

            Box {
                OutlinedTextField(
                        value = selectedApiProvider.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择API提供商") },
                        trailingIcon = {
                            IconButton(onClick = { apiProviderDropdownExpanded = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                DropdownMenu(
                        expanded = apiProviderDropdownExpanded,
                        onDismissRequest = { apiProviderDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    // 添加所有API提供商选项
                    ApiProviderType.values().forEach { provider ->
                        DropdownMenuItem(
                                text = {
                                    Text(
                                            when (provider) {
                                                ApiProviderType.OPENAI -> "OpenAI (GPT系列)"
                                                ApiProviderType.ANTHROPIC -> "Anthropic (Claude系列)"
                                                ApiProviderType.GOOGLE -> "Google (Gemini系列)"
                                                ApiProviderType.BAIDU -> "百度 (文心一言系列)"
                                                ApiProviderType.ALIYUN -> "阿里云 (通义千问系列)"
                                                ApiProviderType.XUNFEI -> "讯飞 (星火认知系列)"
                                                ApiProviderType.ZHIPU -> "智谱AI (ChatGLM系列)"
                                                ApiProviderType.BAICHUAN -> "百川大模型"
                                                ApiProviderType.MOONSHOT -> "月之暗面大模型"
                                                ApiProviderType.DEEPSEEK -> "Deepseek大模型"
                                                ApiProviderType.SILICONFLOW -> "硅基流动"
                                                ApiProviderType.OPENROUTER -> "OpenRouter (多模型聚合)"
                                                ApiProviderType.OTHER -> "其他提供商"
                                            }
                                    )
                                },
                                onClick = {
                                    selectedApiProvider = provider
                                    apiProviderDropdownExpanded = false

                                    // 根据选择的提供商设置推荐模型名称
                                    if (modelNameInput.isEmpty() ||
                                                    isDefaultModelName(modelNameInput)
                                    ) {
                                        modelNameInput = getDefaultModelName(provider)
                                    }
                                }
                        )

                        if (provider.ordinal < ApiProviderType.values().size - 1) {
                            Divider()
                        }
                    }
                }
            }

            // 显示模型限制信息
            if (showModelRestrictionInfo) {
                Text(
                        text = "使用默认配置时，只能使用deepseek-chat模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp),
                        fontSize = 12.sp
                )
            }
            // 保存按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                        onClick = {
                            scope.launch {
                                // 强制在使用默认API密钥时使用默认模型
                                val modelToSave =
                                        if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
                                            ApiPreferences.DEFAULT_MODEL_NAME
                                        } else {
                                            modelNameInput
                                        }

                                Log.d(
                                        TAG,
                                        "保存API设置: apiKey=${apiKeyInput.take(5)}..., endpoint=$apiEndpointInput, model=$modelToSave, providerType=${selectedApiProvider.name}"
                                )

                                // 更新配置
                                configManager.updateModelConfig(
                                        configId = config.id,
                                        apiKey = apiKeyInput,
                                        apiEndpoint = apiEndpointInput,
                                        modelName = modelToSave,
                                        apiProviderType = selectedApiProvider
                                )

                                Log.d(TAG, "API设置保存完成")
                                showNotification("API设置已保存")
                            }
                        }
                ) { Text("保存API设置") }
            }
        }
    }

    // 模型列表对话框
    if (showModelsDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredModelsList =
                remember(searchQuery, modelsList) {
                    if (searchQuery.isEmpty()) modelsList
                    else modelsList.filter { it.id.contains(searchQuery, ignoreCase = true) }
                }

        Dialog(onDismissRequest = { showModelsDialog = false }) {
            Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题栏
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                "可用模型列表",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )

                        FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        if (apiEndpointInput.isNotBlank() &&
                                                        apiKeyInput.isNotBlank() &&
                                                        !isUsingDefaultApiKey
                                        ) {
                                            isLoadingModels = true
                                            try {
                                                val result =
                                                        ModelListFetcher.getModelsList(
                                                                apiKeyInput,
                                                                apiEndpointInput,
                                                                selectedApiProvider
                                                        )
                                                if (result.isSuccess) {
                                                    modelsList = result.getOrThrow()
                                                } else {
                                                    modelLoadError =
                                                            "刷新模型列表失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                                    showNotification(modelLoadError ?: "刷新模型列表失败")
                                                }
                                            } catch (e: Exception) {
                                                modelLoadError = "刷新模型列表失败: ${e.message}"
                                                showNotification(modelLoadError ?: "刷新模型列表失败")
                                            } finally {
                                                isLoadingModels = false
                                            }
                                        }
                                    }
                                },
                                colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                contentColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "刷新模型列表",
                                        modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 搜索框 - 用普通的OutlinedTextField替代实验性的SearchBar
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索模型", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索",
                                        modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "清除",
                                                modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier =
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(48.dp),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor =
                                                    MaterialTheme.colorScheme.outline,
                                            focusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                            unfocusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    // 模型列表
                    if (modelsList.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = modelLoadError ?: "没有找到可用模型",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredModelsList.size) { index ->
                                val model = filteredModelsList[index]
                                // 使用自定义Row替代ListItem以使布局更紧凑
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable {
                                                            modelNameInput = model.id
                                                            showModelsDialog = false
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            text = model.id,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                    )
                                }

                                if (index < filteredModelsList.size - 1) {
                                    Divider(
                                            thickness = 0.5.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部信息
                    if (filteredModelsList.isNotEmpty()) {
                        Text(
                                text =
                                        "已显示 ${filteredModelsList.size} 个模型" +
                                                (if (searchQuery.isNotEmpty()) " (已过滤)" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                                fontSize = 12.sp
                        )
                    }

                    // 底部按钮
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                                onClick = { showModelsDialog = false },
                                modifier = Modifier.height(36.dp)
                        ) { Text("关闭", fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}
