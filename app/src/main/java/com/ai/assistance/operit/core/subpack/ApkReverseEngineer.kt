package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import net.dongliu.apk.parser.ApkFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import org.w3c.dom.Element
import pxb.android.axml.Axml
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter

/** APK逆向工程工具类 使用Android标准库和专业库实现APK的解压、修改和重新打包 */
class ApkReverseEngineer(private val context: Context) {
    companion object {
        private const val TAG = "ApkReverseEngineer"
        private const val TEMP_DIR = "apk_reverse_temp"
        private const val ANDROID_MANIFEST = "AndroidManifest.xml"
    }

    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * 获取APK基本信息
     * @param apkFile APK文件
     * @return 包名和版本信息的Map
     */
    fun getApkInfo(apkFile: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            // 使用apk-parser库解析APK文件
            ApkFile(apkFile).use { apkParser ->
                val apkMeta = apkParser.apkMeta
                result["package"] = apkMeta.packageName
                result["versionName"] = apkMeta.versionName
                result["versionCode"] = apkMeta.versionCode.toString()
                result["appName"] = apkMeta.name
                result["minSdkVersion"] = apkMeta.minSdkVersion.toString()
                result["targetSdkVersion"] = apkMeta.targetSdkVersion.toString()

                // 获取权限列表
                val permissions = apkMeta.usesPermissions.joinToString(", ")
                if (permissions.isNotEmpty()) {
                    result["permissions"] = permissions
                }

                // 获取功能列表
                val features = apkMeta.usesFeatures.map { it.name ?: "(未命名功能)" }.joinToString(", ")
                if (features.isNotEmpty()) {
                    result["features"] = features
                }
            }

            Log.d(TAG, "成功解析APK信息: 包名=${result["package"]}, 版本=${result["versionName"]}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "读取APK信息失败", e)
            return mapOf("error" to (e.message ?: "未知错误"))
        }
    }

    /**
     * 解压APK文件到临时目录
     * @param apkFile APK文件
     * @return 解压后的目录
     */
    fun extractApk(apkFile: File): File {
        val extractDir = File(tempDir, apkFile.nameWithoutExtension)
        if (extractDir.exists()) extractDir.deleteRecursively()
        extractDir.mkdirs()

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryDestination = File(extractDir, entry.name)

                    if (entry.isDirectory) {
                        entryDestination.mkdirs()
                    } else {
                        entryDestination.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(entryDestination).use { output ->
                                IOUtils.copy(input, output)
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "APK解压成功: ${extractDir.absolutePath}")
            return extractDir
        } catch (e: Exception) {
            Log.e(TAG, "APK解压失败", e)
            throw RuntimeException("APK解压失败: ${e.message}")
        }
    }

