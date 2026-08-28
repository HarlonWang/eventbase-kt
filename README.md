# eventbase-kt

> Kotlin Multiplatform client for [eventbase](https://github.com/HarlonWang/eventbase) — typed events, an offline queue, and batched upload.

**English** | [简体中文](README.zh-CN.md)

[![Maven Central](https://img.shields.io/maven-central/v/wang.harlon/eventbase-kt)](https://central.sonatype.com/artifact/wang.harlon/eventbase-kt)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**The whole surface is four calls** — `init`, `track`, `setUserId`, `startFlow`. Queueing, retries, install identity, lifecycle events and automatic properties all stay inside the library. The server half is [eventbase](https://github.com/HarlonWang/eventbase), which runs in your own Cloudflare Worker.

## What you get

- **Events that can't be misspelled.** Your event vocabulary is a sealed class in *your* app — the library never learns what a "pick" is. Property names are constructor parameters, so a typo is a compile error and a rename is a refactor.
- **Nothing is lost to a bad connection.** Events go to disk first, then upload in batches with exponential backoff. The queue survives process death, caps at 500, drops oldest first, and discards anything older than seven days.
- **No device identifiers, ever.** The library generates its own install id and collects no ANDROID_ID, no IDFV, nothing. That keeps Play Data Safety, App Store privacy labels and GDPR declarations off your integration checklist — pass your own `deviceId` if you want one, and declare it yourself.
- **Lifecycle analytics for free.** `app_opened` and `app_backgrounded` (with duration) are reported without a line of integration code, using the platform's own foreground signal rather than a hand-rolled Activity count that miscounts on rotation.
- **Losses are diagnosable.** A one-line-per-stage debug log — queued, sent, kept for retry — reconciles against the server's own drop counters, so a missing event resolves to *never tracked*, *lost in transit*, or *refused by the server*.

## Quick start

**1. Add the dependency.** The HTTP engine is yours to choose.

```kotlin
commonMain.dependencies { implementation("wang.harlon:eventbase-kt:<version>") }
```

**2. Initialize once, at startup.**

```kotlin
Eventbase.init(
    context = this,                          // Android only; iOS has no such parameter
    config = EventbaseConfig(
        endpoint = "https://api.example.com/t",
        appKey = BuildConfig.EVENTBASE_KEY,   // public key; shipping it in the APK is fine
        appVersion = BuildConfig.VERSION_NAME,
        platform = "android",
        channel = BuildConfig.CHANNEL,
        locale = systemLocaleTag(),
        isDebug = BuildConfig.DEBUG,
    ),
)
```

From here the library owns install id, automatic properties, lifecycle events and the offline queue.

**3. Define your vocabulary.** It lives in your app, not in the library.

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

**4. Track.**

```kotlin
Eventbase.track(AppEvent.ContentOpened(source = "github", rank = 3, contentId = item.id))

Eventbase.setUserId(identity.id)   // after sign-in; later events carry user_id
Eventbase.clearUserId()            // on sign-out; the install id is unchanged
```

**5. Follow one user journey across a process death.**

```kotlin
val flow = Eventbase.startFlow()
Eventbase.track(AuthStarted("sign_in", method = "github"), flow)

// after the browser comes back — possibly in a brand new process
Eventbase.track(AuthFinished("sign_in", "github", outcome = "success"), Eventbase.currentFlow())
```

`startFlow()` is persisted, so "the user went to the browser and never came back" becomes something you can actually measure.

## What the library handles

| | |
|---|---|
| Offline queue | On disk, capped at 500, oldest dropped first, events older than 7 days discarded on the way out |
| Flush timing | On backgrounding, and whenever `flushAt` events have accumulated — **deliberately no timer**, which on mobile only buys battery drain |
| Retries | Exponential backoff; both 4xx and 204 dequeue, because the server has already decided |
| Install identity | Generated on first launch, changes only on reinstall, derived from no device identifier |
| Automatic properties | app_version, platform, channel, locale, is_debug, session |
| Lifecycle events | `app_opened` / `app_backgrounded`, zero integration code (`autoLifecycle = false` opts out) |
| Testing | `RecordingSink` captures events in-process; assert on names and properties without a server |

## Documentation

| | |
|---|---|
| [Integration guide](docs/integration.md) | Common scenarios, diagnostic logging, the smoke drill, testing, `installId` and `deviceId` |
| [Ingestion protocol](https://github.com/HarlonWang/eventbase/blob/main/docs/protocol.md) | The wire contract, in the server repo — the single source of truth |
| [Telemetry design](https://github.com/HarlonWang/eventbase/blob/main/docs/telemetry-design.md) | Event vocabulary and metric definitions |

## License

MIT
