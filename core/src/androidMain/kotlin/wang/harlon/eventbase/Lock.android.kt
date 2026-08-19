package wang.harlon.eventbase

import java.util.concurrent.locks.ReentrantLock

internal actual class Lock actual constructor() {
    private val delegate = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T {
        delegate.lock()
        try {
            return block()
        } finally {
            delegate.unlock()
        }
    }
}
