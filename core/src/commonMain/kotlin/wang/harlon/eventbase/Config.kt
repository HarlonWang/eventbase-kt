package wang.harlon.eventbase

data class EventbaseConfig(
    val endpoint: String,
    val appKey: String,
    val appVersion: String,
    val platform: String,
    val channel: String = "unknown",
    val locale: String = "unknown",
    val isDebug: Boolean = false,
    /** 攒够这么多条就 flush；进后台与定时也会触发 */
    val flushAt: Int = 10,
    val logEvents: Boolean = false,
    /** 自动上报 app_opened / app_backgrounded，并在进后台时 flush */
    val autoLifecycle: Boolean = true,
)

internal object Limits {
    const val QUEUE_CAP = 500
    const val BATCH = 25
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
}
