package com.syncdroid.app.mesh

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.cert.X509Certificate

class AndroidDeviceIdentity(
    private val alias: String = "syncdroid-device-identity-v1",
) : DeviceSigner {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override val publicKey: PublicKey
        get() {
            ensureKeyExists()
            return keyStore.getCertificate(alias).publicKey
        }

    private val privateKey: PrivateKey
        get() {
            ensureKeyExists()
            return keyStore.getKey(alias, null) as PrivateKey
        }

    override val deviceId: String
        get() = deviceIdFor(publicKey)

    val fingerprint: String
        get() = fingerprintFor(publicKey)

    override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey)
        update(payload)
        sign()
    }

    internal val tlsPublicKey: PublicKey
        get() {
            ensureTlsKeyExists()
            return keyStore.getCertificate(TLS_ALIAS).publicKey
        }

    internal fun tlsPrivateKey(): PrivateKey {
        ensureTlsKeyExists()
        return keyStore.getKey(TLS_ALIAS, null) as PrivateKey
    }

    internal fun tlsCertificateChain(): Array<X509Certificate> {
        ensureTlsKeyExists()
        return keyStore.getCertificateChain(TLS_ALIAS).map { it as X509Certificate }.toTypedArray()
    }

    private fun ensureKeyExists() {
        if (keyStore.containsAlias(alias)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private fun ensureTlsKeyExists() {
        if (keyStore.containsAlias(TLS_ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    TLS_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TLS_ALIAS = "syncdroid-device-tls-v1"
    }
}
