package wang.harlon.eventbase

import platform.Foundation.NSLog

internal actual fun logLine(message: String) {
    NSLog("[eventbase] %s", message)
}
