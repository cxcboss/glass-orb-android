package com.cxcboss.glassorb.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.OrbConfigRepository
import com.cxcboss.glassorb.data.resetGroup
import com.cxcboss.glassorb.model.OrbConfig
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OrbConfigRepository.getInstance(application)

    /** The repository snapshot is already the live value; no second copy is kept. */
    val config: StateFlow<OrbConfig> = repository.config

    fun update(newConfig: OrbConfig) {
        // The overlay follows the same in-memory snapshot on its next frame.
        // Disk writes are debounced inside the repository's own scope so the
        // last value is still persisted after this ViewModel is cleared.
        repository.publishAndPersist(newConfig)
    }

    fun reset(group: ConfigGroup) = update(repository.config.value.resetGroup(group))

    fun resetAll() = update(OrbConfig.reference())
}
