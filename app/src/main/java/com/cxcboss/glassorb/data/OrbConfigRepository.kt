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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.orbConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "orb_config")

enum class ConfigGroup { Geometry, Glass, Container, Wave, Dots, Motion, Performance }

/**
 * Process-wide owner of the live [OrbConfig].
 *
 * The in-memory snapshot is the source of truth: settings sliders publish into
 * it immediately so the overlay follows the finger on the next frame, and the
 * debounced disk write runs in this repository's own process-scoped coroutine
 * scope. Persistence therefore survives the settings Activity/ViewModel being
 * destroyed right after the last drag. DataStore is only read once at start-up;
 * this process is its single writer, so later emissions can never be newer than
 * the in-memory value and are not allowed to overwrite it.
 */
class OrbConfigRepository private constructor(context: Context) {
    private val dataStore = context.applicationContext.orbConfigDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeConfig = MutableStateFlow(OrbConfig.reference())
    private val generation = AtomicLong(0L)

    @Volatile
    private var userPublished = false
    private var persistJob: Job? = null

    val config: StateFlow<OrbConfig> = runtimeConfig.asStateFlow()

    init {
        scope.launch {
            val persisted = try {
                dataStore.data.first()[CONFIG_JSON]
                    ?.let(OrbConfigJson::decode)
                    ?.getOrNull()
            } catch (_: IOException) {
                null
            } ?: return@launch
            // A slider may have been moved before the initial read finished;
            // the user's newer value always wins.
            if (!userPublished) runtimeConfig.value = persisted.normalized()
        }
    }

    /** Publish a normalized runtime snapshot without waiting for DataStore IO. */
    fun publish(newConfig: OrbConfig) {
        generation.incrementAndGet()
        userPublished = true
        runtimeConfig.value = newConfig.normalized()
    }

    /**
     * Publishes immediately and schedules one debounced disk write. Only the
     * newest value of a burst of calls is persisted.
     */
    @Synchronized
    fun publishAndPersist(newConfig: OrbConfig, debounceMillis: Long = PERSIST_DEBOUNCE_MS) {
        publish(newConfig)
        val snapshot = runtimeConfig.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(debounceMillis)
            write(snapshot)
        }
    }

    suspend fun update(transform: (OrbConfig) -> OrbConfig) {
        replace(transform(runtimeConfig.value).normalized())
    }

    suspend fun replace(newConfig: OrbConfig) {
        publish(newConfig)
        write(runtimeConfig.value)
    }

    suspend fun reset(group: ConfigGroup) {
        update { current -> current.resetGroup(group) }
    }

    private suspend fun write(snapshot: OrbConfig) {
        dataStore.edit { it[CONFIG_JSON] = OrbConfigJson.encode(snapshot) }
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 70L

        @Volatile
        private var instance: OrbConfigRepository? = null

        fun getInstance(context: Context): OrbConfigRepository =
            instance ?: synchronized(this) {
                instance ?: OrbConfigRepository(context).also { instance = it }
            }

        private val CONFIG_JSON = stringPreferencesKey("config_json_v1")
    }
}

/** Restores one settings group to the reference defaults, leaving the rest untouched. */
fun OrbConfig.resetGroup(group: ConfigGroup): OrbConfig {
    val defaults = OrbConfig.reference()
    return when (group) {
        ConfigGroup.Geometry -> copy(geometry = defaults.geometry)
        ConfigGroup.Glass -> copy(glass = defaults.glass)
        ConfigGroup.Container -> copy(container = defaults.container)
        ConfigGroup.Wave -> copy(wave = defaults.wave)
        ConfigGroup.Dots -> copy(dots = defaults.dots)
        ConfigGroup.Motion -> copy(motion = defaults.motion)
        ConfigGroup.Performance -> copy(performance = defaults.performance)
    }
}
