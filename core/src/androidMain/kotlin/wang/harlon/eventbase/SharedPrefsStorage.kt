package wang.harlon.eventbase

import android.content.Context

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
