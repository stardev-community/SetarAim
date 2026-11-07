package id.stardev.hwmon
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var dash: TextView
    private lateinit var prev: TextView
    private val h = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, sz: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(8), 0, dp(4)) }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    private fun sw(t: String, k: String, d: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = t; isChecked = p.b(k, d); setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, on -> p.set(k, on); f(on) } }
    private fun seek(col: LinearLayout, t: String, k: String, min: Int, max: Int, d: Int, unit: String) {
        val l = tv("$t: ${p.i(k, d)}$unit")
        val s = SeekBar(this).apply {
            this.max = max - min; progress = p.i(k, d) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, u: Boolean) { l.text = "$t: ${v + min}$unit" }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { p.set(k, sb.progress + min); refresh() }
            })
        }
        col.addView(l); col.addView(s)
    }
    private fun refresh() { if (p.b("enabled", false)) Perm.send(this, "REFRESH") }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = Prefs(this)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32)) }
        setContentView(ScrollView(this).apply { addView(c) })
        c.addView(tv("StarDev Floating Hardware Monitor", 20f, true))
        c.addView(btn("1. Izin overlay (SYSTEM_ALERT_WINDOW)") { Perm.requestOverlay(this) })
        c.addView(btn("2. Izin Shizuku (untuk FPS)") { Perm.requestShizuku(this) })
        c.addView(btn("3. Pengecualian baterai / autostart") { Perm.batterySettings(this) })
        c.addView(sw("Overlay aktif", "enabled", false) { on ->
            if (on) { if (!Perm.hasOverlay(this)) Perm.requestOverlay(this); Perm.send(this, "REFRESH") }
            else stopService(Intent(this, OverlayService::class.java)) })
        c.addView(sw("Sembunyikan sementara", "hidden", false) { refresh() })
        c.addView(sw("Mode geser (bar bisa disentuh: geser / ketuk = collapse)", "unlocked", false) { refresh() })
        c.addView(sw("Collapse", "collapsed", false) { refresh() })
        c.addView(sw("Mulai saat boot", "boot", false) {})
        seek(c, "Transparansi latar", "alpha", 0, 100, 80, "%")
        seek(c, "Ukuran teks", "textSp", 8, 24, 12, "sp")
        seek(c, "Offset atas (x tinggi teks)", "topLines", 0, 30, 12, "")
        seek(c, "Interval update (makin besar makin hemat CPU)", "interval", 10, 2000, 1000, " ms")
        c.addView(sw("Satu warna teks (mati = hijau/kuning/merah sesuai status)", "mono", false) { refresh() })
        seek(c, "Warna teks - Merah", "tr", 0, 255, 255, "")
        seek(c, "Warna teks - Hijau", "tg", 0, 255, 255, "")
        seek(c, "Warna teks - Biru", "tb", 0, 255, 255, "")
        prev = TextView(this).apply { text = "Contoh warna teks 12.34V"; textSize = 16f; setBackgroundColor(0xFF202028.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        c.addView(prev)
        c.addView(tv("Indikator tampil", 14f, true))
        listOf("CPU", "VOLTAGE", "LCD VOLTAGE", "RAM", "FPS", "GPU").forEachIndexed { i, n -> c.addView(sw(n, "show$i", true) { refresh() }) }
        c.addView(btn("Reset posisi") { p.set("offX", 0); p.set("offY", 0); refresh() })
        c.addView(btn("Hapus log crash") { Crash.file(this).delete() })
        c.addView(tv("Dashboard diagnostik", 16f, true))
        dash = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 11f }
        c.addView(dash)
    }

    override fun onResume() { super.onResume(); refresh(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }

    private val tick = object : Runnable {
        override fun run() {
            val s = MonitorEngine.latest
            val cr = Crash.file(this@MainActivity).let { if (it.exists()) "!! LOG CRASH:\n" + it.readText().take(3000) + "\n\n" else "" }
            val sb = StringBuilder(cr + "Overlay: ${if (Perm.hasOverlay(this@MainActivity)) "izin OK" else "izin BELUM"} | ${Perm.shizuku()}\n\n")
            if (s == null) sb.append("Layanan belum berjalan / belum ada sampel.")
            else {
                listOf("CPU" to s.cpu, "BATERAI" to s.bat, "LCD" to s.lcd, "RAM" to s.ram, "FPS" to s.fps, "GPU" to s.gpu).forEach { (n, r) ->
                    sb.append("$n: ${r.text} ${r.sub}\n  [${r.q}] ${r.src}\n") }
                sb.append("\nPer-core: ${s.cores.ifEmpty { "-" }}\n\nRail LCD terdeteksi:\n")
                val rails = Lcd.scan()
                sb.append(if (rails.isEmpty()) "  (tidak ada)" else rails.joinToString("\n") { "  ${it.first} = ${f2(it.second)} V" })
            }
            prev.setTextColor(Color.rgb(p.i("tr", 255), p.i("tg", 255), p.i("tb", 255)))
            dash.text = sb.toString()
            h.postDelayed(this, 1000)
        }
    }
}
