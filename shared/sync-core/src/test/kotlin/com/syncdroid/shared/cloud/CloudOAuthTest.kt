package com.syncdroid.shared.cloud

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudOAuthTest {
    @Test
    fun encryptedTokenStoreRoundTripsAndClears() {
        val values = mutableMapOf<CloudProvider, String>()
        val store = EncryptedCloudTokenStore(
            LocalSecretCipher(ByteArray(96).also(SecureRandom()::nextBytes), "test"),
            values::get,
            { provider, value -> if (value == null) values.remove(provider) else values[provider] = value },
        )
        val expected = CloudOAuthTokens("access", "refresh", 123456L, setOf("scope-a", "scope-b"))
        store.save(CloudProvider.GOOGLE_DRIVE, expected)
        assertEquals(expected, store.load(CloudProvider.GOOGLE_DRIVE))
        store.clear(CloudProvider.GOOGLE_DRIVE)
        assertNull(store.load(CloudProvider.GOOGLE_DRIVE))
    }
}
