package com.syncdroid.shared.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveTransferClaimsTest {
    @Test
    fun onlyOneSessionClaimsTheSameFileUntilReleased() {
        val key = activeTransferKey("folder", "file", "ABC123")
        val first = ActiveTransferClaims.claim(listOf(key))
        val second = ActiveTransferClaims.claim(listOf(key))

        assertTrue(first.owns(key))
        assertFalse(second.owns(key))

        first.close()
        val third = ActiveTransferClaims.claim(listOf(key))
        assertTrue(third.owns(key))

        second.close()
        third.close()
    }

    @Test
    fun differentFilesCanBeClaimedConcurrently() {
        val firstKey = activeTransferKey("folder", "one", "hash-one")
        val secondKey = activeTransferKey("folder", "two", "hash-two")
        val first = ActiveTransferClaims.claim(listOf(firstKey))
        val second = ActiveTransferClaims.claim(listOf(secondKey))

        assertTrue(first.owns(firstKey))
        assertTrue(second.owns(secondKey))

        first.close()
        second.close()
    }
}
