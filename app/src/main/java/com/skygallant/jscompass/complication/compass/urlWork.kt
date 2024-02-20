package com.skygallant.jscompass.complication.compass

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.gson.Gson
import com.skygallant.jscompass.complication.compass.Service.Companion.initDest
import java.io.InputStreamReader
import java.net.URL

class DestinyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private var geofenceList = mutableListOf<Geofence>()

    private fun getGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    override fun doWork(): Result {

        data class PlusCode(
            val compound_code: String,
            val global_code: String
        )

        data class Photo(
            val height: Int,
            val html_attributions: List<String>,
            val photo_reference: String,
            val width: Int
        )

        data class OpeningHours(
            val open_now: Boolean
        )

        data class Viewport(
            val northeast: Location,
            val southwest: Location
        )

        data class Location(
            val lat: Double,
            val lng: Double
        )

        data class Geometry(
            val location: Location,
            val viewport: Viewport
        )

        data class Result(
            val business_status: String,
            val geometry: Geometry,
            val icon: String,
            val name: String,
            val opening_hours: OpeningHours,
            val photos: List<Photo>,
            val place_id: String,
            val plus_code: PlusCode,
            val rating: Double,
            val reference: String,
            val scope: String,
            val types: List<String>,
            val user_ratings_total: Int,
            val vicinity: String
        )

        data class TopLevel(
            val html_attributions: List<String>,
            val next_page_token: String,
            val results: List<Result>,
            val status: String
        )


        Log.d(TAG, "url")
        val url = URL(
            """
                |https://maps.googleapis.com/maps/api/place/nearbysearch/json
                |?opennow
                |&location=${Receiver.myLocation.latitude}%2C${Receiver.myLocation.longitude}
                |&radius=1000
                |&key=${BuildConfig.GOOGLE_MAPS_API_KEY}
                """.trimMargin()
        )
        Log.d(TAG, "reader")
        val reader = InputStreamReader(url.openStream())
        Log.d(TAG, "mapsXML")
        val mapsXML = Gson().fromJson(reader, TopLevel::class.java)

        Log.d(TAG, "random")
        val myDestiny = mapsXML.results.random()

        Log.d(TAG, "output")
        //Log.d(TAG, "${myDestiny.geometry.location.lat}")
        //Log.d(TAG, "${myDestiny.geometry.location.lng}")
        Receiver.destiny.latitude = myDestiny.geometry.location.lat
        Receiver.destiny.longitude = myDestiny.geometry.location.lng
        Log.d(TAG, myDestiny.name)
        Receiver.destiny.provider = myDestiny.name
        initDest = true

        Log.d(TAG, "geofencing")
        geofenceList.clear()

        geofenceList.add(
            Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId(Receiver.destiny.provider!!)

                // Set the circular region of this geofence.
                .setCircularRegion(
                    Receiver.destiny.latitude,
                    Receiver.destiny.longitude,
                    R.string.GEOFENCE_RADIUS_IN_METERS.toFloat()
                )

                // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)

                // Create the geofence.
                .build()
        )
        if (Receiver.checkPermission(applicationContext)) {
            Service.geofencingClient.addGeofences(
                getGeofencingRequest(),
                Service.getGeoIntent(applicationContext))
        } else {
            Log.d(TAG, "no geofence")
        }
        return ListenableWorker.Result.success()
    }
}