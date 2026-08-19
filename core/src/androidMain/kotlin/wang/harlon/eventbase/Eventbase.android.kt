package wang.harlon.eventbase

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import io.ktor.client.HttpClient

fun Eventbase.init(
    context: Context,
    config: EventbaseConfig,
    httpClient: HttpClient = HttpClient(),
): EventbaseClient {
    val client = init(config, SharedPrefsStorage(context), httpClient)
    if (config.autoLifecycle) {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(ForegroundWatcher())
    }
    return client
}

/**
 * 用 ActivityLifecycleCallbacks 而非 androidx.lifecycle 的 ProcessLifecycleOwner：
 * 后者要多一个依赖，而依赖准入的门槛对本库同样成立。
 *
 * 顺带拿到正确的口径——**无界面的后台进程不会有 Activity**，因此不会误报会话。
 */
private class ForegroundWatcher : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) Eventbase.onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started == 0) Eventbase.onBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
