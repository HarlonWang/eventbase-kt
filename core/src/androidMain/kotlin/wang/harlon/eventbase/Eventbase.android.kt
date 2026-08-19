package wang.harlon.eventbase

import android.content.Context
import android.os.Handler
import android.os.Looper
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
        onMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundObserver) }
        Eventbase.markLifecycleAttached()
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
    onMainThread { ProcessLifecycleOwner.get().lifecycle.removeObserver(ForegroundObserver) }
}

/**
 * LifecycleRegistry 强制主线程，而 init/reset 完全可能在业务的 IO 协程里被调用；
 * 消费方若移除了 androidx.startup 的 InitializationProvider，`get()` 本身也会抛。
 * 埋点绝不能成为业务的故障源，故两层都兜住。
 */
private inline fun onMainThread(crossinline block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        runCatching { block() }
    } else {
        Handler(Looper.getMainLooper()).post { runCatching { block() } }
    }
}
