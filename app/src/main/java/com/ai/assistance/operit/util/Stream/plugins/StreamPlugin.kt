package com.ai.assistance.operit.util.Stream.plugins

/**
 * 流处理插件接口
 * 用于实现各种针对字符流的处理逻辑
 */
interface StreamPlugin {
    /**
     * 当前插件是否处于处理状态
     */
    val isProcessing: Boolean
    
    /**
     * 当前插件是否正在尝试开始处理
     */
    val isTryingToStart: Boolean
    
    /**
     * 处理单个字符
     * @param c 要处理的字符
     * @return 是否消费了该字符
     */
    fun processChar(c: Char): Boolean
    
    /**
     * 初始化插件
     * @return 是否初始化成功
     */
    fun initPlugin(): Boolean
    
    /**
     * 销毁插件，释放资源
     */
    fun destroy()
    
    /**
     * 重置插件状态
     */
    fun reset()
} 