package wang.harlon.eventbase

data class EventbaseConfig(
    val endpoint: String,
    val appKey: String,
    val appVersion: String,
    val platform: String,
    val channel: String = "unknown",
    val locale: String = "unknown",
    val isDebug: Boolean = false,
    /** 攒够这么多条就 flush；进后台也会触发（需 [autoLifecycle]）。刻意没有定时器，见 README */
    val flushAt: Int = 10,
    val logEvents: Boolean = false,
    /** 自动上报 app_opened / app_backgrounded，并在进后台时 flush */
    val autoLifecycle: Boolean = true,
    /**
     * 消费方已有的安装级标识，仅在库自己的存储里还没有 install_id 时作种子写入。
     * 用于让客户端事件与服务端按业务 install_id 补发的事件（配额拦截、成单）串得起来。
     */
    val installId: String? = null,
)

internal object Limits {
    const val QUEUE_CAP = 500
    const val BATCH = 25
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
}
