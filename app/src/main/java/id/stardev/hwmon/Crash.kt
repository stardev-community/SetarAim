package id.stardev.hwmon
import android.app.Application
import android.content.Context
import java.io.File

object Crash {
    fun file(c: Context) = File(c.filesDir, "crash.txt")
    fun log(c: Context, where: String, t: Throwable) {
        try {
            val f = file(c)
            if (f.exists() && f.length() > 20000) return
            f.appendText("[$where] " + android.util.Log.getStackTraceString(t).take(1500) + "\n\n")
        } catch (e: Throwable) {}
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, t ->
            Crash.log(this, th.name, t)
            try { Prefs(this).set("enabled", false) } catch (e: Throwable) {}
            old?.uncaughtException(th, t)
        }
    }
}
