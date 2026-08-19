package wang.harlon.eventbase

import io.ktor.client.HttpClient
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

fun Eventbase.init(
    config: EventbaseConfig,
    httpClient: HttpClient = HttpClient(),
): EventbaseClient {
    val client = init(config, UserDefaultsStorage(), httpClient)
    if (config.autoLifecycle) observeLifecycle()
    return client
}

private fun observeLifecycle() {
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
        Eventbase.onForeground()
    }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
        Eventbase.onBackground()
    }
}
