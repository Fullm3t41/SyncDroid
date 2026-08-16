package com.syncdows.app.platform

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.swing.SwingUtilities

class SingleInstanceGuard private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    @Volatile private var activationHandler: (() -> Unit)? = null
    private val activationServer = runCatching {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(LOOPBACK, ACTIVATION_PORT))
        }
    }.getOrNull()
    private val activationThread = activationServer?.let { server ->
        Thread({
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        if (socket.getInputStream().read() == ACTIVATE_MESSAGE) {
                            SwingUtilities.invokeLater { activationHandler?.invoke() }
                        }
                    }
                }.onFailure { if (server.isClosed) return@Thread }
            }
        }, "syncdows-activation").apply {
            isDaemon = true
            start()
        }
    }

    fun onActivate(handler: () -> Unit) {
        activationHandler = handler
    }

    override fun close() {
        activationHandler = null
        runCatching { activationServer?.close() }
        runCatching(lock::release)
        runCatching(channel::close)
    }

    companion object {
        fun acquire(): SingleInstanceGuard? {
            Files.createDirectories(WindowsAppPaths.applicationData)
            val channel = FileChannel.open(
                WindowsAppPaths.applicationData.resolve("syncdows.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                notifyExistingInstance()
                return null
            }
            return SingleInstanceGuard(channel, lock)
        }

        private fun notifyExistingInstance() {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(LOOPBACK, ACTIVATION_PORT), 1_000)
                    socket.getOutputStream().write(ACTIVATE_MESSAGE)
                    socket.getOutputStream().flush()
                }
            }
        }

        private val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
        private const val ACTIVATION_PORT = 45_783
        private const val ACTIVATE_MESSAGE = 1
    }
}
