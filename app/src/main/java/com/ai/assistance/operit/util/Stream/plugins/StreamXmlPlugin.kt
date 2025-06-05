package com.ai.assistance.operit.util.Stream.plugins

import com.ai.assistance.operit.util.Stream.*

/** XML流处理插件 用于识别和处理XML格式的数据流 */
class StreamXmlPlugin : StreamPlugin, KmpNodeChangeListener {

    // 状态标志
    override var isProcessing: Boolean = false
        private set

    override var isTryingToStart: Boolean = false
        private set

    // KMP图实例
    private val startTagMatcher: StreamKmpGraph
    private val endTagMatcher: StreamKmpGraph

    // 节点映射，用于跟踪匹配进度
    private val nodePositionMap = mutableMapOf<KmpNode, Int>()

    init {
        // 初始化开始标签匹配器：匹配"<"后跟非空白、非">"、非"/"字符
        startTagMatcher =
                StreamKmpGraphBuilder()
                        .buildFromPattern(
                                kmpPattern {
                                    char('<')
                                    notChar('/')
                                }
                        )

        // 将起始节点映射为位置0
        nodePositionMap[startTagMatcher.getStartNode()] = 0

        // 找到第二个节点(匹配到'<'后的节点)并映射为位置1
        val startNode = startTagMatcher.getStartNode()
        val transitions = startNode.getTransitions()
        transitions.forEach { (_, node) -> nodePositionMap[node] = 1 }

        // 初始化结束标签匹配器：匹配"</"
        endTagMatcher =
                StreamKmpGraphBuilder()
                        .buildFromPattern(
                                kmpPattern {
                                    char('<')
                                    char('/')
                                }
                        )

        // 添加节点变化监听
        startTagMatcher.addNodeChangeListener(this)
    }

    // Suggested change for StreamXmlPlugin.kt
    override fun onNodeChanged(previousNode: KmpNode, currentNode: KmpNode, triggeredChar: Char) {
        val startNodeOfMatcher =
                startTagMatcher.getStartNode() // Assuming this method gives the KMP start node

        if (currentNode.isFinal) {
            // Entire start tag pattern matched
            isProcessing = true
            isTryingToStart = false
            startTagMatcher.reset()
        } else if (currentNode == startNodeOfMatcher && previousNode != startNodeOfMatcher) {
            // Match failed, returned to the start node from an intermediate state
            isTryingToStart = false
        } else if (previousNode == startNodeOfMatcher && currentNode != startNodeOfMatcher) {
            // Moved from start node to an intermediate node (potential match started)
            isTryingToStart = true
        }
        // If transitioning between intermediate non-final states, isTryingToStart should remain
        // true.
        // The existing value is preserved unless one of the above conditions explicitly changes it.
    }

    /** 处理单个字符 */
    override fun processChar(c: Char): Boolean {
        // 如果当前处于处理状态，尝试匹配结束标签
        if (isProcessing) {
            if (endTagMatcher.processChar(c)) {
                // 匹配到结束标签，退出处理状态
                isProcessing = false
                endTagMatcher.reset()
                return true
            }
            return true // 在处理状态中消费所有字符
        }

        // 否则尝试匹配开始标签
        startTagMatcher.processChar(c)
        return false // 匹配过程中不消费字符，只检测
    }

    /** 初始化插件 */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** 销毁插件 */
    override fun destroy() {
        startTagMatcher.removeNodeChangeListener(this)
    }

    /** 重置插件状态 */
    override fun reset() {
        isProcessing = false
        isTryingToStart = false
        startTagMatcher.reset()
        endTagMatcher.reset()
    }
}
