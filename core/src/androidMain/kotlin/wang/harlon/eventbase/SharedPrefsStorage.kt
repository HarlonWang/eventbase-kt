package wang.harlon.eventbase

import android.content.Context

/**
 * 用 `commit()` 而非 `apply()`：`apply()` 异步落盘，进程被强杀会丢掉 install_id 与整个队列。
 * 写入已在 [EventQueue] 的锁内串行，单次 JSON 通常几 KB；队列接近上限时的开销由
 * 「不再全量序列化」那条优化解决（见服务端仓 docs/review-findings.md 的 K18）。
 */
class SharedPrefsStorage(context: Context) : Storage {
    private val prefs = context.applicationContext.getSharedPreferences("eventbase", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }
}
