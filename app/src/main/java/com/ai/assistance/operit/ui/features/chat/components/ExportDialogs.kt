package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.core.subpack.ApkEditor
import com.ai.assistance.operit.core.subpack.ExeEditor
import com.ai.assistance.operit.core.subpack.KeyStoreHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导出选择对话框，用于选择导出平台 */
@Composable
fun ExportPlatformDialog(
        onDismiss: () -> Unit,
        onSelectAndroid: () -> Unit,
        onSelectWindows: () -> Unit
) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        "选择导出平台",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PlatformButton(
                            icon = Icons.Default.Android,
                            text = "Android",
                            onClick = {
                                onSelectAndroid()
                                onDismiss()
                            }
                    )

                    PlatformButton(
                            icon = Icons.Default.DesktopWindows,
                            text = "Windows",
                            onClick = {
                                onSelectWindows()
                                onDismiss()
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("取消") }
            }
        }
    }
}

@Composable
private fun PlatformButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit
) {
    Column(
            modifier = Modifier.width(100.dp).clickable(onClick = onClick).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
                modifier = Modifier.size(60.dp).align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(8.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                        imageVector = icon,
                        contentDescription = text,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
        )
    }
}

/** Android导出配置对话框 */
@Composable
fun AndroidExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (packageName: String, appName: String, iconUri: Uri?) -> Unit
) {
    var packageName by remember { mutableStateOf("com.example.webproject") }
    var appName by remember { mutableStateOf("Web Project") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                iconUri = uri
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
                Text(
                        "配置Android应用",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("包名") },
                        placeholder = { Text("com.example.webproject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("应用名称") },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            "应用图标: ",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        if (iconUri != null) {
                            val bitmap =
                                    remember(iconUri) {
                                        try {
                                            val inputStream =
                                                    context.contentResolver.openInputStream(
                                                            iconUri!!
                                                    )
                                            val bitmap = BitmapFactory.decodeStream(inputStream)
                                            inputStream?.close()
                                            bitmap?.asImageBitmap()
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = "应用图标",
                                        contentScale = ContentScale.Fit,
                                        modifier =
                                                Modifier.size(70.dp).clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "加载失败",
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "选择图标",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 提示文字
                    Text(
                            "点击选择图标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(packageName, appName, iconUri) },
                            enabled = packageName.isNotEmpty() && appName.isNotEmpty()
                    ) { Text("导出") }
                }
            }
        }
    }
}

/** Windows导出配置对话框 */
@Composable
fun WindowsExportDialog(
        workDir: File,
        onDismiss: () -> Unit,
        onExport: (appName: String, iconUri: Uri?) -> Unit
) {
    var appName by remember { mutableStateOf("Web Project") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val imagePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                iconUri = uri
            }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
                Text(
                        "配置Windows应用",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("应用名称") },
                        placeholder = { Text("Web Project") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图标选择区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            "应用图标: ",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                    )

                    // 图标显示/选择区域
                    Box(
                            modifier =
                                    Modifier.size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                    ) {
                        if (iconUri != null) {
                            val bitmap =
                                    remember(iconUri) {
                                        try {
                                            val inputStream =
                                                    context.contentResolver.openInputStream(
                                                            iconUri!!
                                                    )
                                            val bitmap = BitmapFactory.decodeStream(inputStream)
                                            inputStream?.close()
                                            bitmap?.asImageBitmap()
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                            if (bitmap != null) {
                                Image(
                                        bitmap = bitmap,
                                        contentDescription = "应用图标",
                                        contentScale = ContentScale.Fit,
                                        modifier =
                                                Modifier.size(70.dp).clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "加载失败",
                                        tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "选择图标",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 提示文字
                    Text(
                            "点击选择图标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onExport(appName, iconUri) },
                            enabled = appName.isNotEmpty()
                    ) { Text("导出") }
                }
            }
        }
    }
}

/** 导出进度对话框 */
@Composable
fun ExportProgressDialog(progress: Float, status: String, onCancel: () -> Unit) {
    Dialog(
            onDismissRequest = { /* 不允许点击外部关闭 */},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        "导出中",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

/** 导出完成对话框 */
@Composable
fun ExportCompleteDialog(
        success: Boolean,
        filePath: String?,
        errorMessage: String?,
        onDismiss: () -> Unit,
        onOpenFile: (String) -> Unit
) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        if (success) "导出成功" else "导出失败",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (success) Color.Green else Color.Red
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (success && filePath != null) {
                    Text("文件已保存到:", style = MaterialTheme.typography.bodyMedium)

                    Text(
                            filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (errorMessage != null) {
                    // 使用可滚动的列表显示错误信息，特别是对于长消息
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .heightIn(min = 80.dp, max = 200.dp)
                                            .border(
                                                    1.dp,
                                                    Color.Red.copy(alpha = 0.5f),
                                                    RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                    ) {
                        Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }

                    if (success && filePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = { onOpenFile(filePath) }) { Text("打开文件") }
                    }
                }
            }
        }
    }
}

/**
 * 处理Android应用导出
 * @param context 应用上下文
 * @param packageName 包名
 * @param appName 应用名称
 * @param iconUri 图标URI
 * @param webContentDir 网页内容目录
 * @param onProgress 进度回调
 * @param onComplete 完成回调
 */
suspend fun exportAndroidApp(
        context: Context,
        packageName: String,
        appName: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    try {
        withContext(Dispatchers.IO) {
            onProgress(0.1f, "准备基础APK文件...")

            // 1. 初始化APK编辑器
            val apkEditor = ApkEditor.fromAsset(context, "subpack/android.apk")

            // 2. 解压APK
            onProgress(0.2f, "解压APK...")
            apkEditor.extract()

            // 3. 修改包名和应用名
            onProgress(0.3f, "修改应用信息...")
            apkEditor.changePackageName(packageName)
            apkEditor.changeAppName(appName)

            // 4. 更改图标（如果提供）
            if (iconUri != null) {
                onProgress(0.4f, "更换应用图标...")
                context.contentResolver.openInputStream(iconUri)?.use { inputStream ->
                    apkEditor.changeIcon(inputStream)
                }
            }

            // 5. 复制网页文件到APK
            onProgress(0.5f, "打包网页内容...")

            // 创建临时目录用于存储网页文件
            val extractedDir = apkEditor.getExtractedDir()
            if (extractedDir == null) {
                throw RuntimeException("APK解压目录不存在")
            }
            val webAssetsDir = File(extractedDir, "assets/flutter_assets/assets/web_content")
            if (!webAssetsDir.exists()) {
                webAssetsDir.mkdirs()
            }
            Log.d(
                    "ExportDialogs",
                    "复制网页文件到APK ${webContentDir.absolutePath} -> ${webAssetsDir.absolutePath}"
            )

            // 复制网页文件
            copyDirectory(webContentDir, webAssetsDir)

            // 6. 准备签名文件
            onProgress(0.7f, "准备签名...")
            // 使用KeyStoreHelper获取密钥库
            val keyStoreFile = KeyStoreHelper.getOrCreateKeystore(context)
            Log.d(
                    "ExportDialogs",
                    "签名使用密钥库: ${keyStoreFile.absolutePath}, 大小: ${keyStoreFile.length()}"
            )

            // 7. 设置签名信息并执行签名
            onProgress(0.8f, "签名APK...")
            // 使用下载目录下的Operit/exports子目录
            val outputDir =
                    File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Operit/exports"
                    )
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputName = "WebApp_${Date().time}.apk"
            val outputFile = File(outputDir, outputName)

            Log.d("ExportDialogs", "即将签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: androidkey")
            apkEditor
                    .withSignature(
                            keyStoreFile,
                            "android", // 密码
                            "androidkey", // 别名
                            "android" // 密钥密码
                    )
                    .setOutput(outputFile)

            // 8. 打包并签名
            onProgress(0.9f, "完成打包...")
            try {
                val signedApk = apkEditor.repackAndSign()

                // 9. 清理
                apkEditor.cleanup()

                onProgress(1.0f, "导出完成!")
                onComplete(true, signedApk.absolutePath, null)
            } catch (e: Exception) {
                Log.e("ExportDialogs", "签名APK失败", e)
                onComplete(false, null, "签名APK失败: ${e.message}")
                apkEditor.cleanup() // 确保失败时也清理资源
            }
        }
    } catch (e: Exception) {
        Log.e("ExportDialogs", "导出失败", e)
        onComplete(false, null, "导出失败: ${e.message}")
    }
}

/** 处理Windows应用导出 */
suspend fun exportWindowsApp(
        context: Context,
        appName: String,
        iconUri: Uri?,
        webContentDir: File,
        onProgress: (Float, String) -> Unit,
        onComplete: (success: Boolean, filePath: String?, errorMessage: String?) -> Unit
) {
    try {
        withContext(Dispatchers.IO) {
            onProgress(0.1f, "准备Windows应用模板...")

            // 创建输出目录 - 使用下载目录下的Operit/exports子目录
            val outputDir =
                    File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Operit/exports"
                    )
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 创建临时工作目录
            val tempDir = File(context.cacheDir, "windows_export_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            try {
                // 1. 从assets复制windows.zip模板到临时目录
                onProgress(0.2f, "复制应用模板...")
                val templateZip = File(tempDir, "windows.zip")
                context.assets.open("subpack/windows.zip").use { input ->
                    FileOutputStream(templateZip).use { output -> input.copyTo(output) }
                }

                // 2. 解压windows.zip
                onProgress(0.3f, "解压应用模板...")
                val extractedDir = File(tempDir, "extracted")
                extractedDir.mkdirs()

                // 解压ZIP文件
                java.util.zip.ZipFile(templateZip).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryFile = File(extractedDir, entry.name)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(entryFile).use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }

                // 3. 如果提供了图标，修改assistance_subpack.exe的图标
                if (iconUri != null) {
                    onProgress(0.4f, "更换应用图标...")
                    val mainExe = File(extractedDir, "assistance_subpack.exe")
                    if (mainExe.exists()) {
                        try {
                            val exeEditor = ExeEditor.fromFile(context, mainExe)
                            context.contentResolver.openInputStream(iconUri)?.use { input ->
                                exeEditor.changeIcon(input).setOutput(mainExe).process()
                            }
                            Log.d("ExportDialogs", "已更换Windows应用图标")
                        } catch (e: Exception) {
                            Log.e("ExportDialogs", "更换Windows应用图标失败", e)
                            // 继续执行，不因图标失败而中断整个导出流程
                        }
                    } else {
                        Log.e("ExportDialogs", "未找到assistance_subpack.exe文件")
                    }
                }

                // 4. 复制网页内容到data\flutter_assets\assets\web_content
                onProgress(0.5f, "复制网页内容...")
                val webContentTarget = File(extractedDir, "data/flutter_assets/assets/web_content")
                if (!webContentTarget.exists()) {
                    webContentTarget.mkdirs()
                }

                Log.d(
                        "ExportDialogs",
                        "复制网页文件到Windows应用: ${webContentDir.absolutePath} -> ${webContentTarget.absolutePath}"
                )
                copyDirectory(webContentDir, webContentTarget)

                // 5. 创建最终输出文件名
                val timestamp =
                        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(java.util.Date())
                val safeName = appName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val outputZip = File(outputDir, "${safeName}_${timestamp}.zip")

                // 6. 重新打包为ZIP
                onProgress(0.8f, "打包应用...")

                // 确保输出文件不存在
                if (outputZip.exists()) {
                    outputZip.delete()
                }

                // 创建ZIP文件
                val buffer = ByteArray(1024)
                java.util.zip.ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                    // 添加文件到ZIP
                    addDirToZip(extractedDir, extractedDir, zipOut, buffer)
                }

                onProgress(1.0f, "导出完成!")
                onComplete(true, outputZip.absolutePath, null)
            } catch (e: Exception) {
                Log.e("ExportDialogs", "Windows应用导出过程失败", e)
                onComplete(false, null, "导出过程失败: ${e.message}")
            } finally {
                // 7. 清理临时文件
                try {
                    onProgress(0.9f, "清理临时文件...")
                    tempDir.deleteRecursively()
                } catch (e: Exception) {
                    Log.e("ExportDialogs", "清理临时文件失败", e)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("ExportDialogs", "Windows应用导出失败", e)
        onComplete(false, null, "导出失败: ${e.message}")
    }
}

/** 递归添加目录到ZIP文件 */
private fun addDirToZip(
        rootDir: File,
        currentDir: File,
        zipOut: java.util.zip.ZipOutputStream,
        buffer: ByteArray
) {
    currentDir.listFiles()?.forEach { file ->
        val relativePath =
                file.absolutePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")

        if (file.isDirectory) {
            if (file.listFiles()?.isNotEmpty() == true) {
                addDirToZip(rootDir, file, zipOut, buffer)
            } else {
                val entry = java.util.zip.ZipEntry("$relativePath/")
                zipOut.putNextEntry(entry)
                zipOut.closeEntry()
            }
        } else {
            try {
                val entry = java.util.zip.ZipEntry(relativePath)
                zipOut.putNextEntry(entry)

                FileInputStream(file).use { input ->
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) {
                        zipOut.write(buffer, 0, len)
                    }
                }

                zipOut.closeEntry()
                Log.d("ExportDialogs", "已添加文件到ZIP: $relativePath")
            } catch (e: Exception) {
                Log.e("ExportDialogs", "添加文件到ZIP失败: $relativePath", e)
            }
        }
    }
}

/** 创建或获取应用签名密钥库 */
private fun createOrGetKeystore(context: Context): File {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.getOrCreateKeystore(context)
}

/** 验证密钥库文件是否有效 */
private fun validateKeystore(file: File, type: String, password: String): Boolean {
    // 直接使用KeyStoreHelper
    return KeyStoreHelper.validateKeystore(file, type, password)
}

/** 复制目录及其内容 */
private fun copyDirectory(sourceDir: File, destDir: File) {
    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    sourceDir.listFiles()?.forEach { file ->
        val destFile = File(destDir, file.name)
        if (file.isDirectory) {
            copyDirectory(file, destFile)
        } else {
            try {
                // 如果目标文件已存在，则先删除
                if (destFile.exists()) {
                    destFile.delete()
                }

                // 确保父目录存在
                destFile.parentFile?.mkdirs()

                // 复制文件内容
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }

                Log.d("ExportDialogs", "成功复制文件: ${file.absolutePath} -> ${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(
                        "ExportDialogs",
                        "复制文件失败: ${file.absolutePath} -> ${destFile.absolutePath}",
                        e
                )
            }
        }
    }
}
