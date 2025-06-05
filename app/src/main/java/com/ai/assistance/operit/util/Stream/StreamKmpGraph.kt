package com.ai.assistance.operit.util.Stream

/**
 * 条件接口，用于KMP图中的字符匹配
 * 允许灵活的模式匹配，超越简单的字符相等性比较
 */
interface KmpCondition {
    /**
     * 检查给定字符是否匹配此条件
     */
    fun matches(c: Char): Boolean
    
    /**
     * 获取此条件的描述（用于调试）
     */
    fun getDescription(): String
    
    /**
     * 与另一个条件进行OR组合
     */
    operator fun plus(other: KmpCondition): KmpCondition = OrCondition(listOf(this, other))
    
    /**
     * 与另一个条件进行AND组合
     */
    operator fun times(other: KmpCondition): KmpCondition = AndCondition(listOf(this, other))
    
    /**
     * 对此条件取反
     */
    operator fun not(): KmpCondition = NotCondition(this)
}

/**
 * 匹配特定字符的简单条件
 */
class CharCondition(private val expectedChar: Char) : KmpCondition {
    override fun matches(c: Char): Boolean = c == expectedChar
    override fun getDescription(): String = "'$expectedChar'"
}

/**
 * 匹配指定范围内任何字符的条件
 */
class CharRangeCondition(private val from: Char, private val to: Char) : KmpCondition {
    override fun matches(c: Char): Boolean = c in from..to
    override fun getDescription(): String = "[$from-$to]"
}

/**
 * 匹配字符集中任何字符的条件
 */
class CharSetCondition(private val charSet: Set<Char>) : KmpCondition {
    constructor(vararg chars: Char) : this(chars.toSet())
    
    override fun matches(c: Char): Boolean = c in charSet
    override fun getDescription(): String = "[${charSet.joinToString("")}]"
}

/**
 * 对另一个条件取反的条件
 */
class NotCondition(private val condition: KmpCondition) : KmpCondition {
    override fun matches(c: Char): Boolean = !condition.matches(c)
    override fun getDescription(): String = "not(${condition.getDescription()})"
}

/**
 * 使用OR逻辑组合多个条件
 */
class OrCondition(private val conditions: List<KmpCondition>) : KmpCondition {
    constructor(vararg conditions: KmpCondition) : this(conditions.toList())
    
    override fun matches(c: Char): Boolean = conditions.any { it.matches(c) }
    override fun getDescription(): String = "(${conditions.joinToString(" OR ") { it.getDescription() }})"
}

/**
 * 使用AND逻辑组合多个条件
 */
class AndCondition(private val conditions: List<KmpCondition>) : KmpCondition {
    constructor(vararg conditions: KmpCondition) : this(conditions.toList())
    
    override fun matches(c: Char): Boolean = conditions.all { it.matches(c) }
    override fun getDescription(): String = "(${conditions.joinToString(" AND ") { it.getDescription() }})"
}

/**
 * 使用自定义谓词函数匹配字符的条件
 */
class PredicateCondition(
    private val description: String,
    private val predicate: (Char) -> Boolean
) : KmpCondition {
    override fun matches(c: Char): Boolean = predicate(c)
    override fun getDescription(): String = description
}

/**
 * KMP状态机图中的节点
 */
class KmpNode(
    val id: Int, 
    var isFinal: Boolean = false
) {
    private val transitions = mutableMapOf<KmpCondition, KmpNode>()
    var failureNode: KmpNode? = null
    
    /**
     * 添加从此节点到另一个节点的转换
     */
    fun addTransition(condition: KmpCondition, targetNode: KmpNode) {
        transitions[condition] = targetNode
    }
    
    /**
     * 基于输入字符查找下一个节点
     */
    fun getNextNode(c: Char): KmpNode? {
        for ((condition, node) in transitions) {
            if (condition.matches(c)) {
                return node
            }
        }
        return null
    }
    
    /**
     * 获取此节点的所有出站转换
     */
    fun getTransitions(): Map<KmpCondition, KmpNode> = transitions
}

/**
 * 节点变化监听器，用于监听KMP图中当前节点的变化
 */
