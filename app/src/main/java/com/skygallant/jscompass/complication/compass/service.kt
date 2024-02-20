package com.skygallant.jscompass.complication.compass

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.skygallant.jscompass.complication.compass.data.BEARING_KEY
import com.skygallant.jscompass.complication.compass.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

const val TAG: String = "JSCompanionCompass"

/**
var myLocationCallback = object : LocationCallback() {
override fun onLocationResult(p0: LocationResult) {
super.onLocationResult(p0)

p0.let {
Log.d(TAG, "ping")
myLocation = it.lastLocation
}
}
}
var myLocationCallback: LocationCallback = object : LocationCallback() {
    @SuppressLint("MissingPermission")
    override fun onLocationResult(locationResult: LocationResult) {
        for (location in locationResult.locations.asReversed()) {
            if (location != null) {
                myLocation = location
                break
            }
        }
        if (myLocation == null) {
            myLocation = FLP.lastLocation.result
        }
    }
}
val myUrgentLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
    .setMaxUpdates(1)
    .build()
**/


class Service : SuspendingComplicationDataSourceService(), SensorEventListener {




    companion object {
        lateinit var serviceIntent: Intent
        lateinit var geofencingClient: GeofencingClient

        lateinit var sensorManager: SensorManager
        var accelerometerReading = FloatArray(3)
        var magnetometerReading = FloatArray(3)

        var Fate: Boolean = true
        var initLoc: Boolean = false
        var initDest: Boolean = false

        fun getGeoIntent(thisContext: Context): PendingIntent {
            val geofencePendingIntent: PendingIntent by lazy {
                val intent = Intent(thisContext, GeofenceReceiver::class.java)
                // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
                // addGeofences() and removeGeofences().
                PendingIntent.getBroadcast(
                    thisContext,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }
            return geofencePendingIntent
        }
    }




    private fun doSensors() {

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        Log.d(TAG, "tracking mag")

        if(Receiver.checkPermission(applicationContext)) {
            Log.d(TAG, "tracking pos")
            geofencingClient = LocationServices.getGeofencingClient(this)
        }
    }

    private fun shutdownSensors() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "shutdown mag")
        if(Receiver.checkPermission(applicationContext)) {
            geofencingClient.removeGeofences(getGeoIntent(applicationContext))
            Log.d(TAG, "shutdown fence")
        }
    }


    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")

        Fate = true
        doSensors()
        serviceIntent = Intent(applicationContext, LocationUpdatesService::class.java)
        startService(serviceIntent)

    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return RangedValueComplicationData.Builder(
        value = 30f,
        min = 0f,
        max = 360f,
        contentDescription = PlainComplicationText
            .Builder(text = "Ranged Value version of Bearing.").build()
        )
            .setText(PlainComplicationText.Builder(text = "30").build())
            .setTapAction(null)
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(TAG, "onComplicationRequest() id: ${request.complicationInstanceId}")

        // Create Tap Action so that the user can trigger an update by tapping the complication.
        val thisDataSource = ComponentName(this, javaClass)
        // We pass the complication id, so we can only update the specific complication tapped.
        val complicationPendingIntent =
            Receiver.getToggleIntent(
                this,
                thisDataSource,
                request.complicationInstanceId
            )

        val number: Int = applicationContext.dataStore.data
            .map { preferences ->
                preferences[BEARING_KEY] ?: 0
            }
            .first()

        val numberText = String.format(Locale.getDefault(), "%d", number)

        return when (request.complicationType) {

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = number.toFloat(),
                min = 0f,
                max = 360f,
                contentDescription = PlainComplicationText
                    .Builder(text = "Ranged Value version of Bearing.").build()
            )
                .setText(PlainComplicationText.Builder(text = numberText).build())
                .setTapAction(complicationPendingIntent)
                .build()

            else -> {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type ${request.complicationType}")
                }
                null
            }
        }
    }

    // Called when the complication has been deactivated.
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated(): $complicationInstanceId")

        shutdownSensors()
        stopService(serviceIntent)

    }

    override fun onSensorChanged(eventCall: SensorEvent?) {
        if (eventCall != null) {
            if (eventCall.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(
                    eventCall.values,
                    0,
                    accelerometerReading,
                    0,
                    accelerometerReading.size
                )
            } else if (eventCall.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(
                    eventCall.values,
                    0,
                    magnetometerReading,
                    0,
                    magnetometerReading.size
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }

}