package com.ai.assistance.operit.util.Stream

/** 条件接口，用于KMP图中的字符匹配 允许灵活的模式匹配，超越简单的字符相等性比较 */
interface KmpCondition {
    /** 检查给定字符是否匹配此条件 */
    fun matches(c: Char): Boolean

    /** 获取此条件的描述（用于调试） */
    fun getDescription(): String

    /** 与另一个条件进行OR组合 */
    operator fun plus(other: KmpCondition): KmpCondition = OrCondition(listOf(this, other))

    /** 与另一个条件进行AND组合 */
    operator fun times(other: KmpCondition): KmpCondition = AndCondition(listOf(this, other))

    /** 对此条件取反 */
    operator fun not(): KmpCondition = NotCondition(this)
}

/** 匹配特定字符的简单条件 */
class CharCondition(private val expectedChar: Char) : KmpCondition {
    override fun matches(c: Char): Boolean = c == expectedChar
    override fun getDescription(): String = "'$expectedChar'"
}

/** 匹配指定范围内任何字符的条件 */
class CharRangeCondition(private val from: Char, private val to: Char) : KmpCondition {
    override fun matches(c: Char): Boolean = c in from..to
    override fun getDescription(): String = "[$from-$to]"
}

/** 匹配字符集中任何字符的条件 */
class CharSetCondition(private val charSet: Set<Char>) : KmpCondition {
    constructor(vararg chars: Char) : this(chars.toSet())

    override fun matches(c: Char): Boolean = c in charSet
    override fun getDescription(): String = "[${charSet.joinToString("")}]"
}

/** 对另一个条件取反的条件 */
class NotCondition(private val condition: KmpCondition) : KmpCondition {
    override fun matches(c: Char): Boolean = !condition.matches(c)
    override fun getDescription(): String = "not(${condition.getDescription()})"
}

/** 使用OR逻辑组合多个条件 */
class OrCondition(private val conditions: List<KmpCondition>) : KmpCondition {
    constructor(vararg conditions: KmpCondition) : this(conditions.toList())

    override fun matches(c: Char): Boolean = conditions.any { it.matches(c) }
    override fun getDescription(): String =
            "(${conditions.joinToString(" OR ") { it.getDescription() }})"
}

/** 使用AND逻辑组合多个条件 */
class AndCondition(private val conditions: List<KmpCondition>) : KmpCondition {
    constructor(vararg conditions: KmpCondition) : this(conditions.toList())

    override fun matches(c: Char): Boolean = conditions.all { it.matches(c) }
    override fun getDescription(): String =
            "(${conditions.joinToString(" AND ") { it.getDescription() }})"
}

/** 使用自定义谓词函数匹配字符的条件 */
class PredicateCondition(
        private val description: String,
        private val predicate: (Char) -> Boolean
) : KmpCondition {
    override fun matches(c: Char): Boolean = predicate(c)
    override fun getDescription(): String = description
}

/** 贪心星号匹配条件（内部使用） */
internal class GreedyStarCondition(val condition: KmpCondition) : KmpCondition {
    override fun matches(c: Char): Boolean = condition.matches(c)
    override fun getDescription(): String = "greedy*(${condition.getDescription()})"
}

/** A private marker condition used by the builder to handle capturing groups. */
internal class GroupCondition(val groupId: Int, val conditions: List<KmpCondition>) : KmpCondition {
    override fun matches(c: Char): Boolean =
            throw UnsupportedOperationException(
                    "GroupCondition is a builder marker and should not be matched directly."
            )

    override fun getDescription(): String = "GROUP($groupId)"
}

