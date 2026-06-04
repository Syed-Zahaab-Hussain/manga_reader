package com.example.mangareader.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.data.auth.PinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SetupStep {
    CURRENT_PIN,
    NEW_PIN,
    CONFIRM_PIN
}

data class SetupUiState(
    val changeMode: Boolean,
    val step: SetupStep = if (changeMode) SetupStep.CURRENT_PIN else SetupStep.NEW_PIN,
    val enteredPin: String = "",
    val newPin: String? = null,
    val biometricEnabled: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false
)

class SetupViewModel(
    private val pinRepository: PinRepository,
    changeMode: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState(changeMode = changeMode))
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        if (!digit.isDigit() || _uiState.value.completed) return
        val state = _uiState.value
        if (state.enteredPin.length >= PinRepository.PIN_LENGTH) return
        val updatedPin = state.enteredPin + digit
        _uiState.update { it.copy(enteredPin = updatedPin, errorMessage = null) }
        if (updatedPin.length == PinRepository.PIN_LENGTH) submitPin(updatedPin)
    }

    fun onBackspace() {
        _uiState.update { state ->
            state.copy(
                enteredPin = state.enteredPin.dropLast(1),
                errorMessage = null
            )
        }
    }

    fun goToPreviousStep() {
        _uiState.update { state ->
            when (state.step) {
                SetupStep.CONFIRM_PIN -> state.copy(
                    step = SetupStep.NEW_PIN,
                    enteredPin = "",
                    newPin = null,
                    errorMessage = null
                )
                SetupStep.NEW_PIN -> if (state.changeMode) {
                    state.copy(
                        step = SetupStep.CURRENT_PIN,
                        enteredPin = "",
                        newPin = null,
                        errorMessage = null
                    )
                } else {
                    state
                }
                SetupStep.CURRENT_PIN -> state
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    private fun submitPin(pin: String) {
        when (_uiState.value.step) {
            SetupStep.CURRENT_PIN -> verifyCurrentPin(pin)
            SetupStep.NEW_PIN -> _uiState.update {
                it.copy(
                    step = SetupStep.CONFIRM_PIN,
                    enteredPin = "",
                    newPin = pin,
                    errorMessage = null
                )
            }
            SetupStep.CONFIRM_PIN -> confirmNewPin(pin)
        }
    }

    private fun verifyCurrentPin(pin: String) {
        if (pinRepository.verifyPin(pin)) {
            _uiState.update {
                it.copy(
                    step = SetupStep.NEW_PIN,
                    enteredPin = "",
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    enteredPin = "",
                    errorMessage = "Incorrect current PIN. Try again."
                )
            }
        }
    }

    private fun confirmNewPin(pin: String) {
        val state = _uiState.value
        if (pin != state.newPin) {
            _uiState.update {
                it.copy(
                    step = SetupStep.NEW_PIN,
                    enteredPin = "",
                    newPin = null,
                    errorMessage = "PINs did not match. Create the PIN again."
                )
            }
            return
        }

        pinRepository.savePin(pin)
        if (!state.changeMode) {
            pinRepository.biometricEnabled = state.biometricEnabled
        }
        _uiState.update {
            it.copy(enteredPin = "", errorMessage = null, completed = true)
        }
    }

    companion object {
        fun factory(changeMode: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MangaReaderApp
                SetupViewModel(app.container.pinRepository, changeMode)
            }
        }
    }
}
