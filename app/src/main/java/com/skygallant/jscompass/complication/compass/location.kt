package com.skygallant.jscompass.complication.compass

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skygallant.jscompass.complication.compass.data.complicationsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class LocationUpdatesService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        var x: Location
        if (Receiver.checkPermission(this)) {
            runBlocking {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        x = location
                        Log.d(TAG, "foo $x")
                        Log.d(TAG, "foo ${x.latitude}")
                        Log.d(TAG, "foo ${x.longitude}")

                        runBlocking {
                            applicationContext.complicationsDataStore.updateData {
                                it.copy(
                                    myLocation = x,
                                    initLoc = true
                                )
                            }
                            Log.d(TAG, "bar" + applicationContext.complicationsDataStore.data
                                .map { complicationsDataStore ->
                                    complicationsDataStore.myLocation
                                }
                                .first().latitude.toString())
                        }
                    }
            }
        }
        startLocationUpdates()
        return START_NOT_STICKY
    }
    /**
    override fun onCreate() {
    super.onCreate()
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    startLocationUpdates()
    }
     **/
    private fun startLocationUpdates() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TimeUnit.MINUTES.toMillis(5)).apply {
                setMinUpdateIntervalMillis(TimeUnit.MINUTES.toMillis(5))
                setMaxUpdateDelayMillis(TimeUnit.MINUTES.toMillis(10))
            }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "location results")
                for (location in locationResult.locations) {
                    Log.d(TAG, "Lat: ${location.latitude}, Long: ${location.longitude}")
                    if (location != null) {
                        runBlocking {
                            applicationContext.complicationsDataStore.updateData {
                                it.copy(
                                    myLocation = location
                                )
                            }
                        }
                    }
                }
            }
        }

        if (Receiver.checkPermission(this)) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        private const val TAG = "LocationUpdatesService"
    }
}