/** KMP状态机图中的节点 */
class KmpNode(
        val id: Int,
        val depth: Int, // The non-looping depth from the start node, used for match length
        // calculation.
        var isFinal: Boolean = false
) {
    private val transitions = mutableMapOf<KmpCondition, KmpNode>()
    var failureNode: KmpNode? = null
    val startOfGroups = mutableSetOf<Int>()
    val endOfGroups = mutableSetOf<Int>()

    /** 添加从此节点到另一个节点的转换 */
    fun addTransition(condition: KmpCondition, targetNode: KmpNode) {
        transitions[condition] = targetNode
    }

    /** 基于输入字符查找下一个节点 */
    fun getNextNode(c: Char): KmpNode? {
        for ((condition, node) in transitions) {
            if (condition.matches(c)) {
                return node
            }
        }
        return null
    }

    /** 获取此节点的所有出站转换 */
    fun getTransitions(): Map<KmpCondition, KmpNode> = transitions
}

/** 节点变化监听器，用于监听KMP图中当前节点的变化 */
interface KmpNodeChangeListener {
    /**
     * 当当前节点发生变化时调用
     * @param previousNode 变化前的节点
     * @param currentNode 变化后的节点
     * @param triggeredChar 触发此变化的字符
     */
    fun onNodeChanged(previousNode: KmpNode, currentNode: KmpNode, triggeredChar: Char)
}

/** 实现基于图的Knuth-Morris-Pratt算法，支持自定义转换和灵活的模式匹配 */
class StreamKmpGraph {
    private val nodes = mutableListOf<KmpNode>()
    private var currentNode: KmpNode
    private val startNode: KmpNode
    private val nodeChangeListeners = mutableListOf<KmpNodeChangeListener>()
    private val characterStreamBuffer = StringBuilder()
    private var currentMatchLength = 0
    private val capturedGroups = mutableMapOf<Int, String>()

    // Tracks the start index in the buffer for currently active capture groups.
    private val activeGroups = mutableMapOf<Int, Int>()

    init {
        startNode = createNode(0)
        currentNode = startNode
    }

    /** 添加节点变化监听器 */
    fun addNodeChangeListener(listener: KmpNodeChangeListener) {
        nodeChangeListeners.add(listener)
    }

    /** 移除节点变化监听器 */
    fun removeNodeChangeListener(listener: KmpNodeChangeListener) {
        nodeChangeListeners.remove(listener)
    }

    /** 通知所有监听器节点变化 */
    private fun notifyNodeChanged(
            previousNode: KmpNode,
            currentNode: KmpNode,
            triggeredChar: Char
    ) {
        nodeChangeListeners.forEach { listener ->
            listener.onNodeChanged(previousNode, currentNode, triggeredChar)
        }
    }

    /** 在图中创建新节点 */
    fun createNode(depth: Int, isFinal: Boolean = false): KmpNode {
        val node = KmpNode(nodes.size, depth, isFinal)
        nodes.add(node)
        return node
    }

    /** 添加两个节点之间的转换 */
    fun addTransition(fromNode: KmpNode, toNode: KmpNode, condition: KmpCondition) {
        fromNode.addTransition(condition, toNode)
    }

    /** 设置节点的失败转换 */
    fun setFailure(node: KmpNode, failureNode: KmpNode) {
        node.failureNode = failureNode
    }

