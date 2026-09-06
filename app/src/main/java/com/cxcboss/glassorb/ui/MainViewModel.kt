package com.cxcboss.glassorb.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.data.OrbConfigRepository
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.model.OrbConfigJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OrbConfigRepository.getInstance(application)
    private val mutableConfig = MutableStateFlow(repository.config.value)
    private var pendingSave: Job? = null

    val config: StateFlow<OrbConfig> = mutableConfig.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collectLatest { stored ->
                if (pendingSave?.isActive != true) mutableConfig.value = stored
            }
        }
    }

    fun update(newConfig: OrbConfig) {
        val normalized = newConfig.normalized()
        mutableConfig.value = normalized
        // Keep the overlay process in the same process frame as the slider.
        // DataStore remains debounced below for persistence, but waiting for
        // that IO path makes window geometry visibly lag behind the finger.
        repository.publish(normalized)
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(70)
            repository.replace(normalized)
        }
    }

    fun applyPreset(preset: ConfigPreset) {
        update(
            when (preset) {
                ConfigPreset.Reference -> OrbConfig.reference()
                ConfigPreset.Soft -> OrbConfig.soft()
                ConfigPreset.Bright -> OrbConfig.bright()
            },
        )
    }

    fun reset(group: ConfigGroup) {
        val current = mutableConfig.value
        val defaults = OrbConfig.reference()
        update(
            when (group) {
                ConfigGroup.Geometry -> current.copy(geometry = defaults.geometry)
                ConfigGroup.Glass -> current.copy(glass = defaults.glass)
                ConfigGroup.Container -> current.copy(container = defaults.container)
                ConfigGroup.Wave -> current.copy(wave = defaults.wave)
                ConfigGroup.Dots -> current.copy(dots = defaults.dots)
                ConfigGroup.Motion -> current.copy(motion = defaults.motion)
                ConfigGroup.Performance -> current.copy(performance = defaults.performance)
            },
        )
    }

    fun resetAll() = update(OrbConfig.reference())

    fun exportJson(): String = OrbConfigJson.encode(mutableConfig.value)

    fun importJson(json: String, onResult: (Result<OrbConfig>) -> Unit) {
        val result = OrbConfigJson.decode(json)
        result.getOrNull()?.let(::update)
        onResult(result)
    }
}
