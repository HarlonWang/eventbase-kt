package wang.harlon.eventbase

import android.content.Context

/**
 * 写走 `apply()`：[EventbaseClient.track] 在调用方线程同步入队，落盘不能压在 UI 线程上。
 * SharedPreferences 每次写都重写**整个文件**，与改哪个 key 无关——[EventQueue] 的
 * `persistAppend` 因此只省序列化开销、不省磁盘 I/O，别据此去优化溢出路径。
 */
class SharedPrefsStorage(context: Context) : Storage {
    private val prefs = context.applicationContext.getSharedPreferences("eventbase", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
