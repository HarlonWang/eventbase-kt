package wang.harlon.eventbase

import java.util.concurrent.locks.ReentrantLock

internal actual fun createLock(): Lock = ReentrantLockAdapter()

private class ReentrantLockAdapter : Lock {
    private val delegate = ReentrantLock()

    override fun <T> withLock(block: () -> T): T {
        delegate.lock()
        try {
            return block()
        } finally {
            delegate.unlock()
        }
    }
}
