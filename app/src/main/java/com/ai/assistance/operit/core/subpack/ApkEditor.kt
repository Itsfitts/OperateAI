package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.InputStream

/** APK编辑器 - 提供链式调用API 支持APK解压、修改包名、修改应用名、更改图标和重新签名等操作 */
class ApkEditor
private constructor(
        private val context: Context,
        private val apkFile: File,
        private val apkReverseEngineer: ApkReverseEngineer
) {
    companion object {
        private const val TAG = "ApkEditor"

        /**
         * 从资产文件创建APK编辑器
         * @param context 上下文
         * @param assetPath 资产路径
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromAsset(context: Context, assetPath: String): ApkEditor {
            val apkFile = copyAssetToFile(context, assetPath)
            val apkReverseEngineer = ApkReverseEngineer(context)
            return ApkEditor(context, apkFile, apkReverseEngineer)
        }

        /**
         * 从文件创建APK编辑器
         * @param context 上下文
         * @param apkFile APK文件
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromFile(context: Context, apkFile: File): ApkEditor {
            val apkReverseEngineer = ApkReverseEngineer(context)
            return ApkEditor(context, apkFile, apkReverseEngineer)
        }

        /**
         * 从文件路径创建APK编辑器
         * @param context 上下文
         * @param apkFilePath APK文件路径
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromPath(context: Context, apkFilePath: String): ApkEditor {
            val apkFile = File(apkFilePath)
            return fromFile(context, apkFile)
        }

        /**
         * 复制资产文件到缓存目录
         * @param context 上下文
         * @param assetPath 资产路径
         * @return 缓存文件
         */
        private fun copyAssetToFile(context: Context, assetPath: String): File {
            val fileName = assetPath.substringAfterLast('/')
            val outputFile = File(context.cacheDir, "apk_editor_$fileName")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            context.assets.open(assetPath).use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }

            return outputFile
        }
    }

    private var extractedDir: File? = null
    private var newPackageName: String? = null
    private var newAppName: String? = null
    private var newIconBitmap: Bitmap? = null

    private var keyStoreFile: File? = null
    private var keyStorePassword: String? = null
    private var keyAlias: String? = null
    private var keyPassword: String? = null

    private var outputFile: File? = null

    /**
     * 解压APK文件
     * @return 当前APK编辑器实例
     */
    fun extract(): ApkEditor {
        try {
            Log.d(TAG, "开始解压APK: ${apkFile.absolutePath}")
            extractedDir = apkReverseEngineer.extractApk(apkFile)
            return this
        } catch (e: Exception) {
            Log.e(TAG, "解压APK失败", e)
            throw e
        }
    }

    /**
     * 修改包名
     * @param packageName 新包名
     * @return 当前APK编辑器实例
     */
    fun changePackageName(packageName: String): ApkEditor {
        this.newPackageName = packageName
        return this
    }

    /**
     * 修改应用名称
     * @param appName 新应用名称
     * @return 当前APK编辑器实例
     */
    fun changeAppName(appName: String): ApkEditor {
        this.newAppName = appName
        return this
    }

    /**
     * 更改图标（从位图）
     * @param iconBitmap 图标位图
     * @return 当前APK编辑器实例
     */
    fun changeIcon(iconBitmap: Bitmap): ApkEditor {
        this.newIconBitmap = iconBitmap
        return this
    }

    /**
     * 更改图标（从输入流）
     * @param iconInputStream 图标输入流
     * @return 当前APK编辑器实例
     */
    fun changeIcon(iconInputStream: InputStream): ApkEditor {
        val bitmap = BitmapFactory.decodeStream(iconInputStream)
        return changeIcon(bitmap)
    }

    /**
     * 更改图标（从资产文件）
     * @param iconAssetPath 图标资产路径
     * @return 当前APK编辑器实例
     */
    fun changeIconFromAsset(iconAssetPath: String): ApkEditor {
        context.assets.open(iconAssetPath).use { input ->
            return changeIcon(input)
        }
    }

    /**
     * 设置签名信息
     * @param keyStoreFile 密钥库文件
     * @param keyStorePassword 密钥库密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @return 当前APK编辑器实例
     */
    fun withSignature(
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String
    ): ApkEditor {
        this.keyStoreFile = keyStoreFile
        this.keyStorePassword = keyStorePassword
        this.keyAlias = keyAlias
        this.keyPassword = keyPassword
        return this
    }

    /**
     * 设置输出文件
     * @param outputFile 输出文件
     * @return 当前APK编辑器实例
     */
    fun setOutput(outputFile: File): ApkEditor {
        this.outputFile = outputFile
        return this
    }

    /**
     * 设置输出文件路径
     * @param outputPath 输出文件路径
     * @return 当前APK编辑器实例
     */
    fun setOutput(outputPath: String): ApkEditor {
        return setOutput(File(outputPath))
    }

    /**
     * 重新打包APK（不签名）
     * @return 重新打包后的APK文件
     */
    fun repack(): File {
        if (extractedDir == null) {
            extract()
        }

        // 应用修改
        applyChanges()

        // 确定输出文件
        val unsignedOutputFile =
                if (outputFile != null) {
                    outputFile!!
                } else {
                    File(context.cacheDir, "unsigned_${apkFile.name}")
                }

        // 重新打包
        if (!apkReverseEngineer.repackageApk(extractedDir!!, unsignedOutputFile)) {
            throw RuntimeException("APK重新打包失败")
        }

        return unsignedOutputFile
    }

    /**
     * 重新打包并签名APK
     * @return 签名后的APK文件
     */
    fun repackAndSign(): File {
        // 先重新打包
        val unsignedApk = repack()

        Log.d(TAG, "未签名APK生成成功: ${unsignedApk.absolutePath}, 文件大小: ${unsignedApk.length()}")

        // 确保文件确实存在
        if (!unsignedApk.exists() || unsignedApk.length() == 0L) {
            throw RuntimeException("未签名的APK文件不存在或为空: ${unsignedApk.absolutePath}")
        }

        // 检查签名信息
        if (keyStoreFile == null ||
                        keyStorePassword == null ||
                        keyAlias == null ||
                        keyPassword == null
        ) {
            throw IllegalStateException("签名信息不完整，请先调用withSignature方法设置签名信息")
        }

        // 确定签名后的输出文件
        val signedOutputFile =
                if (outputFile != null) {
                    // 如果已经指定了输出文件，创建一个新的临时文件用于签名过程
                    File(
                            unsignedApk.parentFile,
                            "to_sign_${System.currentTimeMillis()}_${unsignedApk.name}"
                    )
                } else {
                    File(context.cacheDir, "signed_${apkFile.name}")
                }

        Log.d(TAG, "开始签名APK，输入: ${unsignedApk.absolutePath}, 输出: ${signedOutputFile.absolutePath}")

        // 签名APK
        if (!apkReverseEngineer.signApk(
                        unsignedApk,
                        keyStoreFile!!,
                        keyStorePassword!!,
                        keyAlias!!,
                        keyPassword!!,
                        signedOutputFile
                )
        ) {
            throw RuntimeException("APK签名失败")
        }

        // 如果指定了输出路径，将签名后的文件移动到指定位置
        val finalOutputFile = if (outputFile != null) {
            // 如果signedOutputFile已经存在，则将其复制到指定的输出位置
            if (signedOutputFile.exists()) {
                // 确保目标目录存在
                outputFile!!.parentFile?.mkdirs()
                
                // 如果目标文件存在，先删除
                if (outputFile!!.exists()) {
                    outputFile!!.delete()
                }
                
                // 复制文件内容
                signedOutputFile.inputStream().use { input ->
                    outputFile!!.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 复制成功后删除临时文件
                signedOutputFile.delete()
                
                Log.d(TAG, "已将签名后的APK从临时文件复制到指定输出位置: ${outputFile!!.absolutePath}")
                outputFile!!
            } else {
                Log.e(TAG, "签名后的临时文件不存在: ${signedOutputFile.absolutePath}")
                signedOutputFile
            }
        } else {
            signedOutputFile
        }

        // 清理临时文件
        // if (unsignedApk.exists()) {
        //     unsignedApk.delete()
        // }

        Log.d(TAG, "APK签名完成: ${finalOutputFile.absolutePath}, 文件大小: ${finalOutputFile.length()}字节")
        return finalOutputFile
    }

    /** 应用所有修改 */
    private fun applyChanges() {
        if (extractedDir == null) {
            throw IllegalStateException("请先调用extract方法解压APK")
        }

        // 修改包名
        if (newPackageName != null) {
            Log.d(TAG, "修改包名为: $newPackageName")
            if (!apkReverseEngineer.modifyPackageName(extractedDir!!, newPackageName!!)) {
                throw RuntimeException("修改包名失败")
            }
        }

        // 修改应用名称
        if (newAppName != null) {
            Log.d(TAG, "修改应用名称为: $newAppName")
            if (!apkReverseEngineer.modifyAppName(extractedDir!!, newAppName!!)) {
                throw RuntimeException("修改应用名称失败")
            }
        }

        // 更改应用图标
        if (newIconBitmap != null) {
            Log.d(TAG, "更换应用图标")
            if (!apkReverseEngineer.changeAppIcon(extractedDir!!, newIconBitmap!!)) {
                throw RuntimeException("更换应用图标失败")
            }
        }
    }

    /** 清理临时文件 */
    fun cleanup() {
        apkReverseEngineer.cleanup()
        newIconBitmap?.recycle()
        newIconBitmap = null
    }
}
