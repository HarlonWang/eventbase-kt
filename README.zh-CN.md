# eventbase-kt

> [eventbase](https://github.com/HarlonWang/eventbase) 的 Kotlin Multiplatform 客户端——强类型事件、离线队列、批量上报。

[English](README.md) | **简体中文**

[![Maven Central](https://img.shields.io/maven-central/v/wang.harlon/eventbase-kt)](https://central.sonatype.com/artifact/wang.harlon/eventbase-kt)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**接入面只有四个调用**——`init`、`track`、`setUserId`、`startFlow`。队列、重试、安装标识、生命周期事件、自动属性全在库内部。服务端那一半是 [eventbase](https://github.com/HarlonWang/eventbase)，跑在你自己的 Cloudflare Worker 里。

## 能力

- **事件名拼不错。** 事件词汇是**你自己 App 里**的一个 sealed class，库永远不知道 `picks` 是什么。属性名是构造参数，拼错编译不过，改名就是一次全局重构。
- **弱网不丢事件。** 事件先落盘再批量上传，失败按指数退避重试。队列扛得住进程被杀，上限 500 条、满了丢最老、出队前丢弃超过 7 天的。
- **绝不采集设备标识符。** 库自己生成安装 id，不取 ANDROID_ID、不取 IDFV、什么都不取。这让 Play 数据安全、App Store 隐私标签、GDPR 申报不进你的接入清单——想要设备标识就自己传 `deviceId` 并自行申报。
- **生命周期埋点白拿。** `app_opened` 与 `app_backgrounded`（含时长）零代码上报，用的是平台自己的前台信号，而不是自己数 Activity——后者在旋转屏幕时必然切出假会话。
- **丢失查得出来。** 每个环节一行调试日志（入队、已发、留队重试），与服务端自己的丢弃计数对账，一条缺失的事件能归因到**没 track / 传丢了 / 服务端拒了**。

## 快速开始

**1. 加依赖。** HTTP engine 由你挑。

```kotlin
commonMain.dependencies { implementation("wang.harlon:eventbase-kt:<version>") }
```

**2. 启动时初始化一次。**

```kotlin
Eventbase.init(
    context = this,                          // 仅 Android 需要；iOS 无此参数
    config = EventbaseConfig(
        endpoint = "https://api.example.com/t",
        appKey = BuildConfig.EVENTBASE_KEY,   // 公开 key，进 APK 无妨
        appVersion = BuildConfig.VERSION_NAME,
        platform = "android",
        channel = BuildConfig.CHANNEL,
        locale = systemLocaleTag(),
        isDebug = BuildConfig.DEBUG,
    ),
)
```

此后安装 id、自动属性、生命周期事件、离线队列都归库自己管。

**3. 定义你自己的事件词汇。** 它住在你的 App 里，不在库里。

```kotlin
sealed class AppEvent(
    override val name: String,
    override val props: Map<String, Any?>,
) : Event {

    data class ContentOpened(val source: String, val rank: Int, val contentId: String) :
        AppEvent("content_opened", mapOf("source" to source, "rank" to rank, "content_id" to contentId))

    data class SettingChanged(val key: String, val value: String) :
        AppEvent("setting_changed", mapOf("key" to key, "value" to value))
}
```

**4. 上报。**

```kotlin
Eventbase.track(AppEvent.ContentOpened(source = "github", rank = 3, contentId = item.id))

Eventbase.setUserId(identity.id)   // 登录成功后：此后事件带 user_id
Eventbase.clearUserId()            // 登出：install_id 不变，只解除关联
```

**5. 串起一条跨进程的用户旅程。**

```kotlin
val flow = Eventbase.startFlow()
Eventbase.track(AuthStarted("sign_in", method = "github"), flow)

// 浏览器回跳之后——可能已经是一个全新的进程
Eventbase.track(AuthFinished("sign_in", "github", outcome = "success"), Eventbase.currentFlow())
```

`startFlow()` 落盘保存，于是「用户去了浏览器再也没回来」变成一件可以被量出来的事。

## 库负责的事

| | |
|---|---|
| 离线队列 | 落盘、上限 500 条、满了丢最老、出队前丢弃超过 7 天的事件 |
| flush 时机 | 进后台、攒够 `flushAt`；**刻意没有定时器**——在移动端它只换来耗电 |
| 失败重试 | 指数退避；4xx 与 204 一律出队（服务端已判定，重试无意义） |
| 安装标识 | 首次启动生成，卸载重装才变，不取任何设备标识符 |
| 自动属性 | app_version / platform / channel / locale / is_debug / session |
| 生命周期事件 | `app_opened` / `app_backgrounded`，接入方零代码（`autoLifecycle = false` 可关） |
| 测试 | `RecordingSink` 进程内收事件，不用服务端就能断言事件名与属性 |

## 文档

| | |
|---|---|
| [接入指南](docs/integration.md) | 典型场景、诊断日志、冒烟对账、测试、`installId` 与 `deviceId` |
| [上报协议](https://github.com/HarlonWang/eventbase/blob/main/docs/protocol.md) | wire 契约，住在服务端仓——唯一权威 |
| [埋点设计](https://github.com/HarlonWang/eventbase/blob/main/docs/telemetry-design.md) | 事件词汇与指标口径 |

## License

MIT
