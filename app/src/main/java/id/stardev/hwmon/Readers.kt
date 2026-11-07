package id.stardev.hwmon
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.os.BatteryManager
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Locale

enum class Q { MEASURED, SYSTEM_REPORTED, ESTIMATED, UNAVAILABLE }
data class Rd(val text: String, val q: Q, val src: String, val level: Int = 0, val sub: String = "")
data class Snap(val cpu: Rd, val bat: Rd, val lcd: Rd, val ram: Rd, val fps: Rd, val gpu: Rd, val cores: String)

fun na(src: String) = Rd("N/A", Q.UNAVAILABLE, src)
fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

class CpuReader {
    private var prev: Map<String, LongArray>? = null
    var cores = ""
    private fun parse(): Map<String, LongArray>? = try {
        File("/proc/stat").readLines().filter { it.startsWith("cpu") }.associate { l ->
            val f = l.trim().split(Regex("\\s+")); f[0] to f.drop(1).take(8).map { it.toLong() }.toLongArray()
        }.takeIf { it.containsKey("cpu") }
    } catch (e: Exception) { null }

    private fun pct(a: LongArray, b: LongArray): Int {
        val dt = b.sum() - a.sum(); val di = (b[3] + b[4]) - (a[3] + a[4])
        return if (dt <= 0) 0 else ((dt - di) * 100 / dt).toInt().coerceIn(0, 100)
    }

    fun read(): Rd {
        val m = parse()
        if (m != null && m["cpu"]!!.size >= 5) {
            val p = prev; prev = m
            if (p == null) return na("/proc/stat: menunggu sampel kedua")
            val t = pct(p["cpu"]!!, m["cpu"]!!)
            cores = m.keys.filter { it != "cpu" }.sorted().joinToString(" ") { k ->
                val a = p[k]; if (a == null) "$k:?" else "$k:${pct(a, m[k]!!)}%" }
            return Rd("$t%", Q.SYSTEM_REPORTED, "/proc/stat (TOTAL semua core)", if (t >= 90) 2 else if (t >= 70) 1 else 0)
        }
        cores = ""
        var cur = 0L; var mx = 0L
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val d = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            try {
                cur += File(d + "scaling_cur_freq").readText().trim().toLong()
                mx += File(d + "cpuinfo_max_freq").readText().trim().toLong()
            } catch (e: Exception) {}
        }
        if (mx > 0) return Rd("~${cur * 100 / mx}%", Q.ESTIMATED, "rasio frekuensi cpufreq - BUKAN utilisasi (/proc/stat diblokir Android 9)", 1)
        return na("/proc/stat dan cpufreq tidak bisa dibaca")
    }
}

fun battery(c: Context): Rd {
    val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val raw = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
    if (raw <= 0) return na("BatteryManager tidak melaporkan tegangan")
    val v = if (raw > 100000) raw / 1e6 else if (raw < 20) raw.toDouble() else raw / 1000.0
    return Rd(f2(v) + "V", Q.SYSTEM_REPORTED, "BatteryManager EXTRA_VOLTAGE", if (v < 3.3) 2 else if (v < 3.5) 1 else 0)
}

