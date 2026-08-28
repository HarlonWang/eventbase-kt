package wang.harlon.eventbase

internal object AppOpened : Event {
    override val name = "app_opened"
}

internal data class AppBackgrounded(val durationSeconds: Long, val isWake: Boolean) : Event {
    override val name = "app_backgrounded"
    override val props = mapOf("duration_s" to durationSeconds, "is_wake" to isWake)
}

/**
 * 会话口径住在库里，不留给每个接入方自己判断：后台唤醒起的无界面进程若被算成会话，
 * 日活会被显著推高，而这是每个接入方都会各踩一次的坑。
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
            client.track(AppOpened)
        }
        if (foregroundAt == 0L) foregroundAt = clock.now()
    }

    fun onBackground() {
        if (foregroundAt != 0L) {
            val seconds = (clock.now() - foregroundAt) / 1000
            foregroundAt = 0L
            if (seconds > 0) client.track(AppBackgrounded(seconds, wokenInBackground))
        }
        // 无论有没有前台区间都要 flush：后台唤醒进程里攒的事件否则要等到下次启动
        onBackgroundFlush()
    }
}
