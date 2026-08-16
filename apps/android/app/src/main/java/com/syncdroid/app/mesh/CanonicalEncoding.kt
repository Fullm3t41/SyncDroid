package com.syncdroid.app.mesh

import com.syncdroid.shared.protocol.canonicalBytes as sharedCanonicalBytes

internal typealias CanonicalOutput = com.syncdroid.shared.protocol.CanonicalOutput

/** Language-neutral canonical bytes used for hashes and signatures. */
internal fun canonicalBytes(block: CanonicalOutput.() -> Unit): ByteArray = sharedCanonicalBytes(block)
