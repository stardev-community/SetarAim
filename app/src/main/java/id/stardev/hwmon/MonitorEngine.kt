package id.stardev.hwmon
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

class MonitorEngine(private val c: Context, private val onSnap: (Snap) -> Unit) {
    private val th = HandlerThread("hwmon").apply { start() }
    private val h = Handler(th.looper)
    private val ui = Handler(Looper.getMainLooper())
    private val cpu = CpuReader(); private val fps = FpsReader(); private val prefs = Prefs(c)
    @Volatile private var active = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val s = Snap(cpu.read(), battery(c), Lcd.read(), ram(c), fps.read(), cpu.cores)
            latest = s
            ui.post { onSnap(s) }
            h.postDelayed(this, prefs.i("interval", 1000).toLong())
        }
    }
    fun start() { active = true; h.post(tick) }
    fun stop() { active = false; h.removeCallbacksAndMessages(null); th.quitSafely(); latest = null }
    companion object { @Volatile var latest: Snap? = null }
}
