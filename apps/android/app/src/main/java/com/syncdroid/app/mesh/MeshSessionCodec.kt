package com.syncdroid.app.mesh

import com.syncdroid.shared.protocol.MeshSessionMessage

typealias FolderClock = com.syncdroid.shared.protocol.FolderClock

object MeshSessionCodec {
    fun encode(message: MeshSessionMessage): ByteArray =
        com.syncdroid.shared.protocol.MeshSessionWireCodec.encode(message)

    fun decode(bytes: ByteArray): MeshSessionMessage =
        com.syncdroid.shared.protocol.MeshSessionWireCodec.decode(bytes)
}
