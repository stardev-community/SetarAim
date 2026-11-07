package id.stardev.hwmon
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var engine: MonitorEngine? = null
    private var root: LinearLayout? = null
    private val vals = arrayOfNulls<TextView>(6)
    private val lbls = arrayOfNulls<TextView>(6)
    private val names = arrayOf("CPU", "VOLTAGE", "LCD VOLTAGE", "RAM", "FPS", "GPU")

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun base() = (prefs.i("topLines", 12) * prefs.i("textSp", 12) * resources.displayMetrics.density * 1.2f).toInt()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        startForeground(1, notif())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { prefs.set("enabled", false); stopSelf(); return START_NOT_STICKY }
            "HIDE" -> prefs.set("hidden", !prefs.b("hidden", false))
            "LOCK" -> prefs.set("unlocked", !prefs.b("unlocked", false))
        }
        startForeground(1, notif())
        try {
            rebuild()
            if (engine == null) engine = MonitorEngine(this) { paint(it) }.also { it.start() }
        } catch (t: Throwable) {
            Crash.log(this, "service", t); prefs.set("enabled", false); stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(c: android.content.res.Configuration) { super.onConfigurationChanged(c); rebuild() }

    override fun onDestroy() { engine?.stop(); engine = null; remove(); super.onDestroy() }

    private fun remove() { root?.let { try { wm.removeView(it) } catch (e: Exception) {} }; root = null }

    private fun notif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("hw", "HW Monitor", NotificationManager.IMPORTANCE_LOW))
        fun pi(a: String, n: Int) = PendingIntent.getForegroundService(this, n, Intent(this, OverlayService::class.java).setAction(a), PendingIntent.FLAG_UPDATE_CURRENT)
        val hid = prefs.b("hidden", false); val unl = prefs.b("unlocked", false)
        return Notification.Builder(this, "hw").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("StarDev HW Monitor")
            .setContentText(if (unl) "Mode geser AKTIF (sentuh bar: geser / ketuk = collapse)" else "Mode kunci (sentuhan tembus)")
            .addAction(android.R.drawable.ic_menu_manage, if (hid) "Tampilkan" else "Sembunyikan", pi("HIDE", 1))
            .addAction(android.R.drawable.ic_menu_manage, if (unl) "Kunci" else "Geser", pi("LOCK", 2))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Berhenti", pi("STOP", 3)).setOngoing(true).build()
    }

    private fun rebuild() {
        remove()
        vals.fill(null); lbls.fill(null)
        if (!android.provider.Settings.canDrawOverlays(this) || prefs.b("hidden", false)) return
        val sp = prefs.i("textSp", 12).toFloat()
        val unlocked = prefs.b("unlocked", false)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(prefs.i("alpha", 80) * 255 / 100, 15, 15, 20))
                setStroke(1, Color.argb(60, 255, 255, 255))
            }
        }
        if (prefs.b("collapsed", false)) {
            r.addView(TextView(this).apply { text = "▸ HW"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp) })
        } else for (i in 0..5) {
            if (!prefs.b("show$i", true)) continue
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) }
            lbls[i] = TextView(this).apply { text = names[i]; setTextColor(0xFFE0E0E0.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * 0.65f); gravity = Gravity.CENTER; maxLines = 1 }
            vals[i] = TextView(this).apply { text = "--"; setTextColor(0xFF9E9E9E.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; maxLines = 1 }
            cell.addView(lbls[i]); cell.addView(vals[i]); r.addView(cell)
        }
        val b = base()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or (if (unlocked) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = prefs.i("offX", 0); lp.y = (b + prefs.i("offY", 0)).coerceAtLeast(0)
        if (unlocked) r.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) { lp.x = sx + dx; lp.y = (sy + dy).coerceAtLeast(0); wm.updateViewLayout(v, lp) }
                    }
                    MotionEvent.ACTION_UP -> if (moved) { prefs.set("offX", lp.x); prefs.set("offY", lp.y - b) }
                        else { prefs.set("collapsed", !prefs.b("collapsed", false)); rebuild() }
                }
                return true
            }
        })
        try { wm.addView(r, lp); root = r } catch (e: Exception) {}
        MonitorEngine.latest?.let { paint(it) }
    }

    private fun col(r: Rd) = if (r.q == Q.UNAVAILABLE) 0xFF9E9E9E.toInt()
        else when (r.level) { 2 -> 0xFFF44336.toInt(); 1 -> 0xFFFFC107.toInt(); else -> 0xFF4CAF50.toInt() }

    private fun paint(s: Snap) {
        val rs = arrayOf(s.cpu, s.bat, s.lcd, s.ram, s.fps, s.gpu)
        for (i in 0..5) {
            val v = vals[i] ?: continue; val r = rs[i]
            v.text = r.text; v.setTextColor(col(r))
            lbls[i]?.text = if (r.sub.isEmpty()) names[i] else names[i] + " " + r.sub
        }
    }
}
