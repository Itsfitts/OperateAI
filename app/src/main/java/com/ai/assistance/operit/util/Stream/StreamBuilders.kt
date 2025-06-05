package com.ai.assistance.operit.util.Stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * 创建一个空的Stream
 */
fun <T> emptyStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("emptyStream", "收集空Stream")
        // 不发射任何值
    }
}

/**
 * 从单个值创建Stream
 */
fun <T> streamOf(value: T): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("streamOf", "创建单值Stream: $value")
        collector.emit(value)
    }
}

/**
 * 从多个值创建Stream
 */
fun <T> streamOf(vararg values: T): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("streamOf", "创建多值Stream, 元素数量: ${values.size}")
        for (value in values) {
            StreamLogger.v("streamOf", "发射元素: $value")
            collector.emit(value)
        }
    }
}

/**
 * 从集合创建Stream
 */
fun <T> Collection<T>.asStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("Collection.asStream", "从集合创建Stream, 元素数量: ${this@asStream.size}")
        for (item in this@asStream) {
            StreamLogger.v("Collection.asStream", "发射元素: $item")
            collector.emit(item)
        }
    }
}

/**
 * 从序列创建Stream
 */
fun <T> Sequence<T>.asStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("Sequence.asStream", "从序列创建Stream")
        var count = 0
        for (item in this@asStream) {
            count++
            StreamLogger.v("Sequence.asStream", "发射元素[$count]: $item")
            collector.emit(item)
        }
        StreamLogger.d("Sequence.asStream", "序列Stream收集完成, 共$count 个元素")
    }
}

/**
 * 通过调用构建器创建Stream
 */
fun <T> stream(block: suspend StreamCollector<T>.() -> Unit): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("stream", "开始构建器创建的Stream收集")
        try {
            block(collector)
            StreamLogger.d("stream", "构建器Stream收集完成")
        } catch (e: Exception) {
            StreamLogger.e("stream", "构建器Stream收集出错", e)
            throw e
        }
    }
}

/**
 * 创建固定间隔发射整数的Stream
 */
fun intervalStream(period: Duration, initialDelay: Duration = Duration.ZERO): Stream<Long> = stream {
    var count = 0L
    StreamLogger.d("intervalStream", "创建间隔Stream, 周期: $period, 初始延迟: $initialDelay")
    delay(initialDelay)
    while (true) {
        StreamLogger.v("intervalStream", "发射计数: $count")
        emit(count++)
        delay(period)
    }
}

/**
 * 创建固定次数的Stream
 */
fun rangeStream(start: Int, count: Int): Stream<Int> = stream {
    StreamLogger.d("rangeStream", "创建范围Stream, 起始: $start, 数量: $count")
    for (i in start until start + count) {
        StreamLogger.v("rangeStream", "发射值: $i")
        emit(i)
    }
    StreamLogger.d("rangeStream", "范围Stream完成")
}

/**
 * 从异常创建Stream
 */
fun <T> streamError(exception: Throwable): Stream<T> = stream {
    StreamLogger.e("streamError", "创建错误Stream, 异常: ${exception.message}", exception)
    throw exception
} 