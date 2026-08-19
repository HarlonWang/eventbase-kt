package wang.harlon.eventbase

import platform.Foundation.NSRecursiveLock

internal actual class Lock actual constructor() {
    private val delegate = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        delegate.lock()
        try {
            return block()
        } finally {
            delegate.unlock()
        }
    }
}
