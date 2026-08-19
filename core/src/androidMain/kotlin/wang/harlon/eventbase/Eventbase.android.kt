package wang.harlon.eventbase

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.ktor.client.HttpClient

fun Eventbase.init(
    context: Context,
    config: EventbaseConfig,
    httpClient: HttpClient? = null,
): EventbaseClient {
    val installed = installClient(config, SharedPrefsStorage(context), httpClient)
    // 只在真正装上新实例时注册：重复 init 会叠加观察者，app_opened 就重复上报了
    if (installed.isNew && config.autoLifecycle) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver)
        Eventbase.lifecycleAttached = true
    }
    return installed.client
}

/**
 * 用 ProcessLifecycleOwner 而非自己数 Activity：它内置 700ms 去抖，**配置变更（旋转、
 * 折叠屏展开）不会切出一个假会话**——自己数 started 计数会在 Activity 重建时归零再加一。
 *
 * 口径照旧：无界面的后台进程不会有 Activity，因此不会误报会话。
 */
private object ForegroundObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = Eventbase.onForeground()

    override fun onStop(owner: LifecycleOwner) = Eventbase.onBackground()
}

internal actual fun detachLifecycle() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(ForegroundObserver)
}
