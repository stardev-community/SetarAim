package id.stardev.hwmon
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Prefs(c)
        if (i.action == Intent.ACTION_BOOT_COMPLETED && p.b("enabled", false) && p.b("boot", false) && Perm.hasOverlay(c)) Perm.send(c, "REFRESH")
    }
}
