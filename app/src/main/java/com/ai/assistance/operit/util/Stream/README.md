# Stream库

Stream是一个类似Kotlin Flow的轻量级异步数据流处理库。它提供了丰富的操作符和工具，用于处理异步数据流。

## 基本概念

- `Stream<T>` - 异步数据流的主要接口
- `SharedStream<T>` - 可共享的热流，类似于SharedFlow
- `StateStream<T>` - 带状态的热流，类似于StateFlow

## 主要功能

### 创建Stream

```kotlin
// 从单个值创建Stream
val stream = streamOf(1)

// 从多个值创建Stream
val stream = streamOf(1, 2, 3, 4, 5)

// 从集合创建Stream
val list = listOf(1, 2, 3, 4, 5)
val stream = list.asStream()

// 自定义构建Stream
val stream = stream<Int> {
    for (i in 1..5) {
        emit(i)
        delay(1000)
    }
}

// 创建范围Stream
val stream = rangeStream(1, 5) // 创建包含1到5的Stream

// 创建间隔Stream
val stream = intervalStream(Duration.seconds(1)) // 每秒发射一次
```

### 操作Stream

```kotlin
// 映射操作
val stream = streamOf(1, 2, 3, 4, 5)
    .map { it * 2 } // 结果: 2, 4, 6, 8, 10

// 过滤操作
val stream = streamOf(1, 2, 3, 4, 5)
    .filter { it % 2 == 0 } // 结果: 2, 4

// 限制数量
val stream = streamOf(1, 2, 3, 4, 5)
    .take(3) // 结果: 1, 2, 3

// 转换操作
val stream = streamOf(1, 2, 3)
    .flatMap { num -> 
        streamOf("a$num", "b$num") 
    } // 结果: a1, b1, a2, b2, a3, b3
```

### 分块/分组 Stream (Chunking / Grouping)

```kotlin
// 将Stream中的元素按固定大小分块
val stream = streamOf(1, 2, 3, 4, 5, 6, 7)
    .chunked(3)
// 上述操作会产生一个Stream，它依次发射:
// listOf(1, 2, 3)
// listOf(4, 5, 6)
// listOf(7)

stream.collect { group ->
    println("Group: $group")
}
```

### 合并Stream

```kotlin
// 合并两个Stream
val stream1 = streamOf(1, 2, 3)
val stream2 = streamOf(4, 5, 6)
val merged = stream1.merge(stream2) // 结果可能是: 1, 4, 2, 5, 3, 6（顺序不确定）

// 连接Stream
val concat = stream1.concatWith(stream2) // 结果: 1, 2, 3, 4, 5, 6（顺序确定）

// 组合Stream
val stream1 = intervalStream(Duration.seconds(1))
val stream2 = intervalStream(Duration.seconds(1))
val combined = stream1.combine(stream2) { a, b -> a + b }
```

### 热流

```kotlin
// 创建共享Stream
val sharedStream = MutableSharedStream<Int>(replay = 3)
sharedStream.emit(1)
sharedStream.emit(2)

// 创建状态Stream
val stateStream = MutableStateStream(0)
stateStream.value = 1

// 将普通Stream转为热Stream
val coldStream = streamOf(1, 2, 3, 4, 5)
val hotStream = coldStream.share(
    scope = viewModelScope,
    replay = 2,
    started = StreamStart.EAGERLY
)
```

### 错误处理

```kotlin
// 捕获异常
val stream = streamOf(1, 2, 3)
    .map { 
        if (it == 2) throw Exception("Error at 2")
        it
    }
    .catch { e ->
        println("Caught: ${e.message}")
    }

// finally模式
val stream = streamOf(1, 2, 3)
    .finally {
        println("Stream completed")
    }
```

### 与Kotlin Flow的互操作

```kotlin
// 将Flow转换为Stream
val flow: Flow<Int> = flow { emit(1) }
val stream: Stream<Int> = flow.asStream()

// 将Stream转换为Flow
val originalStream = streamOf(1, 2, 3)
val flow: Flow<Int> = originalStream.asFlow()
```

## 最佳实践

- 对于短暂的数据转换，使用冷流（普通Stream）
- 对于需要在多个收集点共享的数据，使用SharedStream
- 对于表示状态的数据，使用StateStream
- 总是处理异常，使用`catch`操作符
- 及时取消不再需要的Stream收集

## 示例：HTTP请求结果作为Stream返回

```kotlin
fun getDataAsStream(url: String): Stream<Data> = stream {
    try {
        val response = httpClient.get(url)
        val data = response.body<Data>()
        emit(data)
    } catch (e: Exception) {
        throw e  // 允许调用方使用catch操作符处理错误
    }
}

// 使用
getDataAsStream("https://api.example.com/data")
    .catch { e -> emit(Data.Error(e.message)) }
    .collect { data ->
        // 处理数据
    }
```

## 示例：UI事件作为Stream

```kotlin
class ViewModel {
    private val _events = MutableSharedStream<UiEvent>()
    val events: SharedStream<UiEvent> = _events
    
    fun onButtonClick() {
        viewModelScope.launch {
            _events.emit(UiEvent.ButtonClicked)
        }
    }
}

// 在UI中收集
viewModel.events.collect { event ->
    when (event) {
        is UiEvent.ButtonClicked -> showToast("Button clicked")
    }
}
``` 