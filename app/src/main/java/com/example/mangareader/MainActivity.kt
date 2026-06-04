package com.example.mangareader

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.mangareader.ui.navigation.AppNavHost
import com.example.mangareader.ui.privacy.PrivacySessionViewModel
import com.example.mangareader.ui.privacy.PrivacyShield
import com.example.mangareader.ui.screens.login.LoginScreen
import com.example.mangareader.ui.theme.MangaReaderTheme

class MainActivity : FragmentActivity() {

    private val privacySession: PrivacySessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MangaReaderApp
        privacySession.initialize(
            pinExists = app.container.pinRepository.hasPin(),
            restoringProtectedContent = savedInstanceState
                ?.getBoolean(KEY_PROTECTED_CONTENT_REACHED, false)
                ?: false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()
        setContent {
            MangaReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    val privacyState by privacySession.state.collectAsStateWithLifecycle()

                    BackHandler(enabled = privacyState.requiresUnlock) {
                        moveTaskToBack(true)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = rememberNavController(),
                            onSessionAuthenticated = privacySession::markSessionAuthenticated,
                            onAppReset = privacySession::resetForSetup
                        )

                        if (privacyState.requiresUnlock && !privacyState.privacyShieldVisible) {
                            LoginScreen(onUnlocked = privacySession::unlock)
                        }

                        if (privacyState.privacyShieldVisible) {
                            PrivacyShield()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        privacySession.onActivityResumed()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onPause() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        privacySession.onActivityPaused()
        super.onPause()
    }

    override fun onUserLeaveHint() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        privacySession.onActivityPaused()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        val app = application as MangaReaderApp
        privacySession.onActivityStopped(
            pinExists = app.container.pinRepository.hasPin(),
            changingConfigurations = isChangingConfigurations
        )
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            KEY_PROTECTED_CONTENT_REACHED,
            privacySession.hasProtectedContent()
        )
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val KEY_PROTECTED_CONTENT_REACHED = "protected_content_reached"
    }
}