interface KmpNodeChangeListener {
    /**
     * 当当前节点发生变化时调用
     * @param previousNode 变化前的节点
     * @param currentNode 变化后的节点
     * @param triggeredChar 触发此变化的字符
     */
    fun onNodeChanged(previousNode: KmpNode, currentNode: KmpNode, triggeredChar: Char)
}

/**
 * 实现基于图的Knuth-Morris-Pratt算法，支持自定义转换和灵活的模式匹配
 */
class StreamKmpGraph {
    private val nodes = mutableListOf<KmpNode>()
    private var currentNode: KmpNode
    private val startNode: KmpNode
    private val nodeChangeListeners = mutableListOf<KmpNodeChangeListener>()
    
    init {
        startNode = createNode()
        currentNode = startNode
    }
    
    /**
     * 添加节点变化监听器
     */
    fun addNodeChangeListener(listener: KmpNodeChangeListener) {
        nodeChangeListeners.add(listener)
    }
    
    /**
     * 移除节点变化监听器
     */
    fun removeNodeChangeListener(listener: KmpNodeChangeListener) {
        nodeChangeListeners.remove(listener)
    }
    
    /**
     * 通知所有监听器节点变化
     */
    private fun notifyNodeChanged(previousNode: KmpNode, currentNode: KmpNode, triggeredChar: Char) {
        nodeChangeListeners.forEach { listener ->
            listener.onNodeChanged(previousNode, currentNode, triggeredChar)
        }
    }
    
    /**
     * 在图中创建新节点
     */
    fun createNode(isFinal: Boolean = false): KmpNode {
        val node = KmpNode(nodes.size, isFinal)
        nodes.add(node)
        return node
    }
    
    /**
     * 添加两个节点之间的转换
     */
    fun addTransition(fromNode: KmpNode, toNode: KmpNode, condition: KmpCondition) {
        fromNode.addTransition(condition, toNode)
    }
    
    /**
     * 设置节点的失败转换
     */
    fun setFailure(node: KmpNode, failureNode: KmpNode) {
        node.failureNode = failureNode
    }
    
    /**
     * 处理单个字符并更新当前状态
     */
    fun processChar(c: Char): Boolean {
        val previousNode = currentNode
        var nextNode = currentNode.getNextNode(c)
        
        if (nextNode == null) {
            var currentSearchNode = currentNode.failureNode
            while (currentSearchNode != null) {
                nextNode = currentSearchNode.getNextNode(c)
                if (nextNode != null) {
                    break // Found a transition
                }
                // If currentSearchNode is the startNode and it couldn't find a nextNode,
                // further iterations via startNode.failureNode (which is startNode itself) are futile.
                // This prevents an infinite loop if startNode is its own failureNode and doesn't match c.
                if (currentSearchNode == startNode) {
                    break 
                }
                currentSearchNode = currentSearchNode.failureNode
            }
            
            // If no transition found even through failure links
            if (nextNode == null) {
                // Try one last time from the startNode.
                // This handles cases where:
                // 1. The failure path was exhausted without finding a match.
                // 2. The loop broke because currentSearchNode was startNode and didn't match c.
                // 3. currentNode.failureNode was initially null (though not with current simplified setup).
                nextNode = startNode.getNextNode(c)
                if (nextNode == null) {
                    // If still no match from startNode, default to staying at (or returning to) startNode.
                    nextNode = startNode
                }
            }
        }
        
        currentNode = nextNode!! // nextNode will always be non-null here due to the logic above ensuring it defaults to startNode.
        
        // 通知监听器节点变化
        notifyNodeChanged(previousNode, currentNode, c)
        
        return currentNode.isFinal
    }
    
    /**
     * 处理一串字符
     */
    fun processText(text: String): List<Int> {
        val matchPositions = mutableListOf<Int>()
        
        text.forEachIndexed { index, c ->
            if (processChar(c)) {
                matchPositions.add(index + 1) // KMP typically reports 1-based end positions or 0-based start positions. This is 1-based end.
            }
        }
        
        return matchPositions
    }
    
