package wang.harlon.eventbase

import io.ktor.client.HttpClient
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObjectProtocol

fun Eventbase.init(
    config: EventbaseConfig,
    httpClient: HttpClient? = null,
): EventbaseClient {
    val installed = installClient(config, UserDefaultsStorage(), httpClient)
    if (installed.isNew && config.autoLifecycle) {
        observeLifecycle()
        Eventbase.lifecycleAttached = true
    }
    return installed.client
}

/** addObserverForName 返回的 token 必须留住，否则观察者再也摘不掉。 */
private val observers = mutableListOf<NSObjectProtocol>()

private fun observeLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    observers += center.addObserverForName(
        UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue
    ) { Eventbase.onForeground() }
    observers += center.addObserverForName(
        UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue
    ) { Eventbase.onBackground() }
}

internal actual fun detachLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    observers.forEach(center::removeObserver)
    observers.clear()
}
