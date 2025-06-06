package com.ai.assistance.operit.voice

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment
import com.ai.assistance.operit.util.TextSegmenter

/**
 * TextSummarizer - 基于TensorFlow Lite的文本摘要提取器
 * 用于将聊天消息转换为更适合TTS朗读的格式
 */
class TextSummarizer(private val context: Context) {
    companion object {
        private const val TAG = "TextSummarizer"
    }

    // TensorFlow Lite解释器
    private var interpreter: Interpreter? = null

    // 词汇表和标记器
    private val tokenizer: SimpleTokenizer

    // 初始化
    init {
        loadMode()

        // 初始化分词器
        tokenizer = SimpleTokenizer()
    }

    private fun loadMode() {
        val modelFile = "universal_sentence_encoder_lite.tflite"
        try {
            // 尝试从assets加载模型
            val fileDescriptor = context.assets.openFd(modelFile)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // 创建解释器并配置
            val interpreterOptions = Interpreter.Options().apply {
                numThreads = 2 // 根据设备调整
                useNNAPI = true // 启用Neural Network API加速
            }
            interpreter = Interpreter(mappedByteBuffer, interpreterOptions)

            Log.d(TAG, "TF Lite模型加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "TF Lite模型加载失败: ${e.message}")
            interpreter = null
        }
    }

    /**
     * 处理聊天消息，提取适合TTS的内容
     * @param originalContent 原始包含XML标签的内容
     * @return 处理后适合TTS朗读的内容
     */
    fun process(originalContent: String): String {
        // 1. 解析XML标签
        val contentSegments = MessageContentParser.parseContent(originalContent, true)

        // 特殊处理：提前检查倒数第二个元素是否为Text类型
        val secondLastIndex = contentSegments.size - 2
        val hasImportantSummary = secondLastIndex >= 0 &&
            contentSegments[secondLastIndex] is ContentSegment.Text

        if (!hasImportantSummary) {
            Log.e(TAG, "无法找到有效的摘要")
            return ""
        }

        val content = (contentSegments[secondLastIndex] as ContentSegment.Text).content

        // 清理Markdown标记并处理emoji
        val cleanedText = cleanupMarkdownForTTS(content)

        // TODO
        val summarizeText =
            summarizeText(cleanedText)
        return cleanedText
    }

    /**
     * 清理Markdown格式并优化TTS朗读体验
     * 将Markdown格式的文本转换为适合TTS朗读的纯文本，保留内容但移除或转换格式标记
     */
    private fun cleanupMarkdownForTTS(text: String): String {
        if (text.isBlank()) return ""

        // 一步步处理各种格式
        var result = text

        // 1. 处理代码块 - 在朗读前后添加提示
        result = result.replace(Regex("```(?:[a-zA-Z]+)?\n(.*?)\n```", RegexOption.DOT_MATCHES_ALL), "以下是代码块：$1 代码块结束。")
        
        // 2. 处理行内代码
        result = result.replace(Regex("`([^`]+)`"), "代码 $1")

        // 3. 处理标题 (h1-h6) - 添加"标题："前缀以便于听众理解层级
        result = result.replace(Regex("^\\s*#{6}\\s+(.+)$", RegexOption.MULTILINE), "小标题：$1")
        result = result.replace(Regex("^\\s*#{5}\\s+(.+)$", RegexOption.MULTILINE), "小标题：$1")
        result = result.replace(Regex("^\\s*#{4}\\s+(.+)$", RegexOption.MULTILINE), "小标题：$1")
        result = result.replace(Regex("^\\s*#{3}\\s+(.+)$", RegexOption.MULTILINE), "子标题：$1")
        result = result.replace(Regex("^\\s*#{2}\\s+(.+)$", RegexOption.MULTILINE), "次标题：$1")
        result = result.replace(Regex("^\\s*#\\s+(.+)$", RegexOption.MULTILINE), "主标题：$1")

        // 4. 处理引用块
        result = result.replace(Regex("^\\s*>\\s*(.+)$", RegexOption.MULTILINE), "引用：$1")
        
        // 5. 处理加粗、斜体和组合格式
        result = result.replace(Regex("\\*\\*\\*([^*]+)\\*\\*\\*"), "$1") // 粗斜体
        result = result.replace(Regex("___([^_]+)___"), "$1") // 粗斜体(下划线版)
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // 加粗
        result = result.replace(Regex("__([^_]+)__"), "$1") // 加粗(下划线版)
        result = result.replace(Regex("\\*([^*]+)\\*"), "$1") // 斜体
        result = result.replace(Regex("_([^_]+)_"), "$1") // 斜体(下划线版)
        result = result.replace(Regex("~~([^~]+)~~"), "$1") // 删除线

        // 6. 处理链接 - 提取链接文本，省略URL
        result = result.replace(Regex("!\\[(.*?)\\]\\(.*?\\)"), "图片：$1") // 图片
        result = result.replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // 链接
        result = result.replace(Regex("<(https?://[^>]+)>"), "链接") // 直接URL

        // 7. 处理列表
        // 统一处理有序列表和无序列表
        result = result.replace(Regex("^\\s*[*+-]\\s+(.+)$", RegexOption.MULTILINE), "• $1")
        result = result.replace(Regex("^\\s*(\\d+)\\.\\s+(.+)$", RegexOption.MULTILINE), "$1. $2")

        // 8. 处理任务列表
        result = result.replace(Regex("^\\s*- \\[ \\]\\s+(.+)$", RegexOption.MULTILINE), "待办事项：$1")
        result = result.replace(Regex("^\\s*- \\[x\\]\\s+(.+)$", RegexOption.MULTILINE), "已完成事项：$1")

        // 9. 处理表格 - 简化表格，仅保留内容
        // 移除表格分隔行
        result = result.replace(Regex("^\\|\\s*[-:]+[-\\s|:]*[-:]+\\s*\\|$", RegexOption.MULTILINE), "")
        // 处理表格内容行，将竖线替换为逗号或句号
        result = result.replace(Regex("^\\|(.+)\\|$", RegexOption.MULTILINE)) { matchResult ->
            val cells = matchResult.groupValues[1].split("|")
            cells.joinToString("，") { it.trim() } + "。"
        }

        // 10. 处理水平线
        result = result.replace(Regex("^\\s*[-*_]{3,}\\s*$", RegexOption.MULTILINE), "分隔线。")

        // 11. 处理常见 emoji - 替换为文字描述
        val emojiMap = mapOf(
            "☀️" to "晴天",
            "⛅" to "多云",
            "🌧️" to "雨天",
            "❄️" to "雪花",
            "🌡️" to "温度计",
            "💨" to "风",
            "💧" to "水滴",
            "⚠️" to "警告",
            "🌪️" to "龙卷风",
            "😀" to "笑脸",
            "😃" to "开心",
            "😄" to "大笑",
            "😁" to "露齿笑",
            "😆" to "眯眼笑",
            "😅" to "苦笑",
            "🤣" to "笑倒",
            "😂" to "笑哭",
            "🙂" to "微笑",
            "🙃" to "倒脸笑",
            "😉" to "眨眼",
            "😊" to "含笑",
            "😇" to "天使笑",
            "👍" to "赞",
            "👎" to "踩",
            "❤️" to "爱心",
            "💔" to "心碎",
            "✅" to "勾选",
            "❌" to "错误",
            "🔴" to "红色",
            "🟠" to "橙色",
            "🟡" to "黄色",
            "🟢" to "绿色",
            "🔵" to "蓝色",
            "🟣" to "紫色",
            "⚫" to "黑色",
            "⚪" to "白色"
        )

        // 替换emoji
        for ((emoji, description) in emojiMap) {
            result = result.replace(emoji, description)
        }

        // 12. 处理数学公式
        result = result.replace(Regex("\\$\\$(.*?)\\$\\$", RegexOption.DOT_MATCHES_ALL), "数学公式。")
        result = result.replace(Regex("\\$(.*?)\\$"), "数学公式。")

        // 13. 处理HTML标签
        result = result.replace(Regex("<([a-zA-Z][a-zA-Z0-9]*)\\s*[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("<[^>]+>"), "")

        // 14. 处理转义字符
        result = result.replace(Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!])"), "$1")

        // 15. 处理XML和特殊格式
        result = result.replace(Regex("&[a-zA-Z]+;|&#[0-9]+;"), " ")

        // 16. 去除多余空行和空格
        result = result.replace(Regex("\n{3,}"), "\n\n") // 多个空行替换为最多两个
        result = result.replace(Regex(" {2,}"), " ") // 多个空格替换为一个

        // 17. 处理中英文之间的空格
        result = result.replace(Regex("([a-zA-Z])([\\u4e00-\\u9fa5])"), "$1 $2") // 英文后面是中文
        result = result.replace(Regex("([\\u4e00-\\u9fa5])([a-zA-Z])"), "$1 $2") // 中文后面是英文
        
        // 18. 优化标点符号朗读体验
        result = result.replace(Regex("([,.;:!?，。；：！？])([a-zA-Z\\u4e00-\\u9fa5])"), "$1 $2") // 标点后添加空格

        return result.trim()
    }


    /**
     * 使用TensorFlow Lite模型对文本进行摘要
     */
    private fun summarizeText(text: String): String {
        try {
            if (interpreter == null) return ""

            // 准备输入数据
            val inputText = if (text.length > 512) text.substring(0, 512) else text

            // 对文本进行分词和编码
            val tokens = tokenizeText(inputText)

            // 创建输入张量
            val inputTensor = Array(1) { IntArray(tokens.size) }
            for (i in tokens.indices) {
                inputTensor[0][i] = tokens[i]
            }

            // 创建输出张量（根据您的模型调整维度）
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val outputTensor = Array(1) { FloatArray(outputShape[1]) }

            // 运行推理
            interpreter!!.run(inputTensor, outputTensor)

            // 处理输出结果
            // 注意：这里的实现取决于您的模型具体输出类型
            // 这里假设模型输出是句子重要性的评分

            // 示例：使用输出向量对原始句子重新排序
            val sentences = splitIntoSentences(text)

            // 如果句子数量少于输出维度，进行截断
            val validSize = minOf(sentences.size, outputTensor[0].size)

            if (validSize == 0) return ""

            // 创建句子-得分对
            val scoredSentences = ArrayList<Pair<String, Float>>(validSize)
            for (i in 0 until validSize) {
                scoredSentences.add(Pair(sentences[i], outputTensor[0][i]))
            }

            // 选择得分最高的句子
            val topSentences = scoredSentences
                .sortedByDescending { it.second }
                .take(2)
                .map { it.first }

            return topSentences.joinToString(" ").let {
                if (it.length > 250) it.take(250) + "..." else it
            }
        } catch (e: Exception) {
            Log.e(TAG, "摘要处理失败: ${e.message}", e)
            // 异常处理：返回文本的前200个字符作为安全回退
            return text.take(200) + "..."
        }
    }

    /**
     * 将文本分割成句子
     */
    private fun splitIntoSentences(text: String): List<String> {
        // 使用更复杂的句子分割逻辑，处理中英文混合情况
        val sentencePattern = Regex("(?<=[.。!！?？]\\s)|(?<=[.。!！?？])")

        return sentencePattern.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length >= 10 } // 过滤掉太短的句子
    }


    /**
     * 对文本进行分词处理
     */
    private fun tokenizeText(text: String): IntArray {
        // 使用TextSegmenter进行分词
        val segments = TextSegmenter.segment(text)

        // 将分词结果转换为整数ID
        // 注意：实际实现需要根据您的模型词汇表进行调整
        return segments.map { it.hashCode() and 0x7FFFFFFF }.toIntArray()
    }

    private inner class SimpleTokenizer {
        // 简单实现，实际使用时可以替换为更复杂的分词器
        fun tokenize(text: String): IntArray {
            // 使用TextSegmenter进行分词
            val segments = TextSegmenter.segment(text)
            return segments.map { it.hashCode() and 0xFFFF }.toIntArray()
        }
    }

}