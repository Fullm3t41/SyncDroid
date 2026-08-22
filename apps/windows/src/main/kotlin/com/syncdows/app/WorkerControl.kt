package com.syncdows.app

import com.syncdows.app.platform.WindowsAppPaths
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class WorkerCommand { PING, SHOW, UI_STARTED, UI_CLOSED, QUIT }

internal data class WorkerEndpoint(val port: Int, val token: String) {
    fun send(command: WorkerCommand): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = CONNECT_TIMEOUT_MILLIS
            socket.getOutputStream().bufferedWriter().apply {
                append(token).append(' ').append(command.name).append('\n')
                flush()
            }
            check(socket.getInputStream().bufferedReader().readLine() == "OK")
        }
        true
    }.getOrDefault(false)

    fun save() {
        val target = WindowsAppPaths.workerEndpoint
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "worker", ".endpoint.tmp")
        try {
            Files.writeString(temporary, "$port\n$token\n")
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun deleteIfCurrent() {
        runCatching { if (load() == this) Files.deleteIfExists(WindowsAppPaths.workerEndpoint) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 1_500

        fun load(): WorkerEndpoint? = runCatching {
            val lines = Files.readAllLines(WindowsAppPaths.workerEndpoint)
            WorkerEndpoint(lines[0].toInt(), lines[1]).takeIf {
                it.port in 1..65_535 && it.token.matches(Regex("[A-Za-z0-9_-]{32,128}"))
            }
        }.getOrNull()

        fun fromEnvironmentOrArguments(args: Array<String>): WorkerEndpoint? {
            val port = (System.getenv(WORKER_PORT_ENV) ?: args.argumentValue("--worker-port"))?.toIntOrNull()
                ?: return null
            val token = System.getenv(WORKER_TOKEN_ENV) ?: args.argumentValue("--worker-token") ?: return null
            return WorkerEndpoint(port, token)
        }

        fun create(port: Int): WorkerEndpoint {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            return WorkerEndpoint(port, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }
    }
}

internal const val WORKER_PORT_ENV = "SYNCDOWS_WORKER_PORT"
internal const val WORKER_TOKEN_ENV = "SYNCDOWS_WORKER_TOKEN"

internal class WorkerInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun tryAcquire(path: java.nio.file.Path): WorkerInstanceLock? {
            Files.createDirectories(requireNotNull(path.parent))
            val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            return try {
                val lock = channel.tryLock()
                if (lock == null) {
                    channel.close()
                    return null
                }
                WorkerInstanceLock(channel, lock)
            } catch (_: OverlappingFileLockException) {
                channel.close()
                null
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }
    }
}

internal class WorkerControlServer(
    private val readTimeoutMillis: Int = 1_500,
    private val onCommand: (WorkerCommand) -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val endpoint: WorkerEndpoint = WorkerEndpoint.create(server.localPort)
    private val acceptJob: Job = scope.launch {
        while (isActive && !server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: continue
            scope.launch { runCatching { handle(socket) } }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = readTimeoutMillis
            val parts = it.getInputStream().bufferedReader().readLine()?.split(' ', limit = 2) ?: return
            if (parts.size != 2 || parts[0] != endpoint.token) return
            val command = runCatching { WorkerCommand.valueOf(parts[1]) }.getOrNull() ?: return
            it.getOutputStream().bufferedWriter().apply {
                append("OK\n")
                flush()
            }
            onCommand(command)
        }
    }

    override fun close() {
        server.close()
        acceptJob.cancel()
        scope.cancel()
    }
}

private fun Array<String>.argumentValue(name: String): String? =
    firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
