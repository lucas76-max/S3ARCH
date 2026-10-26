package com.s3arch.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "s3arch")

/** Persistance des cles API, des reglages et de la liste des telechargements suivis. */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            c411Key = prefs[KEY_C411] ?: "",
            c411Url = prefs[URL_C411] ?: Tracker.C411.defaultUrl,
            tr4kerKey = prefs[KEY_TR4KER] ?: "",
            tr4kerUrl = prefs[URL_TR4KER] ?: Tracker.TR4KER.defaultUrl,
            v3xKey = prefs[KEY_V3X] ?: "",
            v3xUrl = prefs[URL_V3X] ?: Tracker.V3X.defaultUrl,
            enabledTrackers = prefs[ENABLED] ?: Tracker.entries.map { it.id }.toSet(),
            freeboxHost = prefs[FB_HOST] ?: "mafreebox.freebox.fr",
            freeboxApiBase = prefs[FB_BASE] ?: "/api/v8",
            freeboxApiVersion = prefs[FB_VERSION] ?: "8",
            freeboxAppToken = prefs[FB_TOKEN] ?: "",
            freeboxDownloadDir = prefs[FB_DIR] ?: "/Disque dur/S3ARCH",
            onboardingDone = prefs[ONBOARDING] ?: false,
        )
    }

    suspend fun saveApiKeys(keys: Map<Tracker, String>, urls: Map<Tracker, String>) {
        context.dataStore.edit { prefs ->
            keys[Tracker.C411]?.let { prefs[KEY_C411] = it.trim() }
            keys[Tracker.TR4KER]?.let { prefs[KEY_TR4KER] = it.trim() }
            keys[Tracker.V3X]?.let { prefs[KEY_V3X] = it.trim() }
            urls[Tracker.C411]?.let { prefs[URL_C411] = it.trim() }
            urls[Tracker.TR4KER]?.let { prefs[URL_TR4KER] = it.trim() }
            urls[Tracker.V3X]?.let { prefs[URL_V3X] = it.trim() }
        }
    }

    suspend fun setEnabledTrackers(ids: Set<String>) {
        context.dataStore.edit { it[ENABLED] = ids }
    }

    suspend fun saveFreebox(host: String, apiBase: String, apiVersion: String, appToken: String, downloadDir: String) {
        context.dataStore.edit { prefs ->
            prefs[FB_HOST] = host.trim()
            prefs[FB_BASE] = apiBase.trim()
            prefs[FB_VERSION] = apiVersion.trim()
            if (appToken.isNotBlank()) prefs[FB_TOKEN] = appToken.trim()
            prefs[FB_DIR] = downloadDir.trim()
        }
    }

    suspend fun setDownloadDir(dir: String) {
        context.dataStore.edit { it[FB_DIR] = dir.trim() }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[ONBOARDING] = true }
    }

    private val trackedKey = stringPreferencesKey("tracked_downloads")

    val downloads: Flow<List<TrackedDownload>> = context.dataStore.data.map { prefs ->
        val raw = prefs[trackedKey] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(TrackedDownload.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun addDownload(download: TrackedDownload) {
        context.dataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString(ListSerializer(TrackedDownload.serializer()), prefs[trackedKey] ?: "[]")
            }.getOrDefault(emptyList())
            val updated = (listOf(download) + current.filterNot { it.taskId == download.taskId }).take(60)
            prefs[trackedKey] = json.encodeToString(ListSerializer(TrackedDownload.serializer()), updated)
        }
    }

    suspend fun removeDownload(taskId: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString(ListSerializer(TrackedDownload.serializer()), prefs[trackedKey] ?: "[]")
            }.getOrDefault(emptyList())
            prefs[trackedKey] = json.encodeToString(
                ListSerializer(TrackedDownload.serializer()),
                current.filterNot { it.taskId == taskId },
            )
        }
    }

    private companion object {
        val KEY_C411 = stringPreferencesKey("c411_key")
        val KEY_TR4KER = stringPreferencesKey("tr4ker_key")
        val KEY_V3X = stringPreferencesKey("v3x_key")
        val URL_C411 = stringPreferencesKey("c411_url")
        val URL_TR4KER = stringPreferencesKey("tr4ker_url")
        val URL_V3X = stringPreferencesKey("v3x_url")
        val ENABLED = stringSetPreferencesKey("enabled_trackers")
        val FB_HOST = stringPreferencesKey("freebox_host")
        val FB_BASE = stringPreferencesKey("freebox_api_base")
        val FB_VERSION = stringPreferencesKey("freebox_version")
        val FB_TOKEN = stringPreferencesKey("freebox_app_token")
        val FB_DIR = stringPreferencesKey("freebox_download_dir")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }
}