    /** 处理单个字符并更新当前状态 */
    fun processChar(c: Char): StreamKmpMatchResult {
        characterStreamBuffer.append(c)
        var nextNode = currentNode.getNextNode(c)

        if (nextNode != null) {
            // Simple transition, advance match length
            currentMatchLength++
        } else {
            // Failure, requires jump.
            var searchNode = currentNode.failureNode
            while (searchNode != null) {
                nextNode = searchNode.getNextNode(c)
                if (nextNode != null) {
                    // Found a fallback transition. Adjust match length based on failure node's
                    // depth.
                    currentMatchLength = searchNode.depth + 1
                    break
                }
                if (searchNode == startNode) break
                searchNode = searchNode.failureNode
            }

            // If still no match after checking all failure links
            if (nextNode == null) {
                // Try one last time from the very start
                nextNode = startNode.getNextNode(c)
                currentMatchLength = if (nextNode != null) 1 else 0
            }
        }

        // Default to start node if no path was found at all
        currentNode = nextNode ?: startNode

        // --- Group Management ---

        // 1. Invalidate groups that were on a failed path
        val validBufferStart = characterStreamBuffer.length - currentMatchLength
        activeGroups.entries.retainAll { (_, groupStartIndex) ->
            groupStartIndex >= validBufferStart
        }

        // 2. Start any new groups at the current node
        // This logic assumes that a transition "out of" a node with startOfGroups begins the
        // capture.
        // Let's refine this to check the previous node on a successful transition.
        // For now, the builder puts the start marker on the node where the group begins.
        currentNode.startOfGroups.forEach { groupId ->
            if (!activeGroups.containsKey(groupId)) {
                activeGroups[groupId] =
                        characterStreamBuffer.length - 1 // Group starts at current char
            }
        }

        // 3. Complete any groups ending at the current node
        currentNode.endOfGroups.forEach { groupId ->
            activeGroups.remove(groupId)?.let { startIndex ->
                // The character that led to this node is the final character of the group.
                val content =
                        characterStreamBuffer.substring(startIndex, characterStreamBuffer.length)
                capturedGroups[groupId] = content
            }
        }

        // --- Result Calculation ---
        if (currentNode.isFinal) {
            val result = StreamKmpMatchResult.Match(capturedGroups.toMap(), true)
            // After a full match, clear the groups for the next potential match.
            capturedGroups.clear()
            return result
        }

        return if (currentNode == startNode && currentMatchLength == 0) {
            capturedGroups.clear() // No match, so clear any groups captured on a failed path
            StreamKmpMatchResult.NoMatch
        } else {
            StreamKmpMatchResult.InProgress
        }
    }

    /** 处理一串字符 */
    fun processText(text: String): List<Int> {
        reset() // Always start a full text scan from a clean state.
        val matchPositions = mutableListOf<Int>()

        text.forEachIndexed { index, c ->
            val result = processChar(c)
            if (result is StreamKmpMatchResult.Match && result.isFullMatch) {
                matchPositions.add(index + 1)
                // Do NOT reset here. KMP's failure function handles finding the next match.
            }
        }

        return matchPositions
    }

    /** 重置图到初始状态 */
    fun reset() {
        currentNode = startNode
        characterStreamBuffer.clear()
        currentMatchLength = 0
        activeGroups.clear()
        capturedGroups.clear()
    }

    /** 获取当前状态节点 */
    fun getCurrentNode(): KmpNode = currentNode

    /** 获取起始节点 */
    fun getStartNode(): KmpNode = startNode

    /** 获取图中的所有节点 */
    fun getNodes(): List<KmpNode> = nodes.toList()

    /**
     * 查找并返回文本中所有匹配的子字符串。 此方法会处理整个文本并返回一个包含所有非重叠匹配项的列表。
     *
     * @param text 要在其中搜索模式的输入字符串。
     * @return 一个字符串列表，其中每个字符串都是输入文本中与模式匹配的子序列。
     */
    fun findMatches(text: String): List<String> {
        val matches = mutableListOf<String>()
        var currentMatchLength = 0
        var lastMatchEnd = -1
        reset()

        text.forEachIndexed { index, c ->
            var nextNode: KmpNode? = currentNode.getNextNode(c)

            if (nextNode != null) {
                if (currentNode == nextNode) { // Self-loop for greedy star
                    currentMatchLength++
                } else {
                    currentMatchLength++
                }
            } else {
                var searchNode = currentNode.failureNode
                while (searchNode != null) {
                    nextNode = searchNode.getNextNode(c)
                    if (nextNode != null) {
                        currentMatchLength = searchNode.depth + 1
                        break
                    }
                    if (searchNode == startNode) break
                    searchNode = searchNode.failureNode
                }

                if (nextNode == null) {
                    nextNode = startNode.getNextNode(c)
                    if (nextNode != null) {
                        currentMatchLength = 1
                    } else {
                        nextNode = startNode
                        currentMatchLength = 0
                    }
                }
            }
            currentNode = nextNode!!

            if (currentNode.isFinal) {
                val startIndex = index - currentMatchLength + 1
                if (startIndex > lastMatchEnd) {
                    matches.add(text.substring(startIndex, index + 1))
                    lastMatchEnd = index
                }
            }
        }
        return matches
    }
}

