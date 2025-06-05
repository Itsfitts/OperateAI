package com.ai.assistance.operit.util.Stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import com.ai.assistance.operit.util.Stream.plugins.*

/**
 * 将Stream中的元素转换为另一种类型
 */
fun <T, R> Stream<T>.map(transform: suspend (T) -> R): Stream<R> = stream {
    collect { value ->
        emit(transform(value))
    }
}

/**
 * 过滤Stream中的元素
 */
fun <T> Stream<T>.filter(predicate: suspend (T) -> Boolean): Stream<T> = stream {
    collect { value ->
        if (predicate(value)) {
            emit(value)
        }
    }
}

/**
 * 限制Stream发射的元素数量
 */
fun <T> Stream<T>.take(count: Int): Stream<T> = stream {
    var remaining = count
    collect { value ->
        if (remaining > 0) {
            emit(value)
            remaining--
        }
    }
}

/**
 * 丢弃Stream前n个元素
 */
fun <T> Stream<T>.drop(count: Int): Stream<T> = stream {
    var dropped = 0
    collect { value ->
        if (dropped < count) {
            dropped++
        } else {
            emit(value)
        }
    }
}

/**
 * 转换Stream中的每个元素，可以发出多个值
 */
fun <T, R> Stream<T>.flatMap(transform: suspend (T) -> Stream<R>): Stream<R> = stream {
    collect { value ->
        transform(value).collect { transformedValue ->
            emit(transformedValue)
        }
    }
}

/**
 * 对Stream中的每个元素执行操作，并继续传递原始值
 */
fun <T> Stream<T>.onEach(action: suspend (T) -> Unit): Stream<T> = stream {
    collect { value ->
        action(value)
        emit(value)
    }
}

/**
 * 添加超时限制
 */
fun <T> Stream<T>.timeout(timeout: Duration): Stream<T> = stream {
    var lastEmissionTime = System.currentTimeMillis()
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime > timeout.inWholeMilliseconds) {
            throw TimeoutException("Stream timed out after $timeout")
        }
        lastEmissionTime = currentTime
        emit(value)
    }
}

/**
 * 合并两个Stream
 */
fun <T> Stream<T>.merge(other: Stream<T>): Stream<T> = stream {
    coroutineScope {
        launch {
            this@merge.collect { value ->
                emit(value)
            }
        }
        launch {
            other.collect { value ->
                emit(value)
            }
        }
    }
}

/**
 * 组合两个Stream的值
 */
fun <T1, T2, R> Stream<T1>.combine(other: Stream<T2>, transform: suspend (T1, T2) -> R): Stream<R> = stream {
    val latest1 = mutableListOf<T1>()
    val latest2 = mutableListOf<T2>()
    val channel = Channel<Pair<T1?, T2?>>(Channel.BUFFERED)
    
    coroutineScope {
        launch {
            this@combine.collect { value ->
                val latest2Value: T2? = synchronized(latest2) {
                    if (latest2.isNotEmpty()) latest2.last() else null
                }
                
                synchronized(latest1) {
                    if (latest1.isNotEmpty()) latest1.clear()
                    latest1.add(value)
                }
                
                if (latest2Value != null) {
                    channel.send(value to latest2Value)
                }
            }
        }
        
        launch {
            other.collect { value ->
                val latest1Value: T1? = synchronized(latest1) {
                    if (latest1.isNotEmpty()) latest1.last() else null
                }
                
                synchronized(latest2) {
                    if (latest2.isNotEmpty()) latest2.clear()
                    latest2.add(value)
                }
                
                if (latest1Value != null) {
                    channel.send(latest1Value to value)
                }
            }
        }
        
        launch {
            for (pair in channel) {
                emit(transform(pair.first!!, pair.second!!))
            }
        }
    }
}

/**
 * 连接两个Stream，第一个完成后再收集第二个
 */
fun <T> Stream<T>.concatWith(other: Stream<T>): Stream<T> = stream {
    this@concatWith.collect { value ->
        emit(value)
    }
    other.collect { value ->
        emit(value)
    }
}

/**
 * 捕获Stream中的异常
 */
fun <T> Stream<T>.catch(action: suspend (Throwable) -> Unit): Stream<T> = stream {
    try {
        this@catch.collect { value ->
            emit(value)
        }
    } catch (e: Throwable) {
        if (e is CancellationException) {
            throw e
        }
        action(e)
    }
}

/**
 * 无论Stream是否正常完成或发生异常，都执行指定操作
 */
fun <T> Stream<T>.finally(action: suspend () -> Unit): Stream<T> = stream {
    try {
        this@finally.collect { value ->
            emit(value)
        }
    } finally {
        action()
    }
}

/**
 * 节流操作，限制发射频率
 */
fun <T> Stream<T>.throttleFirst(windowDuration: Duration): Stream<T> = stream {
    var lastEmitTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmitTime >= windowDuration.inWholeMilliseconds) {
            lastEmitTime = currentTime
            emit(value)
        }
    }
}

/**
 * 去除重复元素
 */
fun <T> Stream<T>.distinctUntilChanged(): Stream<T> = stream {
    var lastValue: Any? = NoValue
    collect { value ->
        if (lastValue === NoValue || lastValue != value) {
            lastValue = value
            emit(value)
        }
    }
}

/**
 * 将流中的元素按指定大小分块 (分组)。
 *
 * @param size 每个块的大小。
 * @return 一个新的Stream，它发出元素列表 (块)。
 * @throws IllegalArgumentException 如果 `size` 不是正数。
 */
fun <T> Stream<T>.chunked(size: Int): Stream<List<T>> {
    require(size > 0) { "Size must be positive." }
    return stream {
        val currentChunk = mutableListOf<T>()
        this@chunked.collect { value ->
            currentChunk.add(value)
            if (currentChunk.size == size) {
                emit(currentChunk.toList()) // Emit a copy of the chunk
                currentChunk.clear()
            }
        }
        // Emit the last chunk if it's not empty
        if (currentChunk.isNotEmpty()) {
            emit(currentChunk.toList())
        }
    }
}


/**
 * 用于表示没有值的标记对象
 */
private object NoValue

/**
 * Take操作完成时抛出的异常
 */
private class TakeCompletedException : Exception()

/**
 * 超时异常
 */
class TimeoutException(message: String) : Exception(message) 