package wang.harlon.eventbase

/**
 * 队列与落盘的串行化原语。common 没有 `synchronized`，故按平台实现。
 * 用接口 + expect 工厂函数而非 `expect class`：后者仍是 Beta，会带一条编译警告。
 */
internal interface Lock {
    fun <T> withLock(block: () -> T): T
}

internal expect fun createLock(): Lock
