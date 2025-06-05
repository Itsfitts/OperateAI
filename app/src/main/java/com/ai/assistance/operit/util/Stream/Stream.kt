package com.ai.assistance.operit.util.Stream

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

/**
 * 日志工具类，统一Stream相关日志
 */
object StreamLogger {
    private const val TAG = "StreamFramework"
    private var enabled = true
    private var verboseEnabled = false
    
    /**
     * 启用或禁用日志
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    /**
     * 启用或禁用详细日志
     */
    fun setVerboseEnabled(enabled: Boolean) {
        this.verboseEnabled = enabled
    }
    
    /**
     * 记录调试信息
     */
    fun d(component: String, message: String) {
        if (enabled) {
            Log.d(TAG, "[$component] $message")
        }
    }
    
    /**
     * 记录信息
     */
    fun i(component: String, message: String) {
        if (enabled) {
            Log.i(TAG, "[$component] $message")
        }
    }
    
    /**
     * 记录详细信息
     */
    fun v(component: String, message: String) {
        if (enabled && verboseEnabled) {
            Log.v(TAG, "[$component] $message")
        }
    }
    
    /**
     * 记录警告
     */
    fun w(component: String, message: String) {
        if (enabled) {
            Log.w(TAG, "[$component] $message")
        }
    }
    
    /**
     * 记录错误
     */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.e(TAG, "[$component] $message", throwable)
            } else {
                Log.e(TAG, "[$component] $message")
            }
        }
    }
}

/** Stream接口，类似于Kotlin Flow，用于表示异步计算的数据流 */
interface Stream<T> {
    /** 收集Stream发出的值 */
    suspend fun collect(collector: StreamCollector<T>)

    /** Stream的标准收集方法，简化使用 */
    suspend fun collect(onEach: suspend (T) -> Unit) {
        StreamLogger.d("Stream", "开始收集Stream元素")
        collect(
                object : StreamCollector<T> {
                    override suspend fun emit(value: T) {
                        StreamLogger.v("Stream", "收集到元素: $value")
                        onEach(value)
                    }
                }
        )
        StreamLogger.d("Stream", "完成Stream收集")
    }
}

/** Stream收集器接口，类似于FlowCollector */
interface StreamCollector<in T> {
    /** 发射一个值到Stream */
    suspend fun emit(value: T)
}

/** Flow到Stream的适配器，允许将Kotlin Flow转换为Stream */
class FlowAsStream<T>(private val flow: Flow<T>) : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("FlowAsStream", "开始从Flow收集元素")
        flow.collect { value -> 
            StreamLogger.v("FlowAsStream", "从Flow收集到元素: $value")
            collector.emit(value) 
        }
        StreamLogger.d("FlowAsStream", "完成从Flow收集元素")
    }
}

/** Stream到Flow的适配器，允许将Stream转换为Kotlin Flow */
class StreamAsFlow<T>(private val stream: Stream<T>) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        StreamLogger.d("StreamAsFlow", "开始转换Stream到Flow")
        stream.collect { value -> 
            StreamLogger.v("StreamAsFlow", "转换元素到Flow: $value")
            collector.emit(value) 
        }
        StreamLogger.d("StreamAsFlow", "完成转换Stream到Flow")
    }
}

/** 将Flow转换为Stream */
fun <T> Flow<T>.asStream(): Stream<T> = FlowAsStream(this)

/** 将Stream转换为Flow */
fun <T> Stream<T>.asFlow(): Flow<T> = StreamAsFlow(this)

/** 在指定的协程作用域中启动Stream收集 */
fun <T> Stream<T>.launchIn(scope: CoroutineScope, onEach: suspend (T) -> Unit = {}): Job {
    StreamLogger.d("Stream.launchIn", "在协程作用域中启动Stream收集")
    return scope.launch { 
        collect { value -> 
            StreamLogger.v("Stream.launchIn", "收集到元素: $value")
            onEach(value) 
        } 
    }
}
