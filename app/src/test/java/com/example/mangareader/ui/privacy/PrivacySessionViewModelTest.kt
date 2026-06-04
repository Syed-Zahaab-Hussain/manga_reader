package com.example.mangareader.ui.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySessionViewModelTest {

    @Test
    fun authenticatedSessionLocksAfterBackgrounding() {
        val viewModel = PrivacySessionViewModel()
        viewModel.initialize(pinExists = true, restoringProtectedContent = false)
        viewModel.onActivityResumed()
        viewModel.markSessionAuthenticated()

        viewModel.onActivityPaused()
        assertTrue(viewModel.state.value.privacyShieldVisible)

        viewModel.onActivityStopped(pinExists = true, changingConfigurations = false)
        assertTrue(viewModel.state.value.requiresUnlock)

        viewModel.onActivityResumed()
        assertFalse(viewModel.state.value.privacyShieldVisible)
        assertTrue(viewModel.state.value.requiresUnlock)

        viewModel.unlock()
        assertFalse(viewModel.state.value.requiresUnlock)
        assertFalse(viewModel.state.value.privacyShieldVisible)
    }

    @Test
    fun configurationChangeDoesNotLockSession() {
        val viewModel = PrivacySessionViewModel()
        viewModel.initialize(pinExists = true, restoringProtectedContent = false)
        viewModel.onActivityResumed()
        viewModel.markSessionAuthenticated()
        viewModel.onActivityPaused()
        viewModel.onActivityStopped(pinExists = true, changingConfigurations = true)

        assertFalse(viewModel.state.value.requiresUnlock)
    }

    @Test
    fun unauthenticatedLoginScreenDoesNotCreateSecondLock() {
        val viewModel = PrivacySessionViewModel()
        viewModel.initialize(pinExists = true, restoringProtectedContent = false)
        viewModel.onActivityResumed()
        viewModel.onActivityPaused()
        viewModel.onActivityStopped(pinExists = true, changingConfigurations = false)

        assertFalse(viewModel.state.value.requiresUnlock)
    }

    @Test
    fun restoredActivityWithPinStartsLocked() {
        val viewModel = PrivacySessionViewModel()
        viewModel.initialize(pinExists = true, restoringProtectedContent = true)

        assertTrue(viewModel.state.value.privacyShieldVisible)
        assertTrue(viewModel.state.value.requiresUnlock)
    }

    @Test
    fun appResetClearsProtectedAndLockedSessionState() {
        val viewModel = PrivacySessionViewModel()
        viewModel.initialize(pinExists = true, restoringProtectedContent = false)
        viewModel.onActivityResumed()
        viewModel.markSessionAuthenticated()
        viewModel.onActivityPaused()
        viewModel.onActivityStopped(pinExists = true, changingConfigurations = false)

        viewModel.resetForSetup()

        assertFalse(viewModel.hasProtectedContent())
        assertFalse(viewModel.state.value.requiresUnlock)
        assertFalse(viewModel.state.value.privacyShieldVisible)
    }
}
