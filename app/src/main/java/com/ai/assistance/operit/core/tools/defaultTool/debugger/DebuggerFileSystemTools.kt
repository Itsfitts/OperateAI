package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityFileSystemTools
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 调试者级别的文件系统工具，继承无障碍级别，使用ADB命令实现 */
open class DebuggerFileSystemTools(context: Context) : AccessibilityFileSystemTools(context) {

    companion object {
        private const val TAG = "DebuggerFileSystemTools"

        // Maximum allowed file size for operations
        protected const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    }

    /** List files in a directory */
    override suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 确保目录路径末尾有斜杠
            val normalizedPath = if (path.endsWith("/")) path else "$path/"

            // 使用ls -la命令获取详细的文件列表
            Log.d(TAG, "Using ls -la command for path: $normalizedPath")
            val listResult = AndroidShellExecutor.executeShellCommand("ls -la \"$normalizedPath\"")

            if (listResult.success) {
                Log.d(TAG, "ls -la command output: ${listResult.stdout}")

                // 解析ls -la命令输出
                val entries = parseDetailedDirectoryListing(listResult.stdout, normalizedPath)

                Log.d(TAG, "Parsed ${entries.size} entries from ls -la output")

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = DirectoryListingData(path, entries),
                        error = ""
                )
            } else {
                Log.w(TAG, "ls -la command failed: ${listResult.stderr}")

                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to list directory: ${listResult.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** Parse the output of the ls -la command into structured data */
    protected fun parseDetailedDirectoryListing(
            output: String,
            path: String
    ): List<DirectoryListingData.FileEntry> {
        val lines = output.trim().split("\n")
        val entries = mutableListOf<DirectoryListingData.FileEntry>()

        Log.d(TAG, "Parsing ${lines.size} lines from ls -la output")

        // 跳过第一行总计行
        val startIndex = if (lines.isNotEmpty() && lines[0].startsWith("total")) 1 else 0

        // 日期格式化器，用于解析日期时间字符串
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

        for (i in startIndex until lines.size) {
            try {
                val line = lines[i]
                if (line.isBlank()) continue

                // 打印每一行以便调试
                Log.d(TAG, "Parsing line: $line")

                // Android上ls -la输出格式: crwxrw--- 2 u0_a425 media_rw 4056 2025-03-14 06:04 Android
                // 符号链接格式: lrwxrwxrwx 1 root root 12 2025-03-14 06:04 filename -> /path/to/target

                // 使用正则表达式解析Android上的ls -la输出
                val androidRegex =
                        """^(\S+)\s+(\d+)\s+(\S+\s*\S*)\s+(\S+)\s+(\d+)\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(.+)$""".toRegex()
                val androidMatch = androidRegex.find(line)

                if (androidMatch != null) {
                    // 特定于Android的格式解析
                    val permissions = androidMatch.groupValues[1]
                    val size = androidMatch.groupValues[5].toLongOrNull() ?: 0
                    val date = androidMatch.groupValues[6]
                    val time = androidMatch.groupValues[7]
                    var name = androidMatch.groupValues[8]
                    val isDirectory = permissions.startsWith("d") || permissions.startsWith("c")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        Log.d(TAG, "Found symlink: $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 将日期和时间转换为时间戳
                    val dateTimeStr = "$date $time"
                    val timestamp =
                            try {
                                val parsedDate = dateFormat.parse(dateTimeStr)
                                parsedDate?.time?.toString() ?: "0"
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing date: $dateTimeStr", e)
                                "0" // 解析失败时使用默认时间戳
                            }

                    Log.d(
                            TAG,
                            "Successfully parsed $name with date $dateTimeStr -> timestamp $timestamp"
                    )

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp // 使用时间戳字符串
                            )
                    )
                    continue
                }

                // 如果Android特定格式不匹配，尝试通用格式
                val genericRegex =
                        """^([\-ld][\w-]{9})\s+(\d+)\s+(\w+)\s+(\w+)\s+(\d+)\s+([\w\d\s\-:\.]+)\s+(.+)$""".toRegex()
                val match = genericRegex.find(line)

                if (match != null) {
                    val permissions = match.groupValues[1]
                    val size = match.groupValues[5].toLongOrNull() ?: 0
                    val dateTimeStr = match.groupValues[6].trim()
                    var name = match.groupValues[7]
                    val isDirectory = permissions.startsWith("d")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        Log.d(TAG, "Found symlink (generic): $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 尝试解析通用格式的日期时间
                    val timestamp =
                            try {
                                if (dateTimeStr.matches(
                                                """^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}$""".toRegex()
                                        )
                                ) {
                                    val parsedDate = dateFormat.parse(dateTimeStr)
                                    parsedDate?.time?.toString() ?: "0"
                                } else {
                                    // 如果不是YYYY-MM-DD HH:MM格式，返回当前时间
                                    System.currentTimeMillis().toString()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing generic date: $dateTimeStr", e)
                                "0"
                            }

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp
                            )
                    )
                } else {
                    // 如果标准正则表达式也不匹配，使用更宽松的解析方法
                    // 权限字段始终是10个字符
                    if (line.length < 10) continue

                    val permissions = line.substring(0, 10).trim()
                    val isDirectory = permissions.startsWith("d") || permissions.startsWith("c")

                    // 解析剩余部分
                    val parts = line.substring(10).trim().split("\\s+".toRegex())

                    if (parts.size < 6) {
                        Log.w(TAG, "Invalid ls -la format: $line")
                        continue
                    }

                    // 查找日期部分 - Android上通常是YYYY-MM-DD格式
                    val dateIndex =
                            parts.indexOfFirst { it.matches("""^\d{4}-\d{2}-\d{2}$""".toRegex()) }

                    if (dateIndex < 0 || dateIndex + 1 >= parts.size) {
                        Log.w(TAG, "Cannot find date in line: $line")
                        continue
                    }

                    // 日期后面的字段通常是时间 (HH:MM)
                    val timeIndex = dateIndex + 1

                    // 时间后面的所有内容都是文件名
                    val nameStartIndex = timeIndex + 1
                    if (nameStartIndex >= parts.size) {
                        Log.w(TAG, "Cannot find filename position: $line")
                        continue
                    }

                    var name = parts.subList(nameStartIndex, parts.size).joinToString(" ")
                    val isSymlink = permissions.startsWith("l")

                    // 处理符号链接格式 "name -> target"
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                        Log.d(TAG, "Found symlink (fallback): $name")
                    }

                    // 跳过 . 和 .. 条目
                    if (name == "." || name == "..") continue

                    // 文件大小通常在用户和组之后，日期之前
                    val sizeIndex = dateIndex - 1
                    val size = if (sizeIndex >= 0) parts[sizeIndex].toLongOrNull() ?: 0 else 0

                    // 组合日期和时间，并转换为时间戳
                    val dateTimeStr = "${parts[dateIndex]} ${parts[timeIndex]}"
                    val timestamp =
                            try {
                                val parsedDate = dateFormat.parse(dateTimeStr)
                                parsedDate?.time?.toString() ?: "0"
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing fallback date: $dateTimeStr", e)
                                "0"
                            }

                    entries.add(
                            DirectoryListingData.FileEntry(
                                    name = name,
                                    isDirectory = isDirectory,
                                    size = size,
                                    permissions = permissions,
                                    lastModified = timestamp
                            )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing directory entry: ${lines[i]}", e)
                // 跳过这一行但继续处理其他行
            }
        }

        return entries
    }

    /** 将八进制权限格式转换为字符串表示 (如 "rwxr-xr-x") */
    protected fun convertOctalPermToString(octalPerm: String): String {
        try {
            val permInt = octalPerm.toInt(8)
            val permChars = CharArray(9)

            // 所有者权限
            permChars[0] = if (permInt and 0x100 != 0) 'r' else '-'
            permChars[1] = if (permInt and 0x80 != 0) 'w' else '-'
            permChars[2] = if (permInt and 0x40 != 0) 'x' else '-'

            // 组权限
            permChars[3] = if (permInt and 0x20 != 0) 'r' else '-'
            permChars[4] = if (permInt and 0x10 != 0) 'w' else '-'
            permChars[5] = if (permInt and 0x8 != 0) 'x' else '-'

            // 其他用户权限
            permChars[6] = if (permInt and 0x4 != 0) 'r' else '-'
            permChars[7] = if (permInt and 0x2 != 0) 'w' else '-'
            permChars[8] = if (permInt and 0x1 != 0) 'x' else '-'

            return String(permChars)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting octal permission: $octalPerm", e)
            return "???"
        }
    }

    /** Read file content */
    override suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // First check if the file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f \"$path\" && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "File does not exist: $path"
                )
            }

            // Check file size before reading
            val sizeResult = AndroidShellExecutor.executeShellCommand("stat -c %s \"$path\"")
            if (sizeResult.success) {
                val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
                if (size > MAX_FILE_SIZE_BYTES) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error =
                                    "File is too large (${size / 1024} KB). Maximum allowed size is ${MAX_FILE_SIZE_BYTES / 1024} KB."
                    )
                }
            }

            // 检查文件扩展名
            val fileExt = path.substringAfterLast('.', "").lowercase()

            // 如果是Word文档，先转换为文本
            if (fileExt == "doc" || fileExt == "docx") {
                Log.d(TAG, "Detected Word document, converting to text before reading")

                // 创建临时文件路径用于存储转换后的文本
                val tempFilePath = "${path}_converted_${System.currentTimeMillis()}.txt"

                try {
                    // 使用AIToolHandler获取并使用文件转换工具
                    val fileConverterTool =
                            AITool(
                                    name = "convert_file",
                                    parameters =
                                            listOf(
                                                    ToolParameter("source_path", path),
                                                    ToolParameter("target_path", tempFilePath)
                                            )
                            )

                    // 获取AIToolHandler实例
                    val toolHandler = AIToolHandler.getInstance(context)

                    // 执行文件转换
                    val conversionResult = toolHandler.executeTool(fileConverterTool)

                    if (conversionResult.success) {
                        Log.d(TAG, "Successfully converted Word document to text")

                        // 读取转换后的文本文件
                        val textContent =
                                AndroidShellExecutor.executeShellCommand("cat \"$tempFilePath\"")

                        if (textContent.success) {
                            // 创建结果
                            val result =
                                    ToolResult(
                                            toolName = tool.name,
                                            success = true,
                                            result =
                                                    FileContentData(
                                                            path = path,
                                                            content = textContent.stdout,
                                                            size =
                                                                    textContent.stdout.length
                                                                            .toLong()
                                                    ),
                                            error = ""
                                    )

                            // 删除临时文件
                            AndroidShellExecutor.executeShellCommand("rm -f \"$tempFilePath\"")

                            return result
                        }
                    } else {
                        Log.w(
                                TAG,
                                "Word conversion failed: ${conversionResult.error}, falling back to raw content"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during Word document conversion", e)
                    // 转换失败，继续尝试读取原始文件
                }
            }

            // 对于非Word文档或转换失败的情况，直接读取文件内容
            val result = AndroidShellExecutor.executeShellCommand("cat \"$path\"")

            if (result.success) {
                val size = sizeResult.stdout.trim().toLongOrNull() ?: result.stdout.length.toLong()

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileContentData(path = path, content = result.stdout, size = size),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error reading file: ${e.message}"
            )
        }
    }

    /** Write content to a file */
    override suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "write",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            // 确保目标目录存在
            val directory = File(path).parent
            if (directory != null) {
                val mkdirResult = AndroidShellExecutor.executeShellCommand("mkdir -p '$directory'")
                if (!mkdirResult.success) {
                    Log.w(TAG, "Warning: Failed to create parent directory: ${mkdirResult.stderr}")
                }
            }

            // 直接使用echo命令写入内容
            // 对内容进行base64编码，避免特殊字符问题
            val contentBase64 =
                    android.util.Base64.encodeToString(
                            content.toByteArray(),
                            android.util.Base64.NO_WRAP
                    )

            // 使用两种写入方法中的一种:
            // 方法1: 使用base64命令解码并写入文件
            val redirectOperator = if (append) ">>" else ">"
            val writeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "echo '$contentBase64' | base64 -d $redirectOperator '$path'"
                    )

            if (!writeResult.success) {
                Log.e(TAG, "Failed to write with base64 method: ${writeResult.stderr}")
                // 方法2: 尝试直接写入，无需base64
                val fallbackResult =
                        AndroidShellExecutor.executeShellCommand(
                                "printf '%s' '$content' $redirectOperator '$path'"
                        )
                if (!fallbackResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = if (append) "append" else "write",
                                            path = path,
                                            successful = false,
                                            details =
                                                    "Failed to write to file: ${fallbackResult.stderr}"
                                    ),
                            error = "Failed to write to file: ${fallbackResult.stderr}"
                    )
                }
            }

            // 验证写入是否成功
            val verifyResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (verifyResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "Write command completed but file does not exist. Possible permission issue."
                                ),
                        error =
                                "Write command completed but file does not exist. Possible permission issue."
                )
            }

            // 检查文件大小确认内容被写入
            val sizeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "stat -c %s '$path' 2>/dev/null || echo '0'"
                    )
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0
            if (size == 0L && content.isNotEmpty()) {
                // 文件存在但是大小为0，可能写入失败
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = if (append) "append" else "write",
                                        path = path,
                                        successful = false,
                                        details =
                                                "File was created but appears to be empty. Possible write failure."
                                ),
                        error = "File was created but appears to be empty. Possible write failure."
                )
            }

            val operation = if (append) "append" else "write"
            val details = if (append) "Content appended to $path" else "Content written to $path"

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileOperationData(
                                    operation = operation,
                                    path = path,
                                    successful = true,
                                    details = details
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file", e)

            // 提供更具体的错误信息
            val errorMessage =
                    when {
                        e is InterruptedException ||
                                e.message?.contains("interrupted", ignoreCase = true) == true ->
                                "ADB连接被中断，可能是网络不稳定导致。请检查ADB连接并重试。错误详情: ${e.message}"
                        e is java.net.SocketException ||
                                e.message?.contains("socket", ignoreCase = true) == true ->
                                "ADB网络连接异常，请检查设备是否仍然连接并重试。错误详情: ${e.message}"
                        e is java.io.IOException -> "文件IO错误: ${e.message}。请检查文件路径是否有写入权限。"
                        e.message?.contains("permission", ignoreCase = true) == true ->
                                "权限拒绝，无法写入文件: ${e.message}。请检查应用是否有适当的权限。"
                        else -> "写入文件时出错: ${e.message}"
                    }

            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = if (append) "append" else "write",
                                    path = path,
                                    successful = false,
                                    details = errorMessage
                            ),
                    error = errorMessage
            )
        }
    }

    /** Delete a file or directory */
    override suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        // Don't allow deleting system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { path.startsWith(it) }) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = path,
                                    successful = false,
                                    details = "Deleting system directories is not allowed"
                            ),
                    error = "Deleting system directories is not allowed"
            )
        }

        return try {
            val deleteCommand = if (recursive) "rm -rf $path" else "rm -f $path"
            val result = AndroidShellExecutor.executeShellCommand(deleteCommand)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = true,
                                        details = "Successfully deleted $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "delete",
                                        path = path,
                                        successful = false,
                                        details = "Failed to delete: ${result.stderr}"
                                ),
                        error = "Failed to delete: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file/directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "delete",
                                    path = path,
                                    successful = false,
                                    details = "Error deleting file/directory: ${e.message}"
                            ),
                    error = "Error deleting file/directory: ${e.message}"
            )
        }
    }

    /** Check if a file or directory exists */
    override suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path parameter is required"
            )
        }

        return try {
            // Check if the path exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$path' && echo 'exists' || echo 'not exists'"
                    )
            val exists = existsResult.success && existsResult.stdout.trim() == "exists"

            if (!exists) {
                // If it doesn't exist, return a simple FileExistsData with
                // exists=false
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = FileExistsData(path = path, exists = false),
                        error = ""
                )
            }

            // If it exists, check if it's a directory
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$path' && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.success && isDirResult.stdout.trim() == "true"

            // Get the size
            val sizeResult =
                    AndroidShellExecutor.executeShellCommand(
                            "stat -c %s '$path' 2>/dev/null || echo '0'"
                    )
            val size = sizeResult.stdout.trim().toLongOrNull() ?: 0

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = true,
                                    isDirectory = isDirectory,
                                    size = size
                            ),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileExistsData(
                                    path = path,
                                    exists = false,
                                    isDirectory = false,
                                    size = 0
                            ),
                    error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** Move or rename a file or directory */
    override suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }

        // Don't allow moving system directories
        val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
        if (restrictedPaths.any { sourcePath.startsWith(it) }) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Moving system directories is not allowed"
                            ),
                    error = "Moving system directories is not allowed"
            )
        }

        return try {
            val result = AndroidShellExecutor.executeShellCommand("mv '$sourcePath' '$destPath'")

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully moved $sourcePath to $destPath"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "move",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Failed to move file: ${result.stderr}"
                                ),
                        error = "Failed to move file: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "move",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error moving file: ${e.message}"
                            ),
                    error = "Error moving file: ${e.message}"
            )
        }
    }

    /** Copy a file or directory */
    override suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
        val recursive =
                tool.parameters.find { it.name == "recursive" }?.value?.toBoolean()
                        ?: true // 默认为true以支持目录复制

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Source and destination parameters are required"
                            ),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            // 首先检查源路径是否存在
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$sourcePath' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Source path does not exist: $sourcePath"
                                ),
                        error = "Source path does not exist: $sourcePath"
                )
            }

            // 检查是否为目录
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$sourcePath' && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.stdout.trim() == "true"

            // 确保目标父目录存在
            val destParentDir = destPath.substringBeforeLast('/')
            if (destParentDir.isNotEmpty()) {
                AndroidShellExecutor.executeShellCommand("mkdir -p '$destParentDir'")
            }

            // 根据是否为目录选择不同的复制命令
            val copyCommand =
                    if (isDirectory && recursive) {
                        "cp -r '$sourcePath' '$destPath'"
                    } else if (!isDirectory) {
                        "cp '$sourcePath' '$destPath'"
                    } else {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Cannot copy directory without recursive flag"
                                        ),
                                error = "Cannot copy directory without recursive flag"
                        )
                    }

            val result = AndroidShellExecutor.executeShellCommand(copyCommand)

            if (result.success) {
                // 验证复制是否成功
                val verifyResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -e '$destPath' && echo 'exists' || echo 'not exists'"
                        )
                if (verifyResult.stdout.trim() != "exists") {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "copy",
                                            path = sourcePath,
                                            successful = false,
                                            details =
                                                    "Copy command completed but destination does not exist"
                                    ),
                            error = "Copy command completed but destination does not exist"
                    )
                }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = true,
                                        details =
                                                "Successfully copied ${if (isDirectory) "directory" else "file"} $sourcePath to $destPath"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Failed to copy: ${result.stderr}"
                                ),
                        error = "Failed to copy: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file/directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "copy",
                                    path = sourcePath,
                                    successful = false,
                                    details = "Error copying file/directory: ${e.message}"
                            ),
                    error = "Error copying file/directory: ${e.message}"
            )
        }
    }

    /** Create a directory */
    override suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val createParents =
                tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = "",
                                    successful = false,
                                    details = "Path parameter is required"
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            val mkdirCommand = if (createParents) "mkdir -p '$path'" else "mkdir '$path'"
            val result = AndroidShellExecutor.executeShellCommand(mkdirCommand)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = true,
                                        details = "Successfully created directory $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "mkdir",
                                        path = path,
                                        successful = false,
                                        details = "Failed to create directory: ${result.stderr}"
                                ),
                        error = "Failed to create directory: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "mkdir",
                                    path = path,
                                    successful = false,
                                    details = "Error creating directory: ${e.message}"
                            ),
                    error = "Error creating directory: ${e.message}"
            )
        }
    }

    /** Search for files matching a pattern */
    override suspend fun findFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        if (path.isBlank() || pattern.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Path and pattern parameters are required"
            )
        }

        return try {
            // Add options for different search modes
            val usePathPattern =
                    tool.parameters.find { it.name == "use_path_pattern" }?.value?.toBoolean()
                            ?: false
            val caseInsensitive =
                    tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean()
                            ?: false

            // Add depth control parameter (default to -1 for unlimited depth/fully
            // recursive)
            val maxDepth =
                    tool.parameters.find { it.name == "max_depth" }?.value?.toIntOrNull() ?: -1

            // Determine which search option to use
            val searchOption =
                    if (usePathPattern) {
                        if (caseInsensitive) "-ipath" else "-path"
                    } else {
                        if (caseInsensitive) "-iname" else "-name"
                    }

            // Properly escape the pattern if quotes are required
            val escapedPattern = pattern.replace("'", "'\\''")
            val patternForCommand = "'$escapedPattern'"

            // Build the command with depth control if specified
            val depthOption = if (maxDepth >= 0) "-maxdepth $maxDepth" else ""
            val command =
                    "find ${if(path.endsWith("/")) path else "$path/"} $depthOption $searchOption $patternForCommand"

            val result = AndroidShellExecutor.executeShellCommand(command)

            // Always consider the command successful, and check the output
            val fileList = result.stdout.trim()

            // 将结果转换为字符串列表
            val files =
                    if (fileList.isBlank()) {
                        emptyList()
                    } else {
                        fileList.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    }

            return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FindFilesResultData(path = path, pattern = pattern, files = files),
                    error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for files", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FindFilesResultData(
                                    path = path,
                                    pattern = pattern,
                                    files = emptyList()
                            ),
                    error = "Error searching for files: ${e.message}"
            )
        }
    }

    /** Get file information */
    override suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = "",
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Path parameter is required"
            )
        }

        return try {
            // Check if file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = false,
                                        fileType = "",
                                        size = 0,
                                        permissions = "",
                                        owner = "",
                                        group = "",
                                        lastModified = "",
                                        rawStatOutput = ""
                                ),
                        error = "File or directory does not exist: $path"
                )
            }

            // Get file details using stat
            val statResult = AndroidShellExecutor.executeShellCommand("stat '$path'")

            if (statResult.success) {
                // Get file type
                val fileTypeResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -d '$path' && echo 'directory' || (test -f '$path' && echo 'file' || echo 'other')"
                        )
                val fileType = fileTypeResult.stdout.trim()

                // Get file size
                val sizeResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %s '$path' 2>/dev/null || echo '0'"
                        )
                val size = sizeResult.stdout.trim().toLongOrNull() ?: 0

                // Get file permissions
                val permissionsResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %A '$path' 2>/dev/null || echo ''"
                        )
                val permissions = permissionsResult.stdout.trim()

                // Get owner and group
                val ownerResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %U '$path' 2>/dev/null || echo ''"
                        )
                val owner = ownerResult.stdout.trim()

                val groupResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %G '$path' 2>/dev/null || echo ''"
                        )
                val group = groupResult.stdout.trim()

                // Get last modified time
                val modifiedResult =
                        AndroidShellExecutor.executeShellCommand(
                                "stat -c %y '$path' 2>/dev/null || echo ''"
                        )
                val lastModified = modifiedResult.stdout.trim()

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = true,
                                        fileType = fileType,
                                        size = size,
                                        permissions = permissions,
                                        owner = owner,
                                        group = group,
                                        lastModified = lastModified,
                                        rawStatOutput = statResult.stdout
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileInfoData(
                                        path = path,
                                        exists = true,
                                        fileType = "",
                                        size = 0,
                                        permissions = "",
                                        owner = "",
                                        group = "",
                                        lastModified = "",
                                        rawStatOutput = ""
                                ),
                        error = "Failed to get file information: ${statResult.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file information", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileInfoData(
                                    path = path,
                                    exists = false,
                                    fileType = "",
                                    size = 0,
                                    permissions = "",
                                    owner = "",
                                    group = "",
                                    lastModified = "",
                                    rawStatOutput = ""
                            ),
                    error = "Error getting file information: ${e.message}"
            )
        }
    }

    /** Zip files or directories */
    override suspend fun zipFiles(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (sourcePath.isBlank() || zipPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            // First, check if the source path exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -e '$sourcePath' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Source file or directory does not exist: $sourcePath"
                )
            }

            // Check if source is a directory
            val isDirResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -d '$sourcePath' && echo 'true' || echo 'false'"
                    )
            val isDirectory = isDirResult.stdout.trim() == "true"

            // Create parent directory for zip file if needed
            val zipDir = File(zipPath).parent
            if (zipDir != null) {
                AndroidShellExecutor.executeShellCommand("mkdir -p '$zipDir'")
            }

            // Use Java's ZipOutputStream to create the zip file
            // We'll use ADB to copy files to/from the device and process locally
            val sourceFile = File(sourcePath)
            val destZipFile = File(zipPath)

            // Initialize buffer for file copy
            val buffer = ByteArray(1024)

            // Create temporary file for processing - using external files directory for
            // better
            // permissions
            val tempDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val tempSourceFile = File(tempDir, "temp_source_${System.currentTimeMillis()}")
            val tempZipFile = File(tempDir, "temp_zip_${System.currentTimeMillis()}.zip")

            try {
                // Make sure the temp directory exists
                tempDir.mkdirs()

                if (isDirectory) {
                    // For directories, we need to list all files and add them
                    // to the zip
                    val listResult =
                            AndroidShellExecutor.executeShellCommand("find '$sourcePath' -type f")
                    val fileList = listResult.stdout.trim().split("\n").filter { it.isNotEmpty() }

                    // Create ZIP output stream
                    val fos = FileOutputStream(tempZipFile)
                    val zos = ZipOutputStream(BufferedOutputStream(fos))

                    try {
                        for (filePath in fileList) {
                            // Get the file path relative to the source
                            // directory
                            val relativePath = filePath.substring(sourcePath.length + 1)

                            // Copy the file from device to temp file
                            val pullResult =
                                    AndroidShellExecutor.executeShellCommand(
                                            "cat '$filePath' > '${tempSourceFile.absolutePath}'"
                                    )
                            if (!pullResult.success) {
                                continue // Skip this file if we
                                // can't pull it
                            }

                            // Add the file to the ZIP
                            val fis = FileInputStream(tempSourceFile)
                            val bis = BufferedInputStream(fis)

                            try {
                                // Add ZIP entry
                                val entry = ZipEntry(relativePath)
                                zos.putNextEntry(entry)

                                // Write file content to ZIP
                                var len: Int
                                while (bis.read(buffer).also { len = it } > 0) {
                                    zos.write(buffer, 0, len)
                                }

                                zos.closeEntry()
                            } finally {
                                bis.close()
                                fis.close()
                                tempSourceFile.delete()
                            }
                        }
                    } finally {
                        zos.close()
                        fos.close()
                    }
                } else {
                    // For a single file, simpler process
                    // Copy the file from device to temp file
                    val pullResult =
                            AndroidShellExecutor.executeShellCommand(
                                    "cat '$sourcePath' > '${tempSourceFile.absolutePath}'"
                            )
                    if (!pullResult.success) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Failed to read source file: ${pullResult.stderr}"
                        )
                    }

                    // Create zip file with single entry
                    val fos = FileOutputStream(tempZipFile)
                    val zos = ZipOutputStream(BufferedOutputStream(fos))

                    try {
                        val fis = FileInputStream(tempSourceFile)
                        val bis = BufferedInputStream(fis)

                        try {
                            // Add ZIP entry
                            val entry = ZipEntry(sourceFile.name)
                            zos.putNextEntry(entry)

                            // Write file content to ZIP
                            var len: Int
                            while (bis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }

                            zos.closeEntry()
                        } finally {
                            bis.close()
                            fis.close()
                        }
                    } finally {
                        zos.close()
                        fos.close()
                    }
                }

                // Log information about the temp ZIP file
                Log.d(
                        TAG,
                        "Temp ZIP file created at: ${tempZipFile.absolutePath}, size: ${tempZipFile.length()} bytes"
                )

                // Push the ZIP file to the destination
                val pushResult =
                        AndroidShellExecutor.executeShellCommand(
                                "cat '${tempZipFile.absolutePath}' > '$zipPath'"
                        )
                if (!pushResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to write ZIP file: ${pushResult.stderr}"
                    )
                }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "zip",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully compressed $sourcePath to $zipPath"
                                ),
                        error = ""
                )
            } finally {
                // Clean up temporary files
                tempSourceFile.delete()
                tempZipFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing files", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error compressing files: ${e.message}"
            )
        }
    }

    /** Unzip a zip file */
    override suspend fun unzipFiles(tool: AITool): ToolResult {
        val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (zipPath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source and destination parameters are required"
            )
        }

        return try {
            // Check if the zip file exists
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$zipPath' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Zip file does not exist: $zipPath"
                )
            }

            // Create destination directory if it doesn't exist
            AndroidShellExecutor.executeShellCommand("mkdir -p '$destPath'")

            // Create temporary files for processing - using external files directory
            // for better
            // permissions
            val tempDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val tempZipFile = File(tempDir, "temp_zip_${System.currentTimeMillis()}.zip")

            try {
                // Make sure the temp directory exists
                tempDir.mkdirs()

                // Copy the zip file from device to temp file
                val pullResult =
                        AndroidShellExecutor.executeShellCommand(
                                "cat '$zipPath' > '${tempZipFile.absolutePath}'"
                        )
                if (!pullResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to read zip file: ${pullResult.stderr}"
                    )
                }

                // Log information about the temp ZIP file
                Log.d(
                        TAG,
                        "Temp ZIP file loaded at: ${tempZipFile.absolutePath}, size: ${tempZipFile.length()} bytes"
                )

                // Extract files using ZipInputStream
                val buffer = ByteArray(1024)
                val zipInputStream =
                        ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile)))

                try {
                    var zipEntry: ZipEntry? = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        val fileName = zipEntry.name
                        val newFile = File(tempDir, "unzip_temp_${System.currentTimeMillis()}")

                        // Skip directories, but make sure they exist
                        if (zipEntry.isDirectory) {
                            val dirPath = "$destPath/$fileName"
                            AndroidShellExecutor.executeShellCommand("mkdir -p '$dirPath'")
                            zipInputStream.closeEntry()
                            zipEntry = zipInputStream.nextEntry
                            continue
                        }

                        // Create parent directories if needed
                        val filePath = "$destPath/$fileName"
                        val parentDirPath = File(filePath).parent
                        if (parentDirPath != null) {
                            AndroidShellExecutor.executeShellCommand("mkdir -p '$parentDirPath'")
                        }

                        // Extract file
                        val fileOutputStream = FileOutputStream(newFile)

                        try {
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                fileOutputStream.write(buffer, 0, len)
                            }
                        } finally {
                            fileOutputStream.close()
                        }

                        // Copy the extracted file to device
                        val pushResult =
                                AndroidShellExecutor.executeShellCommand(
                                        "cat '${newFile.absolutePath}' > '$filePath'"
                                )
                        if (!pushResult.success) {
                            Log.w(TAG, "Failed to copy extracted file: $fileName to $filePath")
                            // Continue with next file
                        }

                        // Clean up temp file
                        newFile.delete()

                        zipInputStream.closeEntry()
                        zipEntry = zipInputStream.nextEntry
                    }
                } finally {
                    zipInputStream.close()
                }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "unzip",
                                        path = zipPath,
                                        successful = true,
                                        details = "Successfully extracted $zipPath to $destPath"
                                ),
                        error = ""
                )
            } finally {
                tempZipFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip file", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error extracting zip file: ${e.message}"
            )
        }
    }

    /** 打开文件 使用系统默认应用打开文件 */
    override suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "open",
                                    path = "",
                                    successful = false,
                                    details = "必须提供path参数"
                            ),
                    error = "必须提供path参数"
            )
        }

        return try {
            // 首先检查文件是否存在
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = false,
                                        details = "文件不存在: $path"
                                ),
                        error = "文件不存在: $path"
                )
            }

            // 获取文件MIME类型
            val mimeTypeResult =
                    AndroidShellExecutor.executeShellCommand("file --mime-type -b '$path'")
            val mimeType =
                    if (mimeTypeResult.success) mimeTypeResult.stdout.trim()
                    else "application/octet-stream"

            // 使用Android intent打开文件
            val command = "am start -a android.intent.action.VIEW -d 'file://$path' -t '$mimeType'"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = true,
                                        details = "已使用系统应用打开文件: $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "open",
                                        path = path,
                                        successful = false,
                                        details = "打开文件失败: ${result.stderr}"
                                ),
                        error = "打开文件失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "open",
                                    path = path,
                                    successful = false,
                                    details = "打开文件时出错: ${e.message}"
                            ),
                    error = "打开文件时出错: ${e.message}"
            )
        }
    }

    /** 分享文件 调用系统分享功能 */
    override suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val title = tool.parameters.find { it.name == "title" }?.value ?: "分享文件"

        if (path.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = "",
                                    successful = false,
                                    details = "必须提供path参数"
                            ),
                    error = "必须提供path参数"
            )
        }

        return try {
            // 首先检查文件是否存在
            val existsResult =
                    AndroidShellExecutor.executeShellCommand(
                            "test -f '$path' && echo 'exists' || echo 'not exists'"
                    )
            if (existsResult.stdout.trim() != "exists") {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "share",
                                        path = path,
                                        successful = false,
                                        details = "文件不存在: $path"
                                ),
                        error = "文件不存在: $path"
                )
            }

            // 获取文件MIME类型
            val mimeTypeResult =
                    AndroidShellExecutor.executeShellCommand("file --mime-type -b '$path'")
            val mimeType =
                    if (mimeTypeResult.success) mimeTypeResult.stdout.trim()
                    else "application/octet-stream"

            // 使用Android intent分享文件
            val command =
                    "am start -a android.intent.action.SEND -t '$mimeType' --es android.intent.extra.SUBJECT '$title' --es android.intent.extra.STREAM 'file://$path' --ez android.intent.extra.STREAM_REFERENCE true"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "share",
                                        path = path,
                                        successful = true,
                                        details = "已打开分享界面，分享文件: $path"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "share",
                                        path = path,
                                        successful = false,
                                        details = "分享文件失败: ${result.stderr}"
                                ),
                        error = "分享文件失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享文件时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "share",
                                    path = path,
                                    successful = false,
                                    details = "分享文件时出错: ${e.message}"
                            ),
                    error = "分享文件时出错: ${e.message}"
            )
        }
    }

    /** 下载文件 从网络URL下载文件到指定路径 */
    override suspend fun downloadFile(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""

        if (url.isBlank() || destPath.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "必须提供url和destination参数"
                            ),
                    error = "必须提供url和destination参数"
            )
        }

        // 验证URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "URL必须以http://或https://开头"
                            ),
                    error = "URL必须以http://或https://开头"
            )
        }

        return try {
            // 确保目标目录存在
            val directory = File(destPath).parent
            if (directory != null) {
                val mkdirResult = AndroidShellExecutor.executeShellCommand("mkdir -p '$directory'")
                if (!mkdirResult.success) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "download",
                                            path = destPath,
                                            successful = false,
                                            details = "无法创建目标目录: ${mkdirResult.stderr}"
                                    ),
                            error = "无法创建目标目录: ${mkdirResult.stderr}"
                    )
                }
            }

            // 使用wget或curl下载文件
            // 首先检查是否有wget
            val wgetCheckResult = AndroidShellExecutor.executeShellCommand("which wget")

            val downloadCommand =
                    if (wgetCheckResult.success) {
                        "wget '$url' -O '$destPath' --no-check-certificate -q"
                    } else {
                        // 如果没有wget，尝试使用curl
                        val curlCheckResult = AndroidShellExecutor.executeShellCommand("which curl")
                        if (!curlCheckResult.success) {
                            return ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result =
                                            FileOperationData(
                                                    operation = "download",
                                                    path = destPath,
                                                    successful = false,
                                                    details = "系统中没有wget或curl工具，无法下载文件"
                                            ),
                                    error = "系统中没有wget或curl工具，无法下载文件"
                            )
                        }
                        "curl -L '$url' -o '$destPath' -s"
                    }

            val result = AndroidShellExecutor.executeShellCommand(downloadCommand)

            if (result.success) {
                // 验证文件是否已下载
                val checkFileResult =
                        AndroidShellExecutor.executeShellCommand(
                                "test -f '$destPath' && echo 'exists' || echo 'not exists'"
                        )
                if (checkFileResult.stdout.trim() != "exists") {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    FileOperationData(
                                            operation = "download",
                                            path = destPath,
                                            successful = false,
                                            details = "下载似乎已完成，但文件未被创建"
                                    ),
                            error = "下载似乎已完成，但文件未被创建"
                    )
                }

                // 获取文件大小
                val fileSizeResult =
                        AndroidShellExecutor.executeShellCommand("stat -c %s '$destPath'")
                val fileSize =
                        if (fileSizeResult.success) {
                            val size = fileSizeResult.stdout.trim().toLongOrNull() ?: 0
                            if (size > 1024 * 1024) {
                                String.format("%.2f MB", size / (1024.0 * 1024.0))
                            } else if (size > 1024) {
                                String.format("%.2f KB", size / 1024.0)
                            } else {
                                "$size bytes"
                            }
                        } else {
                            "未知大小"
                        }

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FileOperationData(
                                        operation = "download",
                                        path = destPath,
                                        successful = true,
                                        details = "文件下载成功: $url -> $destPath (文件大小: $fileSize)"
                                ),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                FileOperationData(
                                        operation = "download",
                                        path = destPath,
                                        successful = false,
                                        details = "下载失败: ${result.stderr}"
                                ),
                        error = "下载失败: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载文件时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                            FileOperationData(
                                    operation = "download",
                                    path = destPath,
                                    successful = false,
                                    details = "下载文件时出错: ${e.message}"
                            ),
                    error = "下载文件时出错: ${e.message}"
            )
        }
    }
}