/** 简化的KMP图构建器 */
class StreamKmpGraphBuilder {
    fun build(pattern: KmpPattern): StreamKmpGraph {
        val graph = StreamKmpGraph()
        val finalNode = buildRecursive(graph, graph.getStartNode(), pattern.conditions, 0).first
        finalNode.isFinal = true
        setupFailureTransitions(graph)
        return graph
    }

    private fun buildRecursive(
            graph: StreamKmpGraph,
            startNode: KmpNode,
            conditions: List<KmpCondition>,
            depth: Int
    ): Pair<KmpNode, Int> {
        var currentNode = startNode
        var currentDepth = depth

        conditions.forEachIndexed { index, condition ->
            when (condition) {
                is GroupCondition -> {
                    val groupConditions = condition.conditions
                    if (groupConditions.isEmpty()) {
                        // Handle empty group: mark start and end on the same current node.
                        currentNode.startOfGroups.add(condition.groupId)
                        currentNode.endOfGroups.add(condition.groupId)
                        return@forEachIndexed // Continue to next condition in the loop
                    }

                    // Manually process the first step of the group to mark the correct start node.
                    var groupNode = currentNode
                    var groupDepth = currentDepth

                    // Process the first condition of the group
                    val firstCond = groupConditions.first()
                    // This assumes the first condition is not a complex one like another group, which is a reasonable simplification for now.
                    groupDepth++
                    val firstNodeInGroup = graph.createNode(groupDepth)
                    graph.addTransition(groupNode, firstNodeInGroup, firstCond)
                    firstNodeInGroup.startOfGroups.add(condition.groupId) // Mark the START here
                    groupNode = firstNodeInGroup

                    // Recursively build the rest of the group
                    val remainingConditions = groupConditions.drop(1)
                    val (lastNodeInGroup, finalGroupDepth) =
                            buildRecursive(graph, groupNode, remainingConditions, groupDepth)

                    lastNodeInGroup.endOfGroups.add(condition.groupId) // Mark the END here
                    currentNode = lastNodeInGroup
                    currentDepth = finalGroupDepth
                }
                is GreedyStarCondition -> {
                    val nextRealCondition = conditions.drop(index + 1).firstOrNull()
                    val loopCondition =
                            if (nextRealCondition != null) {
                                AndCondition(condition.condition, NotCondition(nextRealCondition))
                            } else {
                                condition.condition
                            }
                    graph.addTransition(currentNode, currentNode, loopCondition)
                    // If the greedy star is the very last part of the pattern, this node should
                    // also be final.
                    if (nextRealCondition == null) {
                        currentNode.isFinal = true
                    }
                }
                else -> { // Regular condition
                    currentDepth++
                    val nextNode = graph.createNode(currentDepth, false)
                    graph.addTransition(currentNode, nextNode, condition)
                    currentNode = nextNode
                }
            }
        }
        return currentNode to currentDepth
    }

