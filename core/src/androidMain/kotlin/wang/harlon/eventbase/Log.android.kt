package wang.harlon.eventbase

import android.util.Log

/** runCatching 兜底：android.util.Log 在 JVM 宿主单测里未 mock，会抛「not mocked」。 */
internal actual fun logLine(message: String) {
    runCatching { Log.d("eventbase", message) }
}
