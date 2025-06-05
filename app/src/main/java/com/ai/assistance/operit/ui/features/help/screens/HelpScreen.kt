package com.ai.assistance.operit.ui.features.help.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题部分
        Text(
            text = "Operit AI 工具指南",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 简介
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Operit AI 内置强大工具集",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "以下是AI可以调用的内置工具和扩展包，使用这些工具可以帮助AI更高效地完成各种任务。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 内置工具部分
        var expandedCoreTools by remember { mutableStateOf(true) }
        ExpandableCard(
            title = "内置核心工具",
            icon = Icons.Default.Build,
            expanded = expandedCoreTools,
            onExpandChange = { expandedCoreTools = it }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCategoryItem(
                    title = "基础工具",
                    items = listOf(
                        "sleep: 短暂暂停执行",
                        "device_info: 获取设备详细信息",
                        "use_package: 激活扩展包",
                        "query_problem_library: 查询问题库"
                    )
                )
                
                ToolCategoryItem(
                    title = "文件系统工具",
                    items = listOf(
                        "list_files: 列出目录中的文件",
                        "read_file: 读取文件内容",
                        "write_file: 写入内容到文件",
                        "delete_file: 删除文件或目录",
                        "file_exists: 检查文件是否存在",
                        "move_file: 移动或重命名文件",
                        "copy_file: 复制文件或目录",
                        "make_directory: 创建目录",
                        "find_files: 搜索匹配文件",
                        "zip_files/unzip_files: 压缩/解压文件",
                        "download_file: 从网络下载文件"
                    )
                )
                
                ToolCategoryItem(
                    title = "HTTP工具",
                    items = listOf(
                        "http_request: 发送HTTP请求",
                        "multipart_request: 上传文件",
                        "manage_cookies: 管理cookies",
                        "visit_web: 访问并提取网页内容"
                    )
                )
                
                ToolCategoryItem(
                    title = "系统操作工具",
                    items = listOf(
                        "get_system_setting: 获取系统设置",
                        "modify_system_setting: 修改系统设置",
                        "install_app/uninstall_app: 安装/卸载应用",
                        "start_app/stop_app: 启动/停止应用",
                        "get_notifications: 获取设备通知",
                        "get_device_location: 获取设备位置"
                    )
                )
                
                ToolCategoryItem(
                    title = "UI自动化工具",
                    items = listOf(
                        "get_page_info: 获取UI屏幕信息",
                        "tap: 模拟点击坐标",
                        "click_element: 点击UI元素",
                        "set_input_text: 设置输入文本",
                        "press_key: 模拟按键",
                        "swipe: 模拟滑动手势",
                        "find_element: 查找UI元素"
                    )
                )
                
                ToolCategoryItem(
                    title = "FFmpeg工具",
                    items = listOf(
                        "ffmpeg_execute: 执行FFmpeg命令",
                        "ffmpeg_info: 获取FFmpeg信息",
                        "ffmpeg_convert: 转换视频文件"
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 扩展包部分
        var expandedPackages by remember { mutableStateOf(true) }
        ExpandableCard(
            title = "可用扩展包",
            icon = Icons.Default.Extension,
            expanded = expandedPackages,
            onExpandChange = { expandedPackages = it }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PackageItem(
                    name = "writer",
                    description = "高级文件编辑和读取功能，支持分段编辑、差异编辑、行号编辑以及高级文件读取操作",
                    icon = Icons.Default.Edit
                )
                
                PackageItem(
                    name = "various_search",
                    description = "多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果",
                    icon = Icons.Default.Search
                )
                
                PackageItem(
                    name = "daily_life",
                    description = "日常生活工具集合，包括日期时间查询、设备状态监测、天气搜索、提醒闹钟设置、短信电话通讯等",
                    icon = Icons.Default.DateRange
                )
                
                PackageItem(
                    name = "super_admin",
                    description = "超级管理员工具集，提供终端命令和Shell操作的高级功能",
                    icon = Icons.Default.AdminPanelSettings
                )
                
                PackageItem(
                    name = "code_runner",
                    description = "多语言代码执行能力，支持JavaScript、Python、Ruby、Go和Rust脚本的运行",
                    icon = Icons.Default.Code
                )
                
                PackageItem(
                    name = "baidu_map",
                    description = "百度地图相关功能",
                    icon = Icons.Default.Map
                )
                
                PackageItem(
                    name = "qq_intelligent",
                    description = "QQ智能助手，通过UI自动化技术实现QQ应用交互",
                    icon = Icons.Default.Message
                )
                
                PackageItem(
                    name = "time",
                    description = "提供时间相关功能",
                    icon = Icons.Default.AccessTime
                )
                
                PackageItem(
                    name = "various_output",
                    description = "提供图片输出功能",
                    icon = Icons.Default.Image
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用指南
        var expandedGuide by remember { mutableStateOf(true) }
        ExpandableCard(
            title = "工具使用指南",
            icon = Icons.Default.Info,
            expanded = expandedGuide,
            onExpandChange = { expandedGuide = it }
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                GuideItem(
                    title = "工具调用",
                    content = "AI会根据需要自动调用合适的工具。每次只能调用一个工具，工具执行完成后会自动将结果返回给AI。"
                )
                
                GuideItem(
                    title = "扩展包激活",
                    content = "使用扩展包中的工具前，需要先激活该包。AI会自动使用use_package工具来激活所需的包。"
                )
                
                GuideItem(
                    title = "权限管理",
                    content = "部分工具（如系统操作、UI自动化等）需要用户授予相应权限才能使用。"
                )
                
                GuideItem(
                    title = "计划模式",
                    content = "在处理复杂任务时，可以使用计划模式，AI会将任务拆分为多个步骤并依次执行。"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 联系信息
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "如需更多帮助，请联系我们",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "aaswordsman@foxmail.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandableCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (expanded) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { onExpandChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
fun ToolCategoryItem(
    title: String,
    items: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PackageItem(
    name: String,
    description: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GuideItem(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
        )
    }
}
