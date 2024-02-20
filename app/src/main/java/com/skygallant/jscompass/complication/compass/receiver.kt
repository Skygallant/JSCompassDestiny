package com.skygallant.jscompass.complication.compass

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.skygallant.jscompass.complication.compass.Service.Companion.initDest
import com.skygallant.jscompass.complication.compass.Service.Companion.initLoc
import com.skygallant.jscompass.complication.compass.data.BEARING_KEY
import com.skygallant.jscompass.complication.compass.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Receiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun doCompass(gotCon: Context): Int {
        var bearing = 0f

        if (checkPermission(gotCon)) {
            if (initLoc) {
                if (initDest) {
                    bearing = myLocation.bearingTo(destiny)
                    bearing = (360 - bearing) % 360
                    val text = destiny.provider.toString()
                    val duration = Toast.LENGTH_SHORT
                    val toast = Toast.makeText(gotCon, text, duration)
                    toast.show()
                } else {
                    if (Service.Fate) {
                        WorkManager.getInstance(gotCon)
                            .enqueue(OneTimeWorkRequestBuilder<DestinyWorker>().build())
                        Service.Fate = false
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
                context.dataStore.edit { preferences ->



                    preferences[BEARING_KEY] = doCompass(context)



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
        var myLocation = Location("Google Maps API")
        var destiny = Location("Google Maps API")

        fun checkPermission(thisContext: Context): Boolean {
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