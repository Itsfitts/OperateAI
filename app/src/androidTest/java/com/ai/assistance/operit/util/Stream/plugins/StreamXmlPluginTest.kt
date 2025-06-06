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

        plugin.processChar('t')
        assertTrue(plugin.isTryingToStart)
        assertFalse(plugin.isProcessing)
    }

    @Test
    fun testCompleteTagRecognitionAndProcessingEnter() {
        val xmlTag = "<test>"
        xmlTag.forEach { plugin.processChar(it) }

        assertTrue("Should be in processing state after a full start tag", plugin.isProcessing)
        assertFalse("Should not be trying to start anymore", plugin.isTryingToStart)
    }

    @Test
    fun testTagWithAttributes() {
        val xmlTag = "<test attr=\"value\">"
        xmlTag.forEach { plugin.processChar(it) }
        assertTrue("Should be in processing state after a tag with attributes", plugin.isProcessing)
    }

    @Test
    fun testEndTagRecognition() {
        val fullXml = "<test>content</test>"
        fullXml.forEach { plugin.processChar(it) }

        assertFalse("Should stop processing after the end tag is found", plugin.isProcessing)
    }

    @Test
    fun testFailedMatch() {
        // 测试非XML内容
        val nonXml = "This is not XML"
        nonXml.forEach { plugin.processChar(it) }

        assertFalse(plugin.isTryingToStart)
        assertFalse(plugin.isProcessing)
    }

    @Test
    fun testResetFunction() {
        // 先进入处理状态
        val xmlTag = "<test>"
        xmlTag.forEach { plugin.processChar(it) }

        assertTrue(plugin.isProcessing)

        // 重置插件
        plugin.reset()

        // 检查状态复位
        assertFalse(plugin.isProcessing)
        assertFalse(plugin.isTryingToStart)
    }

    @Test
    fun testFullXmlProcessingAndCharacterConsumption() {
        val fullXml = "<root attr=\"value\">Content</root>"
        var contentProcessed = false
        var tagProcessed = false

        // Test consumption during tag definition
        val pluginInstance = StreamXmlPlugin()
        pluginInstance.initPlugin()
        assertTrue("Should consume '<'", pluginInstance.processChar('<'))
        assertTrue("Should consume 'r' as part of the tag", pluginInstance.processChar('r'))
        tagProcessed = true

        // Reset and test full processing
        plugin.reset()

        fullXml.forEach { c ->
            plugin.processChar(c)
            if (plugin.isProcessing && c != '<' && c != '>') {
                contentProcessed = true
            }
        }

        assertTrue("Tag definition characters should have been consumed", tagProcessed)
        assertTrue("Content characters should have been processed", contentProcessed)
        assertFalse("Should not be in processing state after completion", plugin.isProcessing)
    }
}
