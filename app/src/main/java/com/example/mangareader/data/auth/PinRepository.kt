package com.example.mangareader.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasPin(): Boolean =
        prefs.getString(KEY_PIN, null)?.length == PIN_LENGTH

    fun savePin(pin: String) {
        require(pin.length == PIN_LENGTH) { "PIN must be $PIN_LENGTH digits" }
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun verifyPin(pin: String): Boolean =
        hasPin() && prefs.getString(KEY_PIN, null) == pin

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(enabled) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val FILE_NAME = "manga_reader_secure_prefs"
        private const val KEY_PIN = "user_pin"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
