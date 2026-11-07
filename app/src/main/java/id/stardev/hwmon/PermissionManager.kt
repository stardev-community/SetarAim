package id.stardev.hwmon
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import rikka.shizuku.Shizuku

object Perm {
    fun hasOverlay(c: Context) = Settings.canDrawOverlays(c)
    fun requestOverlay(a: Activity) = a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.packageName)))
    fun batterySettings(a: Activity) = a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    fun send(c: Context, action: String) {
        c.startForegroundService(Intent(c, OverlayService::class.java).setAction(action))
    }
    fun shizuku(): String = try {
        if (!Shizuku.pingBinder()) "Shizuku tidak berjalan"
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Shizuku siap (FPS aktif)"
        else "Shizuku jalan, izin belum diberikan"
    } catch (e: Throwable) { "Shizuku tidak terpasang" }
    fun requestShizuku(a: Activity) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1)
            else Toast.makeText(a, shizuku(), Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) { Toast.makeText(a, "Pasang & jalankan Shizuku dulu", Toast.LENGTH_LONG).show() }
    }
}
