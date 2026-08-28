# 接入指南

README 讲最小接入，这里讲典型场景、诊断手段，以及两个配置项背后的取舍。

## installId：只作种子

```kotlin
EventbaseConfig(..., installId = existingInstallId)
```

`installId` 只在库自己的存储里还没有 id 时作**种子**写入，之后再传别的值也不会改它。

消费方若已有一个安装级标识（例如拿它当配额的 `X-Install-Id`），传进来，客户端事件才能和服务端按同一个 id 补发的事件（配额拦截、成单）串成一条漏斗；不传就由库自己生成。

## deviceId：库不采集，只透传

`deviceId`（可选）随每批上报透传。**库自己绝不采集设备标识符**——ANDROID_ID / IDFV 会牵出 Play 数据安全、App Store 隐私标签、GDPR 的单独申报，默认带上等于让所有接入方都背这份义务；而库也拿不到「正确」的那个值（ANDROID_ID 按签名密钥隔离、模拟器有固定串，IDFV 全卸载即重置），消费方侧本来就有权威源。要用就自己传并自行申报。

它**不作 DAU 去重单位**，用途是与安装数相比得出重装率——口径见服务端仓 `docs/telemetry-design.md`。

## 典型场景

### 页面浏览

```kotlin
@Composable
fun TrackScreen(screen: String, from: String? = null) {
    LaunchedEffect(screen, from) { Eventbase.track(ScreenViewed(screen, from)) }
}
```

新增页面只多一行，不新增事件名。

### 跨端漏斗

```kotlin
val flow = Eventbase.startFlow()
Eventbase.track(AuthStarted("sign_in", method = "github"), flow)

// 浏览器回跳之后（可能已跨进程重启）
Eventbase.track(AuthFinished("sign_in", "github", outcome = "success"), Eventbase.currentFlow())
```

`startFlow()` 落盘保存，进程被杀也能续上——「用户空手回到 App」那类黑洞靠它观测。`flow` 的语义与服务端 loginbase 的 `flow_id` 一致，两端在分析时合流。

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

## 诊断日志与上线前对账

debug 构建自动带 `is_debug=1`（服务端照收、分析默认过滤）。`logEvents = true` 打开诊断日志：

```bash
adb logcat -s eventbase:D      # iOS 侧走 NSLog，Console.app 里搜 [eventbase]
```

三类日志，合起来能把「丢在哪一段」定位出来：

```
track content_opened {source=github, rank=3}      入队
POST /e -> 204 (3 events)                          服务端回了什么
flush 3 sent, queued=0                             出队，队列剩多少
flush 3 kept, retry in 5000ms, queued=3            留队，下次重试间隔
```

**为什么需要它**：服务端只知道「收到了什么」，与客户端「本来要发什么」之差就是丢失，但分不清是没 track 还是传丢了。logcat 补的正是这一段；服务端那侧由 `ingest_drops`（按天按原因记账）补主动丢弃。三者对齐才能把丢失归因到 **没 track / 传丢了 / 服务端拒了**。

冒烟演练（断网点 5 下 → 恢复）应看到：5 条 track、`flush 5 kept`、`queued=5`；恢复后 `POST /e -> 204`、`flush 5 sent`、`queued=0`；最后用取数接口查出这 5 条。**三处数字一致才算通过。**

## 生命周期

Android 侧会自动注册 `ActivityLifecycleCallbacks`、iOS 侧注册 `NSNotificationCenter` 观察者，`app_opened` / `app_backgrounded` 无需接入方写一行代码（`autoLifecycle = false` 可关）。

Android 用的是 `ProcessLifecycleOwner` 而非自己数 Activity：自己数数不出配置变更（旋转时 started 计数归零再加一，必然切出假会话），它内置的 700ms 去抖是唯一正确的口径来源。

## 与 loginbase-kt 的关系

**不依赖**。登录相关的客户端事件由 App 在自己的 auth 回调里上报，loginbase-kt 不感知埋点；服务端那一半由 loginbase 写进同一张表，两端靠 `flow_id` 合流。
