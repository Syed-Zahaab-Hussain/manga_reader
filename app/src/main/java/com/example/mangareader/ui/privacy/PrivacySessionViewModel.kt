package com.example.mangareader.ui.privacy

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PrivacySessionState(
    val privacyShieldVisible: Boolean = false,
    val requiresUnlock: Boolean = false
)

class PrivacySessionViewModel : ViewModel() {

    private val _state = MutableStateFlow(PrivacySessionState())
    val state: StateFlow<PrivacySessionState> = _state.asStateFlow()

    private var initialized = false
    private var activityResumed = false
    private var protectedContentReached = false

    fun initialize(pinExists: Boolean, restoringProtectedContent: Boolean) {
        if (initialized) return
        initialized = true
        protectedContentReached = restoringProtectedContent
        if (pinExists && restoringProtectedContent) {
            _state.value = PrivacySessionState(
                privacyShieldVisible = true,
                requiresUnlock = true
            )
        }
    }

    fun markSessionAuthenticated() {
        protectedContentReached = true
    }

    fun onActivityResumed() {
        activityResumed = true
        _state.update { it.copy(privacyShieldVisible = false) }
    }

    fun onActivityPaused() {
        activityResumed = false
        _state.update { it.copy(privacyShieldVisible = true) }
    }

    fun onActivityStopped(pinExists: Boolean, changingConfigurations: Boolean) {
        if (!changingConfigurations && pinExists && protectedContentReached) {
            _state.update { it.copy(requiresUnlock = true) }
        }
    }

    fun unlock() {
        protectedContentReached = true
        _state.update {
            it.copy(
                requiresUnlock = false,
                privacyShieldVisible = !activityResumed
            )
        }
    }

    fun hasProtectedContent(): Boolean = protectedContentReached
}