    /**
     * 修改APK包名 - 通过修改AndroidManifest.xml文件
     * @param extractedDir 解压后的APK目录
     * @param newPackageName 新包名
     * @return 是否修改成功
     */
    fun modifyPackageName(extractedDir: File, newPackageName: String): Boolean {
        val manifestFile = File(extractedDir, ANDROID_MANIFEST)
        if (!manifestFile.exists()) {
            Log.e(TAG, "未找到AndroidManifest.xml文件")
            return false
        }

        try {
            // 读取二进制AndroidManifest.xml文件
            val manifestBytes = FileInputStream(manifestFile).use { it.readBytes() }

            // 通过AxmlReader读取AXML文件
            val reader = AxmlReader(manifestBytes)

            // 创建Axml数据结构
            val axml = Axml()
            reader.accept(axml)

            // 查找manifest元素并修改package属性
            var packageFound = false
            for (node in axml.firsts) {
                if (node.name == "manifest") {
                    // 遍历属性找到package
                    for (attr in node.attrs) {
                        if (attr.name == "package") {
                            val oldPackage = attr.value as String
                            Log.d(TAG, "找到原始包名: $oldPackage, 将替换为: $newPackageName")
                            // 修改包名
                            attr.value = newPackageName
                            packageFound = true
                            break
                        }
                    }

                    // 如果没找到package属性，添加一个
                    if (!packageFound) {
                        val attr = Axml.Node.Attr()
                        attr.name = "package"
                        attr.ns = null
                        attr.resourceId = -1
                        attr.type = AxmlVisitor.TYPE_STRING
                        attr.value = newPackageName
                        node.attrs.add(attr)
                        packageFound = true
                    }
                    break
                }
            }

            if (!packageFound) {
                Log.e(TAG, "未在AndroidManifest.xml中找到manifest元素或package属性")
                return false
            }

            // 创建AXML写入器生成修改后的二进制文件
            val writer = AxmlWriter()
            axml.accept(writer)
            val modifiedBytes = writer.toByteArray()

            // 备份原始文件
            val backupFile = File(extractedDir, "${ANDROID_MANIFEST}.bak")
            if (backupFile.exists()) backupFile.delete()
            manifestFile.renameTo(backupFile)

            // 写入修改后的文件
            FileOutputStream(manifestFile).use { it.write(modifiedBytes) }

            Log.d(TAG, "成功修改AndroidManifest.xml中的包名")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "修改包名时发生异常", e)
            return false
        }
    }

    /**
     * 修改应用名称 - 通过修改AndroidManifest.xml文件或strings.xml
     * @param extractedDir 解压后的APK目录
     * @param newAppName 新应用名称
     * @return 是否修改成功
     */
    fun modifyAppName(extractedDir: File, newAppName: String): Boolean {
        try {
            val manifestFile = File(extractedDir, ANDROID_MANIFEST)
            if (manifestFile.exists()) {
                // 使用二进制方式直接修改AndroidManifest.xml
                try {
                    // 读取二进制AndroidManifest.xml文件
                    val manifestBytes = FileInputStream(manifestFile).use { it.readBytes() }

                    // 通过AxmlReader读取AXML文件
                    val reader = AxmlReader(manifestBytes)

                    // 创建Axml数据结构
                    val axml = Axml()
                    reader.accept(axml)

                    // 查找application元素并修改label属性
                    var labelModified = false
                    for (node in axml.firsts) {
                        if (node.name == "manifest") {
                            // 查找application节点
                            for (childNode in node.children) {
                                if (childNode.name == "application") {
                                    // 查找label属性
                                    var labelAttr: Axml.Node.Attr? = null
                                    for (attr in childNode.attrs) {
                                        if (attr.name == "label" &&
                                                        (attr.ns == null ||
                                                                attr.ns ==
                                                                        "http://schemas.android.com/apk/res/android")
                                        ) {
                                            // 修改标签值
                                            attr.value = newAppName
                                            labelModified = true
                                            break
                                        }
                                    }

                                    // 如果没有找到label属性，添加一个
                                    if (!labelModified) {
                                        val attr = Axml.Node.Attr()
                                        attr.name = "label"
                                        attr.ns = "http://schemas.android.com/apk/res/android"
                                        attr.resourceId = -1
                                        attr.type = AxmlVisitor.TYPE_STRING
                                        attr.value = newAppName
                                        childNode.attrs.add(attr)
                                        labelModified = true
                                    }
                                    break
                                }
                            }
                            break
                        }
                    }

                    if (labelModified) {
                        // 创建AXML写入器生成修改后的二进制文件
                        val writer = AxmlWriter()
                        axml.accept(writer)
                        val modifiedBytes = writer.toByteArray()

                        // 备份原始文件
                        val backupFile = File(extractedDir, "${ANDROID_MANIFEST}.bak")
                        if (backupFile.exists()) backupFile.delete()
                        manifestFile.renameTo(backupFile)

                        // 写入修改后的文件
                        FileOutputStream(manifestFile).use { it.write(modifiedBytes) }

                        Log.d(TAG, "已在AndroidManifest.xml中更新应用名称为: $newAppName")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "修改AndroidManifest.xml中的应用名称失败: ${e.message}", e)
                    // 尝试备用方法 - 修改strings.xml
                }
            }

            // 如果无法修改清单文件或清单文件不存在，尝试修改strings.xml
            val success = modifyAppNameInStrings(extractedDir, newAppName)
            if (success) {
                return true
            }

            Log.e(TAG, "无法修改应用名称，既找不到AndroidManifest.xml中的label属性，也找不到strings.xml中的app_name")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "修改应用名称失败", e)
            return false
        }
    }

    /** 修改应用名称 - 通过修改strings.xml资源文件 */
    private fun modifyAppNameInStrings(extractedDir: File, newAppName: String): Boolean {
        try {
            // 查找values目录中的strings.xml
            val resDir = File(extractedDir, "res")
            val valuesDirs =
                    resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values") }
                            ?: return false

            var success = false

            for (valuesDir in valuesDirs) {
                val stringsFile = File(valuesDir, "strings.xml")
                if (stringsFile.exists()) {
                    // 解析strings.xml
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val document = builder.parse(stringsFile)

                    // 查找app_name字符串
                    val strings = document.getElementsByTagName("string")
                    var appNameFound = false

                    for (i in 0 until strings.length) {
                        val element = strings.item(i) as Element
                        if (element.getAttribute("name") == "app_name") {
                            element.textContent = newAppName
                            appNameFound = true
                            break
                        }
                    }

                    // 如果未找到app_name，创建一个
                    if (!appNameFound) {
                        val stringElement = document.createElement("string")
                        stringElement.setAttribute("name", "app_name")
                        stringElement.textContent = newAppName
                        document.documentElement.appendChild(stringElement)
                    }

                    // 保存修改
                    val transformerFactory = TransformerFactory.newInstance()
                    val transformer = transformerFactory.newTransformer()
                    val source = DOMSource(document)
                    val result = StreamResult(stringsFile)
                    transformer.transform(source, result)

                    success = true
                    Log.d(TAG, "已在 ${stringsFile.path} 中更新应用名称为: $newAppName")
                }
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "修改strings.xml中的应用名称失败", e)
            return false
        }
    }

    /**
     * 更换应用图标
     * @param extractedDir 解压后的APK目录
     * @param newIconBitmap 新图标位图
     * @return 是否修改成功
     */
    fun changeAppIcon(extractedDir: File, newIconBitmap: Bitmap): Boolean {
        try {
            val resDir = File(extractedDir, "res")
            val iconDirs =
                    resDir.listFiles { file ->
                        file.isDirectory &&
                                (file.name.startsWith("drawable") || file.name.startsWith("mipmap"))
                    }
                            ?: return false

            var success = false

            // 遍历所有drawable和mipmap目录
            for (dir in iconDirs) {
                // 查找启动器图标文件
                val iconFiles =
                        dir.listFiles { file ->
                            file.name.contains("ic_launcher") &&
                                    (file.extension == "png" || file.extension == "webp")
                        }
                                ?: continue

                // 替换每个找到的图标文件
                for (iconFile in iconFiles) {
                    // 根据目录名调整图标大小
                    val scaledIcon =
                            when {
                                dir.name.contains("xxxhdpi") -> scaleBitmap(newIconBitmap, 192)
                                dir.name.contains("xxhdpi") -> scaleBitmap(newIconBitmap, 144)
                                dir.name.contains("xhdpi") -> scaleBitmap(newIconBitmap, 96)
                                dir.name.contains("hdpi") -> scaleBitmap(newIconBitmap, 72)
                                dir.name.contains("mdpi") -> scaleBitmap(newIconBitmap, 48)
                                else -> newIconBitmap
                            }

                    // 保存到文件
                    FileOutputStream(iconFile).use { output ->
                        val format =
                                if (iconFile.extension == "webp") Bitmap.CompressFormat.WEBP
                                else Bitmap.CompressFormat.PNG
                        scaledIcon.compress(format, 100, output)
                    }
                    success = true
                    Log.d(TAG, "已更新图标: ${iconFile.path}")
                }
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "替换应用图标失败", e)
            return false
        }
    }

    /** 按指定尺寸缩放位图 */
    private fun scaleBitmap(source: Bitmap, size: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, size, size, true)
    }

    /**
     * 重新打包APK文件
     * @param extractedDir 解压后的APK目录
     * @param outputApk 输出的APK文件
     * @return 是否打包成功
     */
    fun repackageApk(extractedDir: File, outputApk: File): Boolean {
        try {
            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            ZipArchiveOutputStream(FileOutputStream(outputApk)).use { zipOut ->
                addDirToZip(extractedDir, extractedDir, zipOut)
            }

            Log.d(TAG, "APK重新打包成功: ${outputApk.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "APK重新打包失败", e)
            return false
        }
    }

    /**
     * 重新签名APK
     * @param unsignedApk 未签名的APK文件
     * @param keyStoreFile 密钥库文件
     * @param keyStorePassword 密钥库密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @param outputApk 签名后的APK文件
     * @return 是否签名成功
     */
    fun signApk(
            unsignedApk: File,
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Boolean {
        try {
            if (!unsignedApk.exists()) {
                Log.e(TAG, "未签名的APK文件不存在: ${unsignedApk.absolutePath}")
                return false
            }

            if (!keyStoreFile.exists()) {
                Log.e(TAG, "密钥库文件不存在: ${keyStoreFile.absolutePath}")
                return false
            }

            Log.d(TAG, "开始签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: $keyAlias")
            Log.d(TAG, "密钥文件大小: ${keyStoreFile.length()}字节")

            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            // 首先尝试使用PKCS12格式加载密钥库
            try {
                val pkcs12KeyStore = KeyStore.getInstance("PKCS12")
                FileInputStream(keyStoreFile).use { input ->
                    pkcs12KeyStore.load(input, keyStorePassword.toCharArray())
                    Log.d(TAG, "成功以PKCS12格式加载密钥库")
                    
                    // 获取可用的别名
                    val aliases = pkcs12KeyStore.aliases()
                    val aliasList = mutableListOf<String>()
                    while (aliases.hasMoreElements()) {
                        aliasList.add(aliases.nextElement())
                    }

                    if (aliasList.isEmpty()) {
                        Log.e(TAG, "密钥库中没有任何密钥别名")
                        return false
                    } else {
                        Log.d(TAG, "密钥库中的别名: ${aliasList.joinToString()}")

                        // 如果指定的别名不存在，但有其他别名，使用第一个别名
                        if (!aliasList.contains(keyAlias) && aliasList.isNotEmpty()) {
                            Log.w(TAG, "指定的别名'$keyAlias'不存在，将使用可用的别名: ${aliasList[0]}")
                            val actualKeyAlias = aliasList[0]
                            return signWithKeyStore(pkcs12KeyStore, unsignedApk, actualKeyAlias, keyPassword, outputApk)
                        }
                    }
                    
                    return signWithKeyStore(pkcs12KeyStore, unsignedApk, keyAlias, keyPassword, outputApk)
                }
            } catch (e: Exception) {
                Log.e(TAG, "以PKCS12格式加载密钥库失败: ${e.message}", e)
                
                // 如果PKCS12失败，尝试JKS格式
                try {
                    val jksKeyStore = KeyStore.getInstance("JKS")
                    FileInputStream(keyStoreFile).use { input ->
                        jksKeyStore.load(input, keyStorePassword.toCharArray())
                        Log.d(TAG, "成功以JKS格式加载密钥库")
                        
                        // 获取可用的别名
                        val aliases = jksKeyStore.aliases()
                        val aliasList = mutableListOf<String>()
                        while (aliases.hasMoreElements()) {
                            aliasList.add(aliases.nextElement())
                        }

                        if (aliasList.isEmpty()) {
                            Log.e(TAG, "密钥库中没有任何密钥别名")
                            return false
                        } else {
                            Log.d(TAG, "密钥库中的别名: ${aliasList.joinToString()}")

                            // 如果指定的别名不存在，但有其他别名，使用第一个别名
                            if (!aliasList.contains(keyAlias) && aliasList.isNotEmpty()) {
                                Log.w(TAG, "指定的别名'$keyAlias'不存在，将使用可用的别名: ${aliasList[0]}")
                                val actualKeyAlias = aliasList[0]
                                return signWithKeyStore(jksKeyStore, unsignedApk, actualKeyAlias, keyPassword, outputApk)
                            }
                        }
                        
                        return signWithKeyStore(jksKeyStore, unsignedApk, keyAlias, keyPassword, outputApk)
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "以JKS格式加载密钥库也失败: ${e2.message}", e2)
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK签名失败: ${e.message}", e)
            return false
        }
    }

    /** 使用已加载的KeyStore进行签名 */
    private fun signWithKeyStore(
            keyStore: KeyStore,
            unsignedApk: File,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Boolean {
        try {
            // 获取私钥
            val key = keyStore.getKey(keyAlias, keyPassword.toCharArray())
            if (key == null) {
                Log.e(TAG, "在密钥库中找不到别名为'$keyAlias'的密钥")
                return false
            }

            if (key !is PrivateKey) {
                Log.e(TAG, "找到的密钥不是私钥类型: ${key.javaClass.name}")
                return false
            }
            val privateKey = key

            // 获取证书链
            val certificateChain = keyStore.getCertificateChain(keyAlias)
            if (certificateChain == null || certificateChain.isEmpty()) {
                Log.e(TAG, "无法获取别名为'$keyAlias'的证书链")
                return false
            }

            val x509CertificateChain =
                    certificateChain.map { cert ->
                        if (cert !is X509Certificate) {
                            Log.e(TAG, "证书不是X509Certificate类型: ${cert.javaClass.name}")
                            return false
                        }
                        cert as X509Certificate
                    }

            // 使用ApkSigner进行签名
            val signer =
                    ApkSigner.SignerConfig.Builder(keyAlias, privateKey, x509CertificateChain)
                            .build()
            val signerConfigs = listOf(signer)

            val apkSigner =
                    ApkSigner.Builder(signerConfigs)
                            .setInputApk(unsignedApk)
                            .setOutputApk(outputApk)
                            .setMinSdkVersion(26) // 根据项目实际最低SDK版本调整
                            .build()

            apkSigner.sign()

            Log.d(TAG, "APK签名完成: ${outputApk.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "使用KeyStore签名APK失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 从assets中加载内置的签名密钥库
     * @return 密钥库文件
     */
    private fun loadBuiltInKeystore(): File? {
        try {
            // 密钥直接存放在assets根目录
            val assetKeystore = "app_signing.keystore"
            val keystoreFile = File(context.filesDir, "app_signing.keystore")

            // 如果文件已存在且大小合理，直接返回
            if (keystoreFile.exists() && keystoreFile.length() > 1000) {
                Log.d(
                        TAG,
                        "使用已存在的密钥库: ${keystoreFile.absolutePath}, 大小: ${keystoreFile.length()}字节"
                )
                return keystoreFile
            }

            Log.d(TAG, "尝试从assets加载密钥库: $assetKeystore")

            // 列出assets根目录所有文件用于调试
            try {
                val assetFiles = context.assets.list("") ?: emptyArray()
                Log.d(TAG, "assets目录文件列表: ${assetFiles.joinToString()}")
            } catch (e: Exception) {
                Log.e(TAG, "列出assets文件失败: ${e.message}", e)
            }

            // 先删除可能存在的旧文件
            if (keystoreFile.exists()) {
                keystoreFile.delete()
                Log.d(TAG, "删除旧密钥库文件")
            }

            // 从assets复制密钥库文件 - 使用缓冲区读取全部内容再写入
            try {
                context.assets.open(assetKeystore).use { input ->
                    val bytes = input.readBytes()
                    Log.d(TAG, "从assets读取到字节: ${bytes.size}")

                    if (bytes.size < 1000) {
                        Log.e(TAG, "密钥库文件大小异常，可能已损坏: ${bytes.size}字节")
                        return null
                    }

                    keystoreFile.outputStream().use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                    Log.d(TAG, "成功写入密钥库: ${keystoreFile.absolutePath}, ${bytes.size}字节")
                }
            } catch (e: Exception) {
                Log.e(TAG, "复制密钥库文件失败: ${e.message}", e)
                return null
            }

            if (keystoreFile.exists() && keystoreFile.length() > 1000) {
                Log.d(
                        TAG,
                        "已从assets加载内置密钥库: ${keystoreFile.absolutePath}, 大小: ${keystoreFile.length()}字节"
                )
                return keystoreFile
            } else {
                Log.e(TAG, "密钥库文件创建失败或大小异常: ${keystoreFile.length()}字节")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载内置密钥库失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 使用调试密钥签名APK（开发测试用）
     * @param unsignedApk 未签名的APK文件
     * @param outputApk 签名后的APK文件
     * @return 是否签名成功
     */
    fun signApkWithDebugKey(unsignedApk: File, outputApk: File): Boolean {
        try {
            // 首先尝试加载内置的密钥库
            val builtInKeystore = loadBuiltInKeystore()
            if (builtInKeystore != null) {
                Log.d(TAG, "找到内置密钥库，使用密钥签名")
                // 使用固定的密钥别名和密码
                return signApk(
                        unsignedApk,
                        builtInKeystore,
                        "android", // 密钥库密码
                        "androidkey", // 密钥别名
                        "android", // 密钥密码
                        outputApk
                )
            }

            // 作为备用，尝试使用Android默认调试密钥库
            val userHome = System.getProperty("user.home")
            val debugKeystore = File(userHome, ".android/debug.keystore")

            if (!debugKeystore.exists()) {
                Log.e(TAG, "未找到调试密钥库: ${debugKeystore.absolutePath}")
                // 尝试查找其他可能的位置
                val altLocations =
                        arrayOf(
                                File(context.filesDir.parent ?: "", "debug.keystore"),
                                File(context.cacheDir, "debug.keystore"),
                                File("/data/local/debug.keystore")
                        )

                val existingKeystore = altLocations.firstOrNull { it.exists() }
                if (existingKeystore != null) {
                    Log.d(TAG, "在替代位置找到调试密钥库: ${existingKeystore.absolutePath}")
                    return signApk(
                            unsignedApk,
                            existingKeystore,
                            "android", // 默认密码
                            "androiddebugkey", // 默认别名
                            "android", // 默认密钥密码
                            outputApk
                    )
                }

                Log.e(TAG, "找不到任何可用的密钥库，无法签名APK")
                return false
            }

            // 使用默认调试密钥签名
            return signApk(
                    unsignedApk,
                    debugKeystore,
                    "android", // 默认密码
                    "androiddebugkey", // 默认别名
                    "android", // 默认密钥密码
                    outputApk
            )
        } catch (e: Exception) {
            Log.e(TAG, "使用调试密钥签名APK失败", e)
            return false
        }
    }

    /** 递归添加目录到ZIP文件 */
    private fun addDirToZip(rootDir: File, currentDir: File, zipOut: ZipArchiveOutputStream) {
        currentDir.listFiles()?.forEach { file ->
            val relativePath =
                    file.absolutePath.substring(rootDir.absolutePath.length + 1).replace("\\", "/")

            if (file.isDirectory) {
                if (file.listFiles()?.isNotEmpty() == true) {
                    addDirToZip(rootDir, file, zipOut)
                } else {
                    val entry = ZipArchiveEntry("$relativePath/")
                    zipOut.putArchiveEntry(entry)
                    zipOut.closeArchiveEntry()
                }
            } else {
                val entry = ZipArchiveEntry(relativePath)
                zipOut.putArchiveEntry(entry)
                FileInputStream(file).use { input -> IOUtils.copy(input, zipOut) }
                zipOut.closeArchiveEntry()
            }
        }
    }

    /** 清理临时文件 */
    fun cleanup() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
            Log.d(TAG, "临时文件清理完成")
        }
    }
}
