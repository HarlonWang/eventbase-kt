package wang.harlon.eventbase

import platform.Foundation.NSRecursiveLock

internal actual fun createLock(): Lock = NSRecursiveLockAdapter()

private class NSRecursiveLockAdapter : Lock {
    private val delegate = NSRecursiveLock()

    override fun <T> withLock(block: () -> T): T {
        delegate.lock()
        try {
            return block()
        } finally {
            delegate.unlock()
        }
    }
}