object Lcd {
    private val rx = Regex("(?i)vci|vddi|avdd|avee|vsp|vsn|lcd|lcm|dsi|panel|disp|backlight")
    fun scan(): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        try {
            File("/sys/class/regulator").listFiles()?.forEach { d ->
                try {
                    val n = File(d, "name").readText().trim()
                    if (rx.containsMatchIn(n)) {
                        val uv = File(d, "microvolts").readText().trim().toLong()
                        if (uv > 0) out.add(n to uv / 1e6)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        return out
    }
    fun read(): Rd {
        val l = scan()
        if (l.isEmpty()) return na("Tidak ada rail LCD terbaca di /sys/class/regulator (SELinux/driver)")
        val (n, v) = l[0]
        return Rd(f2(v) + "V", Q.SYSTEM_REPORTED, "regulator '$n' (dilaporkan kernel, bukan ADC)", 0, n.take(10))
    }
}

fun ram(c: Context): Rd {
    val mi = ActivityManager.MemoryInfo()
    (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    val used = mi.totalMem - mi.availMem
    val p = (used * 100 / mi.totalMem).toInt()
    fun g(x: Long) = String.format(Locale.US, "%.1f", x / 1073741824.0)
    return Rd("$p%", Q.SYSTEM_REPORTED, "ActivityManager.MemoryInfo", if (p >= 92) 2 else if (p >= 80) 1 else 0, "${g(used)}/${g(mi.totalMem)}G")
}

object Sh {
    fun ok() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    fun run(cmd: String): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val o = p.inputStream.bufferedReader().readText(); p.waitFor(); o
    } catch (e: Throwable) { null }
}

class FpsReader {
    private var layer: String? = null
    private var ts = 0L
    fun read(): Rd {
        if (!Sh.ok()) return na("Butuh Shizuku aktif + izin (FPS dari SurfaceFlinger)")
        val now = System.currentTimeMillis()
        if (layer == null || now - ts > 3000) {
            ts = now
            val pkg = Sh.run("dumpsys window | grep -m1 mCurrentFocus")
                ?.let { Regex("([\\w.]+)/[\\w.$]+\\}").find(it)?.groupValues?.get(1) }
            val ls = Sh.run("dumpsys SurfaceFlinger --list")?.lines()?.filter { pkg != null && it.contains(pkg) }
            layer = ls?.firstOrNull { it.contains("SurfaceView") } ?: ls?.firstOrNull()
        }
        val ly = layer ?: return na("Layer aplikasi fokus tidak ditemukan")
        val out = Sh.run("dumpsys SurfaceFlinger --latency '$ly'") ?: return na("dumpsys gagal")
        val t = out.lines().drop(1).mapNotNull { l ->
            val f = l.trim().split(Regex("\\s+")); if (f.size < 3) null else f[1].toLongOrNull()
        }.filter { it > 0 && it < Long.MAX_VALUE / 2 }
        if (t.isEmpty()) { layer = null; return na("Tidak ada data frame untuk layer ini") }
        val newest = t.max()
        val fps = if (System.nanoTime() - newest > 1_500_000_000L) 0 else t.count { it > newest - 1_000_000_000L }
        return Rd("$fps", Q.MEASURED, "SurfaceFlinger --latency (frame yang benar-benar dipresentasikan)", if (fps < 30) 1 else 0)
    }
}

object Gpu {
    private val fixed = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "/sys/kernel/ged/hal/gpu_utilization",
        "/sys/kernel/debug/ged/hal/gpu_utilization", "/d/ged/hal/gpu_utilization", "/proc/mali/utilization")
    @Volatile private var path: String? = null
    private var miss = 0
    private fun candidates(): List<String> {
        val l = ArrayList(fixed)
        try {
            File("/sys/class/devfreq").listFiles()?.filter { Regex("(?i)gpu|mali|kgsl|pvr").containsMatchIn(it.name) }
                ?.forEach { l.add(it.path + "/load") }
        } catch (e: Exception) {}
        return l
    }
    private fun cat(p: String): String? = try { File(p).readText() } catch (e: Exception) {
        if (Sh.ok()) Sh.run("cat $p 2>/dev/null")?.takeIf { it.isNotBlank() } else null }
    private fun parse(t: String): Int? = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    private fun ok(p: String, v: Int) = Rd("$v%", Q.SYSTEM_REPORTED, "$p (angka pertama = utilisasi GPU)", if (v >= 90) 2 else if (v >= 70) 1 else 0)
    fun read(): Rd {
        path?.let { p -> val v = cat(p)?.let { parse(it) }; if (v != null) return ok(p, v); path = null }
        if (miss++ % 10 != 0) return na("Beban GPU belum terbaca (cek ulang berkala)")
        for (p in candidates()) { val v = cat(p)?.let { parse(it) }; if (v != null) { path = p; return ok(p, v) } }
        return na("Tidak ada file beban GPU yang terbaca (butuh Shizuku/root; tergantung chipset)")
    }
}
