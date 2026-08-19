package wang.harlon.eventbase

/** 摘掉平台侧的生命周期观察者。[Eventbase.reset] 调用，避免重装后叠加。 */
internal expect fun detachLifecycle()