    /** 设置图的失败转换（简化版） */
    private fun setupFailureTransitions(graph: StreamKmpGraph) {
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

/** 简化的模式构建DSL，允许以更简洁的方式构建KMP匹配条件 */
class KmpPattern {
    val conditions = mutableListOf<KmpCondition>()

    /** 添加一个匹配条件 */
    fun add(condition: KmpCondition) {
        conditions.add(condition)
    }

    /** 定义一个捕获组 */
    fun group(id: Int, builder: KmpPattern.() -> Unit) {
        val subPattern = kmpPattern(builder)
        // GroupCondition now just wraps the sub-pattern. The builder will handle marking nodes.
        add(GroupCondition(id, subPattern.conditions))
    }

    /** 添加一个字符匹配条件 */
    fun char(c: Char) {
        add(CharCondition(c))
    }

    /** 添加一个忽略大小写的字符匹配条件 */
    fun charIgnoreCase(c: Char) {
        add(CharCondition(c.lowercaseChar()) + CharCondition(c.uppercaseChar()))
    }

    /** 添加一个字符范围匹配条件 */
    fun range(from: Char, to: Char) {
        add(CharRangeCondition(from, to))
    }

    /** 添加一个字符集匹配条件 */
    fun anyOf(vararg chars: Char) {
        add(CharSetCondition(*chars))
    }

    /** 添加一个不匹配指定字符的条件 */
    fun notChar(c: Char) {
        add(NotCondition(CharCondition(c)))
    }

    /** 添加一个不匹配指定字符集的条件 */
    fun noneOf(vararg chars: Char) {
        add(NotCondition(CharSetCondition(*chars)))
    }

    /** 添加一个不匹配指定范围的条件 */
    fun notInRange(from: Char, to: Char) {
        add(NotCondition(CharRangeCondition(from, to)))
    }

    /** 添加一个自定义匹配条件 */
    fun predicate(description: String, predicate: (Char) -> Boolean) {
        add(PredicateCondition(description, predicate))
    }

    /** 添加一个数字匹配条件 */
    fun digit() {
        add(PredicateCondition("digit") { it.isDigit() })
    }

    /** 添加一个非数字匹配条件 */
    fun notDigit() {
        add(NotCondition(PredicateCondition("digit") { it.isDigit() }))
    }

    /** 添加一个字母匹配条件 */
    fun letter() {
        add(PredicateCondition("letter") { it.isLetter() })
    }

    /** 添加一个非字母匹配条件 */
    fun notLetter() {
        add(NotCondition(PredicateCondition("letter") { it.isLetter() }))
    }

    /** 添加一个字母或数字匹配条件 */
    fun letterOrDigit() {
        add(PredicateCondition("letterOrDigit") { it.isLetterOrDigit() })
    }

    /** 添加一个非字母非数字匹配条件 */
    fun notLetterOrDigit() {
        add(NotCondition(PredicateCondition("letterOrDigit") { it.isLetterOrDigit() }))
    }

    /** 添加一个空白字符匹配条件 */
    fun whitespace() {
        add(PredicateCondition("whitespace") { it.isWhitespace() })
    }

    /** 添加一个非空白字符匹配条件 */
    fun notWhitespace() {
        add(NotCondition(PredicateCondition("whitespace") { it.isWhitespace() }))
    }

    /** 添加任意字符匹配条件（通配符） */
    fun any() {
        add(PredicateCondition("any") { true })
    }

    /**
     * Adds a literal string to the pattern by converting each character into a CharCondition.
     * @param sequence The string to be matched literally.
     */
    fun literal(sequence: CharSequence) {
        for (c in sequence) {
            char(c)
        }
    }

    /**
     * 添加一个贪心匹配（*），匹配零个或多个满足条件的字符。 这将尽可能多地匹配字符。 注意：为了避免歧义，循环条件会自动排除掉紧随其后的第一个非贪心匹配条件。
     * @param conditionBuilder 构建要重复匹配的条件的lambda
     */
    fun greedyStar(conditionBuilder: KmpPattern.() -> Unit) {
        val subPattern = kmpPattern(conditionBuilder)
        // We assume the builder produces a single, primary condition for the star.
        // If multiple are produced, we OR them together.
        require(subPattern.conditions.isNotEmpty()) { "greedyStar condition cannot be empty." }
        val condition =
                if (subPattern.conditions.size == 1) {
                    subPattern.conditions.first()
                } else {
                    OrCondition(subPattern.conditions)
                }
        add(GreedyStarCondition(condition))
    }

    /**
     * 重复一个模式指定的次数。
     * @param count 重复次数
     * @param builder 用于构建要重复的模式的lambda
     */
    fun repeat(count: Int, builder: KmpPattern.() -> Unit) {
        require(count >= 0) { "Repeat count must be non-negative." }
        if (count == 0) return
        val subPattern = kmpPattern(builder)
        for (i in 1..count) {
            conditions.addAll(subPattern.conditions)
        }
    }

    /** 添加对条件的反向匹配 */
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
