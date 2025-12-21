package com.phad.chatapp.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object SecurityKeyManager {
    private const val TAG = "SecurityKeyManager"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nss_attendance_device_key"

    @Synchronized
    fun ensureKey(): KeyPair {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) {
            return KeyPair(existing.certificate.publicKey, existing.privateKey)
        }

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384)
            .setUserAuthenticationRequired(false)
            .setAttestationChallenge(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(false)
        }

        kpg.initialize(builder.build())
        val kp = kpg.generateKeyPair()
        Log.d(TAG, "Generated new EC key in keystore")
        return kp
    }

    fun getPublicKeyPem(): String {
        val keyPair = ensureKey()
        val pub = keyPair.public as ECPublicKey
        val spkiDer = encodeSpki(pub)
        val b64 = Base64.encodeToString(spkiDer, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n" + b64.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----\n"
    }

    fun signBase64(dataUtf8: String): String {
        val keyPair = ensureKey()
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(keyPair.private)
        sig.update(dataUtf8.toByteArray(Charsets.UTF_8))
        val signature = sig.sign()
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    private fun encodeSpki(pub: ECPublicKey): ByteArray {
        // Use standard X.509 SubjectPublicKeyInfo from the certificate
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey.encoded
    }
}



