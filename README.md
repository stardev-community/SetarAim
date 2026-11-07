# StarDev Floating Hardware Monitor
Target: Vivo 1819, Android 9 (API 28). Kotlin, minSdk 26, targetSdk 28.

## Build tanpa PC
A) GitHub Actions: push folder ini ke repo -> tab Actions -> "Build APK" -> unduh artifact app-debug.apk (APK debug, sudah ter-sign, langsung bisa dipasang).
B) AIDE PRO: Open Project -> pilih folder ini -> Build. (Butuh dukungan Kotlin+Gradle di AIDE PRO; jika gagal pakai opsi A.)

## Pasang
Buka APK dari file manager -> izinkan "pasang dari sumber ini" -> Install.

## Pakai
1. Buka app -> tombol 1: izinkan overlay.
2. (Opsional, untuk FPS) pasang Shizuku, jalankan via Wireless debugging (Android 11+) atau ADB; Android 9 butuh ADB sekali per boot. Lalu tombol 2.
3. Tombol 3 + di Vivo: i Manager > Aplikasi > Autostart & izinkan latar belakang agar service tidak dimatikan.
4. Nyalakan "Overlay aktif". Bar sentuhan-tembus. Untuk geser: nyalakan "Mode geser" (atau aksi notifikasi), seret bar; ketuk = collapse.

## Sumber data
CPU: /proc/stat (SYSTEM_REPORTED) -> fallback rasio cpufreq (ESTIMATED, tampil "~", kuning). Voltage: BatteryManager. LCD: /sys/class/regulator (nama rail cocok vci/vddi/avdd/avee/lcd/dsi...), N/A bila tidak terbaca. RAM: ActivityManager. FPS: dumpsys SurfaceFlinger --latency via Shizuku (MEASURED), N/A bila tak ada.

## FPS tanpa Shizuku
Opsi A (sekali, permanen): dari PC/HP lain via ADB: `adb shell pm grant id.stardev.hwmon android.permission.DUMP`. Setelah itu FPS jalan tanpa Shizuku, tetap ada setelah reboot.
Opsi B: Shizuku. Opsi C (fallback): file measured_fps (hanya di sebagian kernel, itu laju frame layar, bukan FPS per-aplikasi).
CPU asli (/proc/stat) juga dibaca lewat Shizuku kalau Android memblokir akses langsung.
