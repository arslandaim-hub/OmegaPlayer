package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegaplayer.data.LockedVideo
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.LockerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PinState {
    object Loading : PinState()
    object NotSet : PinState()
    object Required : PinState()
    object Unlocked : PinState()
}

class LockerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = LockerDatabase.getDatabase(application).lockerDao()

    private val _pinState = MutableStateFlow<PinState>(PinState.Loading)
    val pinState: StateFlow<PinState> = _pinState.asStateFlow()

    private val _lockedVideos = MutableStateFlow<List<LockedVideo>>(emptyList())
    val lockedVideos: StateFlow<List<LockedVideo>> = _lockedVideos.asStateFlow()

    private val _expandedFolder = MutableStateFlow<String?>(null)
    val expandedFolder: StateFlow<String?> = _expandedFolder.asStateFlow()

    val settings: StateFlow<LockerSettings?> = dao.getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        checkLockerStatus()
    }

    fun checkLockerStatus() {
        viewModelScope.launch {
            val settings = dao.getSettings()
            if (settings == null) {
                _pinState.value = PinState.NotSet
            } else if (_pinState.value !is PinState.Unlocked) {
                _pinState.value = PinState.Required
            }
        }
    }

    fun unlock(isCorrect: Boolean) {
        if (isCorrect) {
            _pinState.value = PinState.Unlocked
            fetchLockedVideos()
        }
    }

    fun lock() {
        if (_pinState.value is PinState.Unlocked) {
            _pinState.value = PinState.Required
            _expandedFolder.value = null
        }
    }

    fun fetchLockedVideos() {
        viewModelScope.launch {
            _lockedVideos.value = dao.getAllLockedVideos()
        }
    }

    fun setExpandedFolder(folderName: String?) {
        _expandedFolder.value = folderName
    }

    suspend fun getSettings(): LockerSettings? = dao.getSettings()

    fun saveSettings(settings: LockerSettings) {
        viewModelScope.launch {
            dao.saveSettings(settings)
            _pinState.value = PinState.Unlocked
        }
    }
}
