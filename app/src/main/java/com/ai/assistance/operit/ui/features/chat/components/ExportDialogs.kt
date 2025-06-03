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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
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
                    Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Red,
                            textAlign = TextAlign.Center
                    )
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
            val extractedDir = File(context.cacheDir, "apk_extracted")
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
            val keyStoreFile = createOrGetKeystore(context)
            Log.d("ExportDialogs", "签名使用密钥库: ${keyStoreFile.absolutePath}, 大小: ${keyStoreFile.length()}")

            // 7. 设置签名信息并执行签名
            onProgress(0.8f, "签名APK...")
            // 使用下载目录下的Operit/exports子目录
            val outputDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Operit/exports")
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
            val signedApk = apkEditor.repackAndSign()

            // 9. 清理
            apkEditor.cleanup()

            onProgress(1.0f, "导出完成!")
            onComplete(true, signedApk.absolutePath, null)
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
    // 这里应该是Windows应用导出的实现
    // 为简化示例，现在只是简单的复制网页内容到一个文件夹中
    try {
        withContext(Dispatchers.IO) {
            onProgress(0.2f, "准备导出目录...")

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

            val appDir = File(outputDir, "${appName}_${Date().time}")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            // 复制网页内容
            onProgress(0.5f, "复制网页内容...")
            copyDirectory(webContentDir, appDir)

            onProgress(0.7f, "创建启动文件...")

            // 创建简单的启动HTML
            val launcherHtml = File(appDir, "index.html")
            if (!launcherHtml.exists()) {
                launcherHtml.createNewFile()
                launcherHtml.writeText(
                        """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>$appName</title>
                    </head>
                    <body>
                        <script>
                            // 重定向到实际的首页
                            window.location.href = "./index.html";
                        </script>
                    </body>
                    </html>
                    """.trimIndent()
                )
            }

            // 如果提供了图标，就保存图标
            if (iconUri != null) {
                onProgress(0.8f, "设置应用图标...")
                context.contentResolver.openInputStream(iconUri)?.use { input ->
                    val iconFile = File(appDir, "favicon.ico")
                    FileOutputStream(iconFile).use { output -> input.copyTo(output) }
                }
            }

            // 创建简单的ZIP文件作为输出
            onProgress(0.9f, "打包应用...")
            val outputZip = File(outputDir, "${appName}_${Date().time}.zip")

            // 实际项目中应使用适当的ZIP库来打包
            // 这里只是示例，没有实际实现ZIP打包

            onProgress(1.0f, "导出完成!")
            onComplete(true, appDir.absolutePath, null)
        }
    } catch (e: Exception) {
        onComplete(false, null, "导出失败: ${e.message}")
    }
}

/** 创建或获取应用签名密钥库 */
private fun createOrGetKeystore(context: Context): File {
    val keyStoreFile = File(context.filesDir, "app_signing.keystore")

    // 如果已经存在合适大小的密钥库文件，直接返回
    if (keyStoreFile.exists() && keyStoreFile.length() > 1000) {
        // 尝试验证密钥库文件
        try {
            // 首先尝试PKCS12格式加载
            FileInputStream(keyStoreFile).use { input ->
                val keyStore = KeyStore.getInstance("PKCS12")
                keyStore.load(input, "android".toCharArray())

                // 确认至少包含一个密钥
                if (keyStore.aliases().hasMoreElements()) {
                    Log.d("ExportDialogs", "已验证PKCS12格式密钥库有效")
                    return keyStoreFile
                }
            }
        } catch (e: Exception) {
            // PKCS12格式加载失败，尝试JKS格式
            try {
                FileInputStream(keyStoreFile).use { input ->
                    val keyStore = KeyStore.getInstance("JKS")
                    keyStore.load(input, "android".toCharArray())

                    // 确认至少包含一个密钥
                    if (keyStore.aliases().hasMoreElements()) {
                        Log.d("ExportDialogs", "已验证JKS格式密钥库有效")
                        return keyStoreFile
                    }
                }
            } catch (e2: Exception) {
                // 两种格式都加载失败，认为密钥库无效
                Log.e("ExportDialogs", "密钥库验证失败: ${e.message}, ${e2.message}")
                keyStoreFile.delete()
            }
        }
    }

    try {
        // 复制assets中预置的密钥库
        val assetKeystore = "app_signing.keystore"

        // 确保assets中存在该文件
        try {
            val assetFiles = context.assets.list("") ?: emptyArray()
            if (!assetFiles.contains(assetKeystore)) {
                throw RuntimeException("在assets目录中找不到密钥库文件: $assetKeystore")
            }
        } catch (e: Exception) {
            throw RuntimeException("列出assets文件失败: ${e.message}")
        }

        // 从assets复制密钥库文件
        context.assets.open(assetKeystore).use { input ->
            val bytes = input.readBytes()
            if (bytes.size < 1000) {
                throw RuntimeException("密钥库文件大小异常: ${bytes.size}字节")
            }

            if (keyStoreFile.exists()) {
                keyStoreFile.delete()
            }

            keyStoreFile.outputStream().use { output ->
                output.write(bytes)
                output.flush()
            }
        }

        if (!keyStoreFile.exists() || keyStoreFile.length() < 1000) {
            throw RuntimeException("无法正确复制密钥库文件")
        }

        // 验证复制后的密钥库
        try {
            // 首先尝试PKCS12格式
            try {
                FileInputStream(keyStoreFile).use { input ->
                    val keyStore = KeyStore.getInstance("PKCS12")
                    keyStore.load(input, "android".toCharArray())
                    Log.d("ExportDialogs", "验证成功：密钥库是PKCS12格式")
                }
            } catch (e: Exception) {
                // 如果PKCS12失败，尝试JKS
                FileInputStream(keyStoreFile).use { input2 ->
                    val keyStore = KeyStore.getInstance("JKS")
                    keyStore.load(input2, "android".toCharArray())
                    Log.d("ExportDialogs", "验证成功：密钥库是JKS格式")
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("无法验证复制的密钥库文件: ${e.message}")
        }
    } catch (e: Exception) {
        // 处理异常
        e.printStackTrace()
        throw RuntimeException("准备密钥库失败: ${e.message}")
    }

    return keyStoreFile
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
            file.inputStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
