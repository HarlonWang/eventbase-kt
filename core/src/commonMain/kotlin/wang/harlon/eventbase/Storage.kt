package wang.harlon.eventbase

/** 键值持久化。Android 走 SharedPreferences，iOS 走 NSUserDefaults，测试用内存实现。 */
interface Storage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class MemoryStorage : Storage {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }
    override fun remove(key: String) {
        map.remove(key)
    }
}
