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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.orbConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "orb_config")

enum class ConfigPreset { Reference, Soft, Bright }

enum class ConfigGroup { Geometry, Glass, Container, Wave, Dots, Motion, Performance }

class OrbConfigRepository private constructor(context: Context) {
    private val dataStore = context.applicationContext.orbConfigDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config: StateFlow<OrbConfig> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[CONFIG_JSON]
                ?.let(OrbConfigJson::decode)
                ?.getOrNull()
                ?: OrbConfig.reference()
        }
        .stateIn(scope, SharingStarted.Eagerly, OrbConfig.reference())

    suspend fun update(transform: (OrbConfig) -> OrbConfig) {
        dataStore.edit { preferences ->
            val current = preferences[CONFIG_JSON]
                ?.let(OrbConfigJson::decode)
                ?.getOrNull()
                ?: OrbConfig.reference()
            preferences[CONFIG_JSON] = OrbConfigJson.encode(transform(current).normalized())
        }
    }

    suspend fun replace(newConfig: OrbConfig) {
        dataStore.edit { it[CONFIG_JSON] = OrbConfigJson.encode(newConfig.normalized()) }
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

    fun exportJson(): String = OrbConfigJson.encode(config.value)

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
