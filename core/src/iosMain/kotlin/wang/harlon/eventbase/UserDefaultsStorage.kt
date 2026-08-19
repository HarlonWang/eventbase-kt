package wang.harlon.eventbase

import platform.Foundation.NSUserDefaults

class UserDefaultsStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : Storage {
    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String) = defaults.setObject(value, key)

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}
