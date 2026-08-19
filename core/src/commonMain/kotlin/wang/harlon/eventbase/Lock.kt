package wang.harlon.eventbase

/** 队列与落盘的串行化原语。common 没有 `synchronized`，故按平台实现。 */
internal expect class Lock() {
    fun <T> withLock(block: () -> T): T
}
