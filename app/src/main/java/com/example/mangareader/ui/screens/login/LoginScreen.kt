package com.example.mangareader.ui.screens.login

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.mangareader.MangaReaderApp
import com.example.mangareader.data.auth.PinRepository
import com.example.mangareader.ui.components.PinKeypad
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val container = remember { (context.applicationContext as MangaReaderApp).container }

    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    val authenticator = remember(activity) { activity?.let { container.biometricAuthenticator(it) } }
    val biometricAvailable = remember(authenticator) {
        authenticator != null && runCatching { authenticator.isAvailable() }.getOrDefault(false)
    }
    val biometricEnabled = remember { container.pinRepository.biometricEnabled }
    val useBiometric = biometricAvailable && biometricEnabled

    fun tryBiometric() {
        val auth = authenticator ?: return
        if (verifying) return
        verifying = true
        auth.authenticate(
            onSuccess = {
                verifying = false
                onUnlocked()
            },
            onFailure = { verifying = false },
            onError = { verifying = false }
        )
    }

    LaunchedEffect(useBiometric) {
        if (useBiometric) {
            delay(300)
            tryBiometric()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Manga Reader",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Enter your PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (errorMessage.isNotEmpty()) errorMessage else "",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
            modifier = Modifier.height(20.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        PinKeypad(
            enteredCount = pin.length,
            total = PinRepository.PIN_LENGTH,
            onDigit = { digit ->
                if (pin.length < PinRepository.PIN_LENGTH && !verifying) {
                    pin += digit
                    if (pin.length == PinRepository.PIN_LENGTH) {
                        verifying = true
                        if (container.pinRepository.verifyPin(pin)) {
                            onUnlocked()
                        } else {
                            errorMessage = "Incorrect PIN. Try again."
                            pin = ""
                            verifying = false
                        }
                    }
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                if (errorMessage.isNotEmpty()) errorMessage = ""
            }
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (useBiometric) {
            BiometricButton(onTap = ::tryBiometric)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BiometricButton(onTap: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onTap,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Use fingerprint",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use Fingerprint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
