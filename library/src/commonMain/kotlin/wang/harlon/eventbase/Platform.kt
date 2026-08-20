package wang.harlon.eventbase

/** 摘掉平台侧的生命周期观察者。[Eventbase.reset] 调用，避免重装后叠加。 */
internal expect fun detachLifecycle()

/** 诊断日志。Android 走 android.util.Log（tag `eventbase`，可 `adb logcat -s eventbase`），iOS 走 NSLog。 */
internal expect fun logLine(message: String)
