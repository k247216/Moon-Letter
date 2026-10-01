package com.twomemory.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bearer tokens never sit in prefs as plaintext on a real device: they are
 * sealed with an AES-GCM key that lives in AndroidKeyStore. Environments
 * without the AndroidKeyStore provider (JVM test harnesses) fall back to a
 * clearly marked plaintext prefix so the same code path stays testable.
 */
object TokenCipher {

    private const val KEY_ALIAS = "moon_letter_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_BITS = 128
    private const val PLAIN_PREFIX = "plain:"

    fun seal(plain: String): String {
        val key = obtainKey() ?: return PLAIN_PREFIX + plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(iv.size + sealed.size) { index ->
            if (index < iv.size) iv[index] else sealed[index - iv.size]
        }
        return "enc:" + android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
    }

    fun unseal(stored: String): String {
        if (stored.startsWith(PLAIN_PREFIX)) return stored.removePrefix(PLAIN_PREFIX)
        if (!stored.startsWith("enc:")) return stored // legacy plaintext value
        val key = obtainKey() ?: error("keystore key missing for encrypted token")
        val payload = android.util.Base64.decode(stored.removePrefix("enc:"), android.util.Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val sealed = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun obtainKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        generator.generateKey()
    }.getOrNull()
}
