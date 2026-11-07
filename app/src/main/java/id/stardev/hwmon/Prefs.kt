package id.stardev.hwmon
import android.content.Context

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("cfg", 0)
    fun b(k: String, d: Boolean) = p.getBoolean(k, d)
    fun i(k: String, d: Int) = p.getInt(k, d)
    fun set(k: String, v: Any) {
        val e = p.edit()
        when (v) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v) }
        e.commit()
    }
}
