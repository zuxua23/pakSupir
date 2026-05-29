package com.example.supir

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * DashboardActivity
 * - FLAG_SECURE aktif: tidak bisa di-screenshot.
 * - Tidak ada scroll: semua info penting (status, kecepatan, trip, QR) terlihat sekaligus.
 * - Mulai LocationForegroundService agar tetap kirim koordinat saat app ditutup.
 * - tvSpeed di-update dari FusedLocationProvider (kecepatan nyata, bukan demo).
 * - Google Maps dengan animasi marker Gojek-style (ValueAnimator LatLng).
 */
class DashboardActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var tvSpeed: TextView
    private lateinit var btnScanQR: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private lateinit var fusedClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private var isFirstLocation = true

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val speedKmh = (loc.speed * 3.6).toInt()
            tvSpeed.text = speedKmh.toString()
            val newPos = LatLng(loc.latitude, loc.longitude)
            animateMarkerTo(newPos)
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            Toast.makeText(this, "QR: ${result.contents}", Toast.LENGTH_LONG).show()
            // TODO: proses QR result sesuai kebutuhan bisnis
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationTracking()
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk pelacakan", Toast.LENGTH_LONG).show()
        }
    }

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

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Start foreground service → persistent notification muncul
        LocationForegroundService.start(this)

        btnScanQR.setOnClickListener { launchQrScan() }
        btnLogout.setOnClickListener { showLogoutConfirm() }

        checkAndRequestLocationPermission()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isMapToolbarEnabled = false
    }

    /**
     * Animasi marker Gojek-style: ValueAnimator menginterpolasi LatLng lama → baru
     * dalam 1,5 detik dengan AccelerateDecelerateInterpolator untuk kesan mulus.
     */
    private fun animateMarkerTo(newPos: LatLng) {
        val map = googleMap ?: return
        if (driverMarker == null || isFirstLocation) {
            if (driverMarker == null) {
                driverMarker = map.addMarker(MarkerOptions().position(newPos).title("Saya"))
            } else {
                driverMarker!!.position = newPos
            }
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 16f))
            isFirstLocation = false
            return
        }
        val marker = driverMarker!!
        val startPos = marker.position
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val lat = startPos.latitude + (newPos.latitude - startPos.latitude) * t
                val lng = startPos.longitude + (newPos.longitude - startPos.longitude) * t
                marker.position = LatLng(lat, lng)
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
            }
        }
        animator.start()
    }

    private fun launchQrScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }

    private fun checkAndRequestLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            startLocationTracking()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // permission denied — handled by permission launcher
        }
    }

    private fun showLogoutConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_logout_title)
            .setMessage(R.string.dialog_logout_body)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_logout_confirm) { _, _ ->
                LocationForegroundService.stop(this)
                fusedClient.removeLocationUpdates(locationCallback)
                startActivity(android.content.Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .show()
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}
