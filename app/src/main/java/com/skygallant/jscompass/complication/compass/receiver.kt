package com.skygallant.jscompass.complication.compass

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.skygallant.jscompass.complication.compass.data.complicationsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class Receiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun piFun(x: Float): Float {
        val magic: Float = 180f / kotlin.math.PI.toFloat()
        return x * magic
    }
    private fun doCompass(gotCon: Context): Int {
        var bearing = 0f
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        SensorManager.getRotationMatrix(rotationMatrix, null, Service.accelerometerReading, Service.magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        var heading = orientationAngles[0]
        heading = piFun(heading)

        val isLoc = runBlocking {
            gotCon.complicationsDataStore.data
                .map { complicationsDataStore ->
                    complicationsDataStore.initLoc
                }
                .first()
        }

        val isDest = runBlocking {
            gotCon.complicationsDataStore.data
                .map { complicationsDataStore ->
                    complicationsDataStore.initDest
                }
                .first()
        }

        val thisLocation = runBlocking {
            gotCon.complicationsDataStore.data
                .map { complicationsDataStore ->
                    complicationsDataStore.myLocation
                }
                .first()
        }

        val thisDestiny = runBlocking {
            gotCon.complicationsDataStore.data
                .map { complicationsDataStore ->
                    complicationsDataStore.destiny
                }
                .first()
        }

        if (checkPermission(gotCon)) {
            if (isLoc) {
                if (isDest) {
                    bearing = thisLocation.bearingTo(thisDestiny)
                    val text = thisDestiny.provider.toString()
                    val duration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(gotCon, text, duration)
                    toast.show()
                    val geoField = GeomagneticField(
                        thisLocation.latitude.toFloat(),
                        thisLocation.longitude.toFloat(),
                        thisLocation.altitude.toFloat(),
                        System.currentTimeMillis()
                    )
                    heading += geoField.declination
                    bearing = (bearing - heading) * -1
                    bearing = (360 - bearing) % 360
                } else {
                    //Log.d(TAG, thisDestiny.provider.toString())
                    if (thisDestiny.provider.toString() == "Google Maps API") {
                        WorkManager.getInstance(gotCon)
                            .enqueue(OneTimeWorkRequestBuilder<DestinyWorker>().build())
                    }
                    val text = "Fate"
                    val duration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(gotCon, text, duration)
                    toast.show()
                }
            } else {
                val text = "Compass Pos"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(gotCon, text, duration)
                toast.show()
            }
        } else {
            val text = "Compass Perms"
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(gotCon, text, duration)
            toast.show()
        }


        Log.d(TAG, "bearing: $bearing")
        return bearing.toInt()
    }


    override fun onReceive(context: Context, intent: Intent) {

        // Retrieve complication values from Intent's extras.
        val extras = intent.extras ?: return
        val dataSource = extras.getParcelable<ComponentName>(EXTRA_DATA_SOURCE_COMPONENT) ?: return
        val complicationId = extras.getInt(EXTRA_COMPLICATION_ID)

        // Required when using async code in onReceive().
        val result = goAsync()

        // Launches coroutine to update the DataStore counter value.
        scope.launch {
            try {
                context.complicationsDataStore.updateData {
                    it.copy(
                        bearingKey = doCompass(context)
                    )
                }

                // Request an update for the complication that has just been tapped, that is,
                // the system call onComplicationUpdate on the specified complication data
                // source.
                val complicationDataSourceUpdateRequester =
                    ComplicationDataSourceUpdateRequester.create(
                        context = context,
                        complicationDataSourceComponent = dataSource
                    )
                complicationDataSourceUpdateRequester.requestUpdate(complicationId)



            } finally {
                // Always call finish, even if cancelled
                result.finish()
            }
        }
    }

    companion object {
        fun checkPermission(thisContext: Context): Boolean {
            Log.d(TAG, "CHECK")
            return ActivityCompat.checkSelfPermission(
                thisContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        private const val EXTRA_DATA_SOURCE_COMPONENT =
            "com.skygallant.jscompass.complication.rose.action.DATA_SOURCE_COMPONENT"
        private const val EXTRA_COMPLICATION_ID =
            "com.skygallant.jscompass.complication.rose.action.COMPLICATION_ID"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getToggleIntent(
            context: Context,
            dataSource: ComponentName,
            complicationId: Int
        ): PendingIntent {
            val intent = Intent(context, Receiver::class.java)
            intent.putExtra(EXTRA_DATA_SOURCE_COMPONENT, dataSource)
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId)

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context,
                complicationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}