    /**
     * 重置图到初始状态
     */
    fun reset() {
        currentNode = startNode
    }
    
    /**
     * 获取当前状态节点
     */
    fun getCurrentNode(): KmpNode = currentNode
    
    /**
     * 获取起始节点
     */
    fun getStartNode(): KmpNode = startNode
    
    /**
     * 获取图中的所有节点
     */
    fun getNodes(): List<KmpNode> = nodes.toList()
}

/**
 * 简化的KMP图构建器
 */
class StreamKmpGraphBuilder {
    private val graph = StreamKmpGraph()
    
    /**
     * 从字符串模式创建简单的模式匹配器
     */
    fun buildFromPattern(pattern: String): StreamKmpGraph {
        if (pattern.isEmpty()) return graph
        
        val startNode = graph.getStartNode()
        var currentNode = startNode
        
        for ((index, c) in pattern.withIndex()) {
            val isLast = index == pattern.length - 1
            val nextNode = graph.createNode(isFinal = isLast)
            graph.addTransition(currentNode, nextNode, CharCondition(c))
            currentNode = nextNode
        }
        
        setupFailureTransitions()
        return graph
    }
    
    /**
     * 从条件列表创建模式匹配器
     */
    fun buildFromConditions(conditions: List<KmpCondition>): StreamKmpGraph {
        if (conditions.isEmpty()) return graph
        
        val startNode = graph.getStartNode()
        var currentNode = startNode
        
        for ((index, condition) in conditions.withIndex()) {
            val isLast = index == conditions.size - 1
            val nextNode = graph.createNode(isFinal = isLast)
            graph.addTransition(currentNode, nextNode, condition)
            currentNode = nextNode
        }
        
        setupFailureTransitions()
        return graph
    }
    
    /**
     * 从KmpPattern创建模式匹配器
     */
    fun buildFromPattern(pattern: KmpPattern): StreamKmpGraph {
        return buildFromConditions(pattern.conditions)
    }
    
    /**
     * 设置图的失败转换（简化版）
     */
    private fun setupFailureTransitions() {
        val nodes = graph.getNodes()
        val startNode = graph.getStartNode()
        
        // 将所有节点的失败转换设为起始节点（简化版本）
        for (node in nodes) {
            if (node != startNode) {
                graph.setFailure(node, startNode)
            }
        }
        
        // 起始节点的失败是其自身
        graph.setFailure(startNode, startNode)
    }
} 

/**
 * 简化的模式构建DSL，允许以更简洁的方式构建KMP匹配条件
 */
class KmpPattern {
    val conditions = mutableListOf<KmpCondition>()
    
    /**
     * 添加一个匹配条件
     */
    fun add(condition: KmpCondition) {
        conditions.add(condition)
    }
    
    /**
     * 添加一个字符匹配条件
     */
    fun char(c: Char) {
        add(CharCondition(c))
    }
    
    /**
     * 添加一个忽略大小写的字符匹配条件
     */
    fun charIgnoreCase(c: Char) {
        add(CharCondition(c.lowercaseChar()) + CharCondition(c.uppercaseChar()))
    }
    
    /**
     * 添加一个字符范围匹配条件
     */
    fun range(from: Char, to: Char) {
        add(CharRangeCondition(from, to))
    }
    
    /**
     * 添加一个字符集匹配条件
     */
    fun anyOf(vararg chars: Char) {
        add(CharSetCondition(*chars))
    }

    /**
     * 添加一个不匹配指定字符的条件
     */
    fun notChar(c: Char) {
        add(NotCondition(CharCondition(c)))
    }
    
    /**
     * 添加一个不匹配指定字符集的条件
     */
    fun noneOf(vararg chars: Char) {
        add(NotCondition(CharSetCondition(*chars)))
    }
    
    /**
     * 添加一个不匹配指定范围的条件
     */
    fun notInRange(from: Char, to: Char) {
        add(NotCondition(CharRangeCondition(from, to)))
    }
    
    /**
     * 添加一个自定义匹配条件
     */
    fun predicate(description: String, predicate: (Char) -> Boolean) {
        add(PredicateCondition(description, predicate))
    }
    
