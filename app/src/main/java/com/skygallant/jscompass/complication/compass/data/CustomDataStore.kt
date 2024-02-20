package com.skygallant.jscompass.complication.compass.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Simple data storage. Provides DataStore and single preference key to save data.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "JSComplicationCompass")
val BEARING_KEY = intPreferencesKey("data_source_tap_counter")