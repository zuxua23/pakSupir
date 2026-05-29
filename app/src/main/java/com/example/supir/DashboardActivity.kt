package com.example.supir

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * DashboardActivity
 * - FLAG_SECURE aktif: tidak bisa di-screenshot.
 * - Tidak ada scroll: semua info penting (status, kecepatan, trip, QR) terlihat sekaligus.
 * - Mulai LocationForegroundService agar tetap kirim koordinat saat app ditutup.
 * - tvSpeed di-update tiap pembaruan koordinat (30–60 detik) — di sini contoh demo random.
 *
 * Integrasi map sungguhan (Google Maps / MapLibre):
 *   - ganti FrameLayout @id/mapContainer dengan MapView,
 *   - smooth-move marker pakai ValueAnimator yang menginterpolasi LatLng lama → baru
 *     (lihat catatan animateMarkerTo() di README).
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var tvSpeed: TextView
    private lateinit var btnScanQR: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private var demoTick: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ====== BLOKIR SCREENSHOT ======
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_dashboard)

        tvSpeed   = findViewById(R.id.tvSpeed)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnLogout = findViewById(R.id.btnLogout)

        // 1) Start foreground service → persistent notification muncul
        LocationForegroundService.start(this)

        // 2) Tombol SCAN QR
        btnScanQR.setOnClickListener {
            // TODO: integrasi ZXing / ML Kit Barcode
            // startActivity(Intent(this, ScanQrActivity::class.java))
        }

        // 3) Tombol KELUAR — pakai dialog konfirmasi besar agar tidak salah tap
        btnLogout.setOnClickListener { showLogoutConfirm() }

        // 4) Demo update kecepatan (di app sungguhan, ini di-update lewat callback Location)
        startSpeedDemo()
    }

    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_logout_title)
            .setMessage(R.string.dialog_logout_body)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_logout_confirm) { _, _ ->
                LocationForegroundService.stop(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .show()
    }

    /** Demo: kecepatan berubah halus tiap 2 detik. Ganti dengan FusedLocationProvider di production. */
    private fun startSpeedDemo() {
        demoTick = object : Runnable {
            override fun run() {
                val current = tvSpeed.text.toString().toIntOrNull() ?: 45
                val target = (30..80).random()
                val next = current + ((target - current) * 0.5).toInt()
                tvSpeed.text = next.toString()
                handler.postDelayed(this, 2200L)
            }
        }.also { handler.post(it) }
    }

    override fun onDestroy() {
        demoTick?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