    /**
     * 添加一个数字匹配条件
     */
    fun digit() {
        add(PredicateCondition("digit") { it.isDigit() })
    }
    
    /**
     * 添加一个非数字匹配条件
     */
    fun notDigit() {
        add(NotCondition(PredicateCondition("digit") { it.isDigit() }))
    }
    
    /**
     * 添加一个字母匹配条件
     */
    fun letter() {
        add(PredicateCondition("letter") { it.isLetter() })
    }
    
    /**
     * 添加一个非字母匹配条件
     */
    fun notLetter() {
        add(NotCondition(PredicateCondition("letter") { it.isLetter() }))
    }
    
    /**
     * 添加一个字母或数字匹配条件
     */
    fun letterOrDigit() {
        add(PredicateCondition("letterOrDigit") { it.isLetterOrDigit() })
    }
    
    /**
     * 添加一个非字母非数字匹配条件
     */
    fun notLetterOrDigit() {
        add(NotCondition(PredicateCondition("letterOrDigit") { it.isLetterOrDigit() }))
    }
    
    /**
     * 添加一个空白字符匹配条件
     */
    fun whitespace() {
        add(PredicateCondition("whitespace") { it.isWhitespace() })
    }
    
    /**
     * 添加一个非空白字符匹配条件
     */
    fun notWhitespace() {
        add(NotCondition(PredicateCondition("whitespace") { it.isWhitespace() }))
    }
    
    /**
     * 添加任意字符匹配条件（通配符）
     */
    fun any() {
        add(PredicateCondition("any") { true })
    }
    
    /**
     * 添加对条件的反向匹配
     */
    fun not(condition: KmpCondition) {
        add(NotCondition(condition))
    }
}

// 扩展函数，简化单个字符匹配条件的创建
operator fun Char.unaryPlus(): KmpCondition = CharCondition(this)

// 扩展属性，用于常见字符组
val DIGITS: KmpCondition = PredicateCondition("DIGITS") { it.isDigit() }
val LETTERS: KmpCondition = PredicateCondition("LETTERS") { it.isLetter() }
val ALPHANUMERIC: KmpCondition = PredicateCondition("ALPHANUMERIC") { it.isLetterOrDigit() }
val WHITESPACE: KmpCondition = PredicateCondition("WHITESPACE") { it.isWhitespace() }
val ANY_CHAR: KmpCondition = PredicateCondition("ANY") { true }

// 反向的预定义条件
val NOT_DIGITS: KmpCondition = NotCondition(DIGITS)
val NOT_LETTERS: KmpCondition = NotCondition(LETTERS)
val NOT_ALPHANUMERIC: KmpCondition = NotCondition(ALPHANUMERIC)
val NOT_WHITESPACE: KmpCondition = NotCondition(WHITESPACE)

// 忽略大小写的字符匹配
infix fun Char.or(other: Char): KmpCondition = CharCondition(this) + CharCondition(other)

// 字符范围匹配
infix fun Char.to(other: Char): KmpCondition = CharRangeCondition(this, other)

// 字符集匹配
fun chars(vararg chars: Char): KmpCondition = CharSetCondition(*chars)

// 字符集不匹配
fun notChars(vararg chars: Char): KmpCondition = NotCondition(CharSetCondition(*chars))

// 不在字符范围内
fun notInRange(from: Char, to: Char): KmpCondition = NotCondition(CharRangeCondition(from, to))

// 自由函数形式的否定
fun not(condition: KmpCondition): KmpCondition = NotCondition(condition)

// DSL构建器
fun kmpPattern(init: KmpPattern.() -> Unit): KmpPattern {
    val pattern = KmpPattern()
    pattern.init()
    return pattern
}

// 例如：+('a' or 'A'), 'a'..'z', chars('a', 'e', 'i', 'o', 'u')
// 或者: kmpPattern { charIgnoreCase('a'); digit(); letter() }
// 反向匹配： not(DIGITS), notChars('a', 'e', 'i', 'o', 'u'), notInRange('0', '9') 