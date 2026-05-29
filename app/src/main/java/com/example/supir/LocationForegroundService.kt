package com.example.supir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase

class LocationForegroundService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private var lastSent: Location? = null
    private var lastSentAt: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val now = System.currentTimeMillis()

            val tooClose = lastSent?.let { loc.distanceTo(it) < 10f } ?: false
            val tooSoon  = (now - lastSentAt) < 120_000L
            if (tooClose && tooSoon) return

            lastSent = loc
            lastSentAt = now

            sendLocationToFirebase(loc, now)
        }
    }

    private fun sendLocationToFirebase(loc: Location, now: Long) {
        try {
            val db = FirebaseDatabase.getInstance().reference
            val data = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "speed" to loc.speed,
                "ts" to now
            )
            db.child("drivers").child("driver_001").setValue(data)
        } catch (e: Exception) {
            // Gagal kirim — akan dicoba pada pembaruan lokasi berikutnya
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        fused = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 45_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .setMaxUpdateDelayMillis(60_000L)
            .setMinUpdateDistanceMeters(10f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission belum diberikan — UI sudah handle ini.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                // IMPORTANCE_LOW: muncul di status bar tanpa suara/getar/heads-up
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPI = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_supir_notif)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notif_body)))
            .setColor(0xFF0D47A1.toInt())
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setContentIntent(openPI)
            .addAction(
                R.drawable.ic_arrow_forward,
                getString(R.string.notif_action_open),
                openPI
            )
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "tracking_channel"

        fun start(ctx: Context) {
            val i = Intent(ctx, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationForegroundService::class.java))
        }
    }
}
