package com.cxcboss.glassorb.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.model.OrbConfigJson
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

private val Context.orbConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "orb_config")

enum class ConfigPreset { Reference, Soft, Bright }

enum class ConfigGroup { Geometry, Glass, Container, Wave, Dots, Motion, Performance }

class OrbConfigRepository private constructor(context: Context) {
    private val dataStore = context.applicationContext.orbConfigDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeConfig = MutableStateFlow(OrbConfig.reference())

    // Slider gestures publish here immediately so the overlay window follows
    // geometry changes on the next frame. Disk writes remain debounced by the
    // ViewModel and must not temporarily overwrite that live snapshot.
    @Volatile
    private var hasPendingRuntimeConfig = false
    @Volatile
    private var publishGeneration = 0L

    val config: StateFlow<OrbConfig> = runtimeConfig.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .map { preferences ->
                    preferences[CONFIG_JSON]
                        ?.let(OrbConfigJson::decode)
                        ?.getOrNull()
                        ?: OrbConfig.reference()
                }
                .collect { persisted ->
                    if (!hasPendingRuntimeConfig) runtimeConfig.value = persisted
                }
        }
    }

    /** Publish a normalized runtime snapshot without waiting for DataStore IO. */
    fun publish(newConfig: OrbConfig) {
        publishGeneration += 1L
        hasPendingRuntimeConfig = true
        runtimeConfig.value = newConfig.normalized()
    }

    suspend fun update(transform: (OrbConfig) -> OrbConfig) {
        replace(transform(runtimeConfig.value).normalized())
    }

    suspend fun replace(newConfig: OrbConfig) {
        val normalized = newConfig.normalized()
        publish(normalized)
        val writeGeneration = publishGeneration
        dataStore.edit { it[CONFIG_JSON] = OrbConfigJson.encode(normalized) }
        // A newer slider sample may have arrived while this disk write was in
        // flight. Never let the old snapshot re-enable DataStore propagation.
        if (publishGeneration == writeGeneration) hasPendingRuntimeConfig = false
    }

    suspend fun applyPreset(preset: ConfigPreset) {
        replace(
            when (preset) {
                ConfigPreset.Reference -> OrbConfig.reference()
                ConfigPreset.Soft -> OrbConfig.soft()
                ConfigPreset.Bright -> OrbConfig.bright()
            },
        )
    }

    suspend fun reset(group: ConfigGroup) {
        update { current ->
            val defaults = OrbConfig.reference()
            when (group) {
                ConfigGroup.Geometry -> current.copy(geometry = defaults.geometry)
                ConfigGroup.Glass -> current.copy(glass = defaults.glass)
                ConfigGroup.Container -> current.copy(container = defaults.container)
                ConfigGroup.Wave -> current.copy(wave = defaults.wave)
                ConfigGroup.Dots -> current.copy(dots = defaults.dots)
                ConfigGroup.Motion -> current.copy(motion = defaults.motion)
                ConfigGroup.Performance -> current.copy(performance = defaults.performance)
            }
        }
    }

    fun exportJson(): String = OrbConfigJson.encode(runtimeConfig.value)

    suspend fun importJson(json: String): Result<OrbConfig> {
        val parsed = OrbConfigJson.decode(json)
        parsed.getOrNull()?.let { replace(it) }
        return parsed
    }

    companion object {
        @Volatile
        private var instance: OrbConfigRepository? = null

        fun getInstance(context: Context): OrbConfigRepository =
            instance ?: synchronized(this) {
                instance ?: OrbConfigRepository(context).also { instance = it }
            }

        private val CONFIG_JSON = stringPreferencesKey("config_json_v1")
    }
}
