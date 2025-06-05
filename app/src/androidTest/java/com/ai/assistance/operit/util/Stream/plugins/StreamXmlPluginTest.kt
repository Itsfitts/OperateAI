package com.ai.assistance.operit.util.Stream.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** StreamXmlPlugin的Android测试类 */
@RunWith(AndroidJUnit4::class)
class StreamXmlPluginTest {

    private lateinit var plugin: StreamXmlPlugin

    @Before
    fun setup() {
        plugin = StreamXmlPlugin()
        plugin.initPlugin()
    }

    @Test
    fun testInitialState() {
        // 初始状态应该是未处理和未尝试开始
        assertFalse(plugin.isProcessing)
        assertFalse(plugin.isTryingToStart)
    }

    @Test
    fun testStartTagDetection() {
        // 检测"<"字符是否正确触发尝试开始状态
        plugin.processChar('<')
        assertTrue(plugin.isTryingToStart)
        assertFalse(plugin.isProcessing)
    }

    @Test
    fun testCompleteTagRecognition() {
        // StreamXmlPluginTest.kt
        val xmlTag = "<test>"
        // Loop processes characters up to, but not including, xmlTag.last()
        // For "<test>", loop processes "<tes"
        for (i in 0 until xmlTag.length - 1) {
            plugin.processChar(xmlTag[i])
        }
        // Test expects that after "<tes", we are trying to start but not yet processing.
        assertTrue(plugin.isTryingToStart)
        assertFalse(plugin.isProcessing)

        // Then, process xmlTag.last() (which is 't' for "<test>", but comments imply '>')
        // Test expects that processing starts *now*.
        plugin.processChar(xmlTag.last())
        assertTrue(plugin.isProcessing)
        assertFalse(plugin.isTryingToStart)
    }

    @Test
    fun testEndTagRecognition() {
        // 先进入处理状态
        val xmlStart = "<test>"
        for (c in xmlStart) {
            plugin.processChar(c)
        }

        assertTrue(plugin.isProcessing)

        // 测试是否正确消费字符
        val xmlContent = "This is content"
        for (c in xmlContent) {
            val consumed = plugin.processChar(c)
            assertTrue(consumed)
        }

        // 测试结束标签
        val endChar = '>'
        val consumed = plugin.processChar(endChar)
        assertTrue(consumed)
        assertFalse(plugin.isProcessing)
    }

    @Test
    fun testFailedMatch() {
        // 测试非XML内容
        val nonXml = "This is not XML"
        for (c in nonXml) {
            plugin.processChar(c)
        }

        assertFalse(plugin.isTryingToStart)
        assertFalse(plugin.isProcessing)
    }

    @Test
    fun testResetFunction() {
        // 先进入处理状态
        val xmlTag = "<test>"
        for (c in xmlTag) {
            plugin.processChar(c)
        }

        assertTrue(plugin.isProcessing)

        // 重置插件
        plugin.reset()

        // 检查状态复位
        assertFalse(plugin.isProcessing)
        assertFalse(plugin.isTryingToStart)
    }

    @Test
    fun testFullXmlProcessing() {
        val fullXml = "<root attr=\"value\">Content</root>"
        var contentProcessed = false

        for (c in fullXml) {
            val consumed = plugin.processChar(c)

            // 验证在标签内容部分字符被消费
            if (plugin.isProcessing && c != '>') {
                contentProcessed = true
                assertTrue(consumed)
            }
        }

        assertTrue(contentProcessed)
        assertFalse(plugin.isProcessing)
    }
}
