# eventbase-kt

> Kotlin Multiplatform client for [eventbase](https://github.com/HarlonWang/eventbase) — typed events, offline queue, batched upload.

强类型事件定义、落盘队列、批量上报、生命周期自动埋点。协议契约以服务端仓的 [`docs/protocol.md`](https://github.com/HarlonWang/eventbase/blob/main/docs/protocol.md) 为唯一权威。

> ⚠️ **状态：建仓阶段，实现未开始（2026-08-18）。** 本文描述的是已定稿的接入形态与 API 意图，代码尚未落地。

## 接入面只有 4 个 API

`init` / `track` / `setUserId` / `startFlow`。其余（队列、重试、install_id、生命周期、自动属性）全在库内部。

## 1. 依赖与初始化

```kotlin
// shared/build.gradle.kts
commonMain.dependencies { implementation("wang.harlon:eventbase-kt:0.1.0") }
```

传递依赖只有 ktor-client-core + kotlinx-serialization-json + kotlinx-coroutines-core（engine 由消费方提供）。

```kotlin
// androidApp/TrendingApplication.onCreate()
Eventbase.init(
    context = this,                          // 仅 Android 需要；iOS 无此参数
    config = EventbaseConfig(
        endpoint = "https://api.trendingai.cn/t",
        appKey = BuildConfig.EVENTBASE_KEY,  // 公开 key，进 APK 无妨
        channel = BuildConfig.CHANNEL,
        isDebug = BuildConfig.DEBUG,
    ),
)
```

`init` 之后库自己接管 install_id 的生成与持久化、app_version / platform / locale 的采集、生命周期事件、离线队列。

## 2. 定义自己的事件词汇

库只认一个接口，**词汇住在 App 里**——词汇是 App 特有的，库不该知道 `picks` 是什么。

```kotlin
sealed class AppEvent(
    override val name: String,
    override val props: Map<String, Any?>,
) : Event {

    data class ContentOpened(
        val source: String, val rank: Int, val contentId: String,
        val title: String, val section: String? = null,
    ) : AppEvent("content_opened", mapOf(
        "source" to source, "rank" to rank, "content_id" to contentId,
        "title" to title.take(60), "section" to section,
    ))

    data class SettingChanged(val key: String, val value: String) :
        AppEvent("setting_changed", mapOf("key" to key, "value" to value))
}

Eventbase.track(AppEvent.ContentOpened(source = "github", rank = 3, contentId = item.id, title = item.title))
```

拼错属性名编译不过，改名就是一次全局重构。

## 3. 典型场景

### 页面浏览

```kotlin
@Composable
fun TrackScreen(screen: String, from: String? = null) {
    LaunchedEffect(screen, from) { Eventbase.track(ScreenViewed(screen, from)) }
}
```

新增页面只多一行，不新增事件名。

### 跨端漏斗（OAuth 回跳）

```kotlin
val flow = Eventbase.startFlow()
Eventbase.track(AuthStarted("sign_in", method = "github"), flow)

// 浏览器回跳之后（可能已跨进程重启）
Eventbase.track(AuthFinished("sign_in", "github", outcome = "success"), Eventbase.currentFlow())
```

`startFlow()` 落盘保存，进程被杀也能续上——「用户空手回到 App」那类黑洞靠它观测。`flow` 的语义与服务端 loginbase 的 `flow_id` 一致，两端在分析时合流。

### 登录态关联

```kotlin
Eventbase.setUserId(identity.id)   // 登录成功后：此后事件带 user_id，并触发一次 install↔identity 映射
Eventbase.clearUserId()            // 登出：install_id 不变，只解除关联
```

### 后台唤醒 / Alarm 里上报

```kotlin
Eventbase.track(NotificationOpened(kind = "daily_picks"))
```

先落盘再发，不必为上传留窗口。库只在**首次进入前台**才发 `app_opened`，纯后台唤醒起的进程不构成会话。

### 测试

```kotlin
val recorder = RecordingSink()
Eventbase.initForTest(sink = recorder)

viewModel.onTabSelected(HomeTab.ME)

assertEquals(listOf("tab_switched"), recorder.names)
assertEquals(mapOf("tab" to "me", "method" to "tap"), recorder.propsOf("tab_switched"))
```

### 调试

debug 构建自动带 `is_debug=1`（服务端照收、分析默认过滤）；`logEvents = true` 把每条事件打进日志。

## 4. 库负责的事

| 能力 | 行为 |
|---|---|
| 离线队列 | 落盘、上限 500 条、满了丢最老、出队前丢弃超过 7 天的事件 |
| flush 时机 | 进后台、攒够阈值、或定时；进程被杀不丢已入队事件 |
| 失败重试 | 指数退避；4xx 与 204 一律出队（服务端已判定，重试无意义） |
| install_id | 首次启动生成，卸载重装才变，**不取任何设备标识符** |
| 自动属性 | app_version / platform / channel / sys_locale / is_debug / session |
| 生命周期事件 | `app_opened` / `app_backgrounded`（含 `duration_s`），接入方零代码 |

## 5. 与 loginbase-kt 的关系

**不依赖**。登录相关的客户端事件由 App 在自己的 auth 回调里上报，loginbase-kt 不感知埋点；服务端那一半由 loginbase 写进同一张表，两端靠 `flow_id` 合流。
