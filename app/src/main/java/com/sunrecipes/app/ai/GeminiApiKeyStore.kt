package com.sunrecipes.app.ai

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GeminiApiKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_ai", Context.MODE_PRIVATE)
    private val alias = "sun_recipes_gemini_key"

    fun get(): String? {
        val encoded = preferences.getString(KEY, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun save(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun clear() { preferences.edit().remove(KEY).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE).apply {
                init(android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
                generateKey()
            }
        }
        return (store.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    companion object {
        private const val KEY = "gemini_api_key"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM = "AES"
    }
}
