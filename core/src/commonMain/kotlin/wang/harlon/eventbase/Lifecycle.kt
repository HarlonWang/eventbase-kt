package wang.harlon.eventbase

internal data class AppOpened(val isCold: Boolean) : Event {
    override val name = "app_opened"
    override val props = mapOf("is_cold" to isCold)
}

internal data class AppBackgrounded(val durationSeconds: Long, val isWake: Boolean) : Event {
    override val name = "app_backgrounded"
    override val props = mapOf("duration_s" to durationSeconds, "is_wake" to isWake)
}

/**
 * 会话口径住在库里，不留给每个接入方自己判断——TrendingAI 1.2.0 把日活推高 55%
 * 就是因为后台唤醒起的无界面进程也被算成了会话。
 *
 * 平台侧只负责把「进前台 / 进后台」两个信号送进来。
 */
internal class LifecycleTracker(
    private val client: EventbaseClient,
    private val clock: Clock,
    private val onBackgroundFlush: () -> Unit,
) {
    private var foregroundAt = 0L
    private var opened = false
    private var wokenInBackground = false

    fun onForeground() {
        if (!opened) {
            opened = true
            wokenInBackground = client.trackedAnything
            client.track(AppOpened(isCold = true))
        }
        foregroundAt = clock.now()
    }

    fun onBackground() {
        if (foregroundAt == 0L) return
        val seconds = (clock.now() - foregroundAt) / 1000
        foregroundAt = 0L
        if (seconds > 0) client.track(AppBackgrounded(seconds, wokenInBackground))
        onBackgroundFlush()
    }
}
