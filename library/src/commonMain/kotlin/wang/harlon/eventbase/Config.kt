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
    /**
     * 设备级标识，随每批上报透传。**库自己绝不采集**——设备标识符会牵出 Play 数据安全 /
     * App Store 隐私标签 / GDPR 的单独申报，默认带上等于让所有接入方都背上这份义务；
     * 消费方要用就自己传（并自行申报），传什么由消费方的权威源决定。
     *
     * 不传则上报体里没有这个字段。分析口径见服务端仓 `docs/telemetry-design.md`：
     * 它**不作 DAU 去重单位**，用途是与安装数相比得出重装率。
     */
    val deviceId: String? = null,
)

internal object Limits {
    const val QUEUE_CAP = 500
    const val BATCH = 25
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
}
