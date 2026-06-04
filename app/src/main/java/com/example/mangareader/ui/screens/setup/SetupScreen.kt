package com.example.mangareader.ui.screens.setup

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.data.auth.PinRepository
import com.example.mangareader.ui.components.PinKeypad
import com.example.mangareader.ui.setup.SetupStep
import com.example.mangareader.ui.setup.SetupViewModel

@Composable
fun SetupScreen(
    changeMode: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit = {},
    viewModel: SetupViewModel = viewModel(
        key = "setup_$changeMode",
        factory = SetupViewModel.factory(changeMode)
    )
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = remember(context) { context.findFragmentActivity() }
    val container = remember { (context.applicationContext as MangaReaderApp).container }
    val biometricAvailable = remember(activity) {
        activity?.let { container.biometricAuthenticator(it) }
            ?.let { runCatching { it.isAvailable() }.getOrDefault(false) }
            ?: false
    }

    LaunchedEffect(state.completed) {
        if (state.completed) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (changeMode) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = titleFor(state.step, changeMode),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitleFor(state.step),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.errorMessage.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        PinKeypad(
            enteredCount = state.enteredPin.length,
            total = PinRepository.PIN_LENGTH,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace
        )

        if (!changeMode && state.step != SetupStep.CURRENT_PIN && biometricAvailable) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text("Fingerprint Login", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use your enrolled fingerprint to unlock the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.biometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled
                )
            }
        }

        if (state.step == SetupStep.CONFIRM_PIN) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = viewModel::goToPreviousStep) {
                Text("Back to create PIN")
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

private fun titleFor(step: SetupStep, changeMode: Boolean): String = when (step) {
    SetupStep.CURRENT_PIN -> "Enter Current PIN"
    SetupStep.NEW_PIN -> if (changeMode) "Create New PIN" else "Create Your PIN"
    SetupStep.CONFIRM_PIN -> "Confirm Your PIN"
}

private fun subtitleFor(step: SetupStep): String = when (step) {
    SetupStep.CURRENT_PIN -> "Verify your existing 4-digit PIN."
    SetupStep.NEW_PIN -> "Choose a 4-digit PIN to protect Manga Reader."
    SetupStep.CONFIRM_PIN -> "Enter the same PIN one more time."
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
