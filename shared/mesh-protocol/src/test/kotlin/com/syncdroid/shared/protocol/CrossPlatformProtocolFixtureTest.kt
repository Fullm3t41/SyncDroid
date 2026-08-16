package com.syncdroid.shared.protocol

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class CrossPlatformProtocolFixtureTest {
    private val fixture = loadFixture()
    private val version = VersionVector.fromJson(fixture.value("versionVector.json"))

    @Test
    fun versionVectorHasStableCanonicalJson() {
        assertEquals(fixture.value("versionVector.json"), version.toJson())
        assertEquals(CausalRelation.Concurrent, version.relationTo(VersionVector(mapOf("another" to 1))))
    }

    @Test
    fun chatPayloadAndIdentifierMatchGoldenBytes() {
        val payload = canonicalChatPayload(
            fixture.value("chat.groupId"),
            fixture.value("chat.authorDeviceId"),
            fixture.value("chat.body"),
            fixture.value("chat.createdAtMillis").toLong(),
        )
        assertEquals(fixture.value("chat.payloadHex"), payload.toHex())
        assertEquals(fixture.value("chat.eventId"), eventIdFor(payload))
    }

    @Test
    fun membershipPayloadMatchesGoldenBytes() {
        val payload = canonicalMembershipPayload(
            fixture.value("chat.groupId"),
            fixture.value("membership.eventType"),
            fixture.value("membership.subjectDeviceId"),
            fixture.value("membership.subjectDisplayName"),
            fixture.value("membership.subjectPublicKeyBase64"),
            fixture.value("chat.authorDeviceId"),
            fixture.csv("membership.parents"),
            version.toJson(),
            fixture.value("chat.createdAtMillis").toLong(),
        )
        assertEquals(fixture.value("membership.payloadHex"), payload.toHex())
        assertEquals(fixture.value("membership.eventId"), eventIdFor(payload))
    }

    @Test
    fun folderPayloadMatchesGoldenBytes() {
        val payload = canonicalFolderAnnouncementPayload(
            fixture.value("chat.groupId"),
            fixture.value("folder.folderId"),
            fixture.value("folder.displayName"),
            fixture.csv("folder.includes"),
            fixture.csv("folder.excludes"),
            fixture.value("chat.authorDeviceId"),
            version.toJson(),
            fixture.value("chat.createdAtMillis").toLong(),
        )
        assertEquals(fixture.value("folder.payloadHex"), payload.toHex())
        assertEquals(fixture.value("folder.eventId"), eventIdFor(payload))
    }
}

private fun loadFixture(): Properties {
    val file = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("protocol/fixtures/shared-core-v1.properties") }
        .first(Files::isRegularFile)
    return Properties().apply { Files.newInputStream(file).use(::load) }
}

private fun Properties.value(key: String): String = requireNotNull(getProperty(key)) { "Missing fixture $key" }
private fun Properties.csv(key: String): List<String> = value(key).split(',').filter(String::isNotEmpty)
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
