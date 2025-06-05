package com.ai.assistance.operit.util.Stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@MediumTest
class StreamKmpGraphTest {

    @Test
    fun testCharCondition() {
        val condition = CharCondition('a')
        assertTrue(condition.matches('a'))
        assertFalse(condition.matches('b'))
        assertEquals("'a'", condition.getDescription())
    }

    @Test
    fun testCharRangeCondition() {
        val condition = CharRangeCondition('a', 'c')
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertEquals("[a-c]", condition.getDescription())
    }

    @Test
    fun testCharSetCondition() {
        val condition = CharSetCondition('a', 'c', 'e')
        assertTrue(condition.matches('a'))
        assertFalse(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertTrue(condition.matches('e'))
        assertEquals("[ace]", condition.getDescription())
    }

    @Test
    fun testNotCondition() {
        val baseCondition = CharCondition('a')
        val notCondition = NotCondition(baseCondition)
        assertFalse(notCondition.matches('a'))
        assertTrue(notCondition.matches('b'))
        assertEquals("not('a')", notCondition.getDescription())
    }

    @Test
    fun testOrCondition() {
        val condition = OrCondition(
            CharCondition('a'),
            CharCondition('b'),
            CharCondition('c')
        )
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertTrue(condition.getDescription().contains("OR"))
    }

    @Test
    fun testAndCondition() {
        val condition = AndCondition(
            CharRangeCondition('a', 'z'),
            NotCondition(CharSetCondition('x', 'y', 'z'))
        )
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertFalse(condition.matches('x'))
        assertFalse(condition.matches('y'))
        assertFalse(condition.matches('z'))
        assertTrue(condition.getDescription().contains("AND"))
    }

    @Test
    fun testPredicateCondition() {
        val condition = PredicateCondition("isDigit") { it.isDigit() }
        assertTrue(condition.matches('0'))
        assertTrue(condition.matches('9'))
        assertFalse(condition.matches('a'))
        assertEquals("isDigit", condition.getDescription())
    }

    @Test
    fun testKmpNode() {
        val node1 = KmpNode(1)
        val node2 = KmpNode(2)
        val condition = CharCondition('a')
        
        node1.addTransition(condition, node2)
        assertEquals(node2, node1.getNextNode('a'))
        assertNull(node1.getNextNode('b'))
        
        val transitions = node1.getTransitions()
        assertEquals(1, transitions.size)
        assertTrue(transitions.containsKey(condition))
        assertEquals(node2, transitions[condition])
    }

    @Test
    fun testStreamKmpGraphSimple() {
        val graph = StreamKmpGraph()
        val node1 = graph.createNode()
        val node2 = graph.createNode(true)
        
        graph.addTransition(graph.getStartNode(), node1, CharCondition('a'))
        graph.addTransition(node1, node2, CharCondition('b'))
        
        assertFalse(graph.processChar('a'))
        assertTrue(graph.processChar('b'))
        
        graph.reset()
        assertFalse(graph.processChar('c'))
    }

    @Test
    fun testStreamKmpGraphProcessText() {
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern("abc")
        
        val matches = graph.processText("ababcabc")
        assertEquals(2, matches.size)
        assertEquals(5, matches[0])
        assertEquals(8, matches[1])
    }

    @Test
    fun testStreamKmpGraphBuilder() {
        // 测试字符串模式构建
        val builder1 = StreamKmpGraphBuilder()
        val graph1 = builder1.buildFromPattern("test")
        
        graph1.reset()
        assertFalse(graph1.processChar('t'))
        assertFalse(graph1.processChar('e'))
        assertFalse(graph1.processChar('s'))
        assertTrue(graph1.processChar('t'))
        
        // 测试条件列表构建
        val builder2 = StreamKmpGraphBuilder()
        val graph2 = builder2.buildFromConditions(listOf(
            CharCondition('a'),
            CharRangeCondition('1', '3'),
            CharSetCondition('x', 'y', 'z')
        ))
        
        graph2.reset()
        assertFalse(graph2.processChar('a'))
        assertFalse(graph2.processChar('2'))
        assertTrue(graph2.processChar('y'))
    }

    @Test
    fun testComplexPattern() {
        val builder = StreamKmpGraphBuilder()
        val conditions = listOf(
            OrCondition(CharCondition('a'), CharCondition('A')),
            OrCondition(CharCondition('b'), CharCondition('B')),
            OrCondition(CharCondition('c'), CharCondition('C'))
        )
        val graph = builder.buildFromConditions(conditions)
        
        // 测试小写匹配
        graph.reset()
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('b'))
        assertTrue(graph.processChar('c'))
        
        // 测试大写匹配
        graph.reset()
        assertFalse(graph.processChar('A'))
        assertFalse(graph.processChar('B'))
        assertTrue(graph.processChar('C'))
        
        // 测试混合匹配
        graph.reset()
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('B'))
        assertTrue(graph.processChar('c'))
    }

    @Test
    fun testFailureTransitions() {
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern("ABABC")
        
        val text = "ABABABABC"
        val matches = graph.processText(text)
        
        assertEquals(1, matches.size)
        assertEquals(text.length, matches[0])
    }

    @Test
    fun testKmpConditionOperators() {
        // 测试加法操作符 (OR)
        val orCondition = CharCondition('a') + CharCondition('b')
        assertTrue(orCondition.matches('a'))
        assertTrue(orCondition.matches('b'))
        assertFalse(orCondition.matches('c'))
        
        // 测试乘法操作符 (AND)
        val andCondition = CharRangeCondition('a', 'z') * CharRangeCondition('m', 'z')
        assertFalse(andCondition.matches('a')) // 不在两个范围的交集中
        assertTrue(andCondition.matches('n'))  // 在两个范围的交集中
        assertFalse(andCondition.matches('0'))
        
        // 测试取反操作符
        val notCondition = !CharCondition('a')
        assertFalse(notCondition.matches('a'))
        assertTrue(notCondition.matches('b'))
    }
    
    @Test
    fun testCharOperators() {
        // 测试 Char.unaryPlus 操作符
        val charCondition = +'a'
        assertTrue(charCondition.matches('a'))
        assertFalse(charCondition.matches('b'))
        
        // 测试 or 中缀操作符
        val ignoreCase = 'a' or 'A'
        assertTrue(ignoreCase.matches('a'))
        assertTrue(ignoreCase.matches('A'))
        assertFalse(ignoreCase.matches('b'))
        
        // 测试 to 中缀操作符 (字符范围)
        val rangeCondition = 'a' to 'c'
        assertTrue(rangeCondition.matches('a'))
        assertTrue(rangeCondition.matches('b'))
        assertTrue(rangeCondition.matches('c'))
        assertFalse(rangeCondition.matches('d'))
    }
    
    @Test
    fun testKmpPatternDSL() {
        val pattern = kmpPattern {
            char('a')
            digit()
            letter()
        }
        
        assertEquals(3, pattern.conditions.size)
        
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern(pattern)
        
        graph.reset()
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('5'))
        assertTrue(graph.processChar('b'))
        
        // 测试特殊字符匹配
        val specialPattern = kmpPattern {
            charIgnoreCase('a')
        }
        
        val specialGraph = builder.buildFromPattern(specialPattern)
        specialGraph.reset()
        assertTrue(specialGraph.processChar('a') || specialGraph.processChar('A'))
    }
    
    @Test
    fun testPredefinedConditions() {
        // 测试预定义常量
        assertTrue(DIGITS.matches('0'))
        assertTrue(DIGITS.matches('9'))
        assertFalse(DIGITS.matches('a'))
        
        assertTrue(LETTERS.matches('a'))
        assertTrue(LETTERS.matches('Z'))
        assertFalse(LETTERS.matches('0'))
        
        assertTrue(ALPHANUMERIC.matches('a'))
        assertTrue(ALPHANUMERIC.matches('0'))
        assertFalse(ALPHANUMERIC.matches('#'))
        
        assertTrue(WHITESPACE.matches(' '))
        assertTrue(WHITESPACE.matches('\t'))
        assertFalse(WHITESPACE.matches('a'))
        
        assertTrue(ANY_CHAR.matches('a'))
        assertTrue(ANY_CHAR.matches('0'))
        assertTrue(ANY_CHAR.matches('#'))
    }
    
    @Test
    fun testNegationFunctions() {
        // 测试 not 函数
        val notDigit = not(DIGITS)
        assertFalse(notDigit.matches('0'))
        assertTrue(notDigit.matches('a'))
        
        // 测试 notChars 函数
        val noVowels = notChars('a', 'e', 'i', 'o', 'u')
        assertFalse(noVowels.matches('a'))
        assertTrue(noVowels.matches('b'))
        
        // 测试 notInRange 函数
        val notLowerCase = notInRange('a', 'z')
        assertFalse(notLowerCase.matches('a'))
        assertTrue(notLowerCase.matches('A'))
        assertTrue(notLowerCase.matches('0'))
    }
    
    @Test 
    fun testInversePredefinedConditions() {
        // 测试反向预定义常量
        assertFalse(NOT_DIGITS.matches('0'))
        assertTrue(NOT_DIGITS.matches('a'))
        
        assertFalse(NOT_LETTERS.matches('a'))
        assertTrue(NOT_LETTERS.matches('0'))
        
        assertFalse(NOT_ALPHANUMERIC.matches('a'))
        assertFalse(NOT_ALPHANUMERIC.matches('0'))
        assertTrue(NOT_ALPHANUMERIC.matches('#'))
        
        assertFalse(NOT_WHITESPACE.matches(' '))
        assertTrue(NOT_WHITESPACE.matches('a'))
    }
    
    @Test
    fun testKmpPatternNegationMethods() {
        val pattern = kmpPattern {
            notChar('a')
            noneOf('0', '1', '2')
            notInRange('A', 'Z')
            notDigit()
            not(WHITESPACE)
        }
        
        assertEquals(5, pattern.conditions.size)
        
        // 按照上面的条件，字符'b'应该满足所有条件
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern(pattern)
        
        graph.reset()
        assertFalse(graph.processChar('b'))  // 匹配第1个条件
        assertFalse(graph.processChar('b'))  // 匹配第2个条件
        assertFalse(graph.processChar('b'))  // 匹配第3个条件
        assertFalse(graph.processChar('b'))  // 匹配第4个条件
        assertTrue(graph.processChar('b'))   // 匹配第5个条件，是最后一个，返回true
    }
    
    @Test
    fun testComplexDSLPattern() {
        // 创建一个复杂的模式：字母+数字+非空白字符
        val pattern = kmpPattern {
            letter()
            digit()
            notWhitespace()
            any() // 匹配任何字符
        }
        
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern(pattern)
        
        // 测试正面案例："a1b!"
        graph.reset()
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('1'))
        assertFalse(graph.processChar('b'))
        assertTrue(graph.processChar('!'))
        
        // 测试反面案例："a 1!"（中间有空格）
        graph.reset()
        assertFalse(graph.processChar('a'))
        graph.reset() // 因为空格不匹配数字，所以重置
        
        // 测试复杂模式："ab123"
        val complexBuilder = StreamKmpGraphBuilder()
        val complexPattern = kmpPattern {
            char('a')
            charIgnoreCase('b')
            notChar('x')
            digit()
        }
        
        val complexGraph = complexBuilder.buildFromPattern(complexPattern)
        complexGraph.reset()
        assertFalse(complexGraph.processChar('a'))
        assertFalse(complexGraph.processChar('B')) // 大写B也匹配
        assertFalse(complexGraph.processChar('c')) // 不是'x'，所以匹配
        assertTrue(complexGraph.processChar('1'))
    }


    @Test
    fun testRepeat() {
        val pattern = kmpPattern {
            repeat(3) {
                char('a')
            }
            notChar('b')
        }
        val builder = StreamKmpGraphBuilder()
        val graph = builder.buildFromPattern(pattern)

        graph.reset()
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('b'))
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('a'))
        assertFalse(graph.processChar('a'))
        assertTrue(graph.processChar('a'))
        assertFalse(graph.processChar('b'))

    }
} 