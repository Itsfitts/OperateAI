package com.ai.assistance.operit.util.Stream

/**
 * StreamGroup represents a grouped stream of data with an associated tag.
 * It encapsulates a Pair<TAG, Stream<String>> and provides convenient methods
 * to access the tag and stream.
 *
 * @param TAG The type of the tag used to identify this stream group
 * @property tag The tag identifying this stream group
 * @property stream The stream of string data in this group
 */
class StreamGroup<TAG>(
    val tag: TAG,
    val stream: Stream<String>
) {
    /**
     * Collect the values from this group's stream
     * 
     * @param collector The function to be called for each emitted value
     */
    suspend fun collect(collector: suspend (String) -> Unit) {
        StreamLogger.d("StreamGroup", "开始收集组[$tag]的元素")
        stream.collect { value ->
            StreamLogger.v("StreamGroup", "组[$tag]收集到元素: $value")
            collector(value)
        }
        StreamLogger.d("StreamGroup", "完成组[$tag]的收集")
    }
    
    /**
     * Collect the values from this group's stream using a StreamCollector
     * 
     * @param collector The StreamCollector to collect values
     */
    suspend fun collect(collector: StreamCollector<String>) {
        StreamLogger.d("StreamGroup", "开始使用StreamCollector收集组[$tag]的元素")
        stream.collect(collector)
    }

    /**
     * Convert this stream group to a Pair of tag and stream
     * 
     * @return A Pair of the tag and stream
     */
    fun toPair(): Pair<TAG, Stream<String>> = tag to stream
    
    override fun toString(): String = "StreamGroup(tag=$tag)"
}

/**
 * Extension function to create a StreamGroup from a Pair<TAG, Stream<String>>
 */
fun <TAG> Pair<TAG, Stream<String>>.asStreamGroup(): StreamGroup<TAG> = 
    StreamGroup(first, second) 