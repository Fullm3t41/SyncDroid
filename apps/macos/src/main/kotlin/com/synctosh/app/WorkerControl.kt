package com.synctosh.app

import com.synctosh.app.platform.MacAppPaths
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
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
            val writer = socket.getOutputStream().bufferedWriter()
            writer.append(token).append(' ').append(command.name).append('\n')
            writer.flush()
            check(socket.getInputStream().bufferedReader().readLine() == "OK")
        }
        true
    }.getOrDefault(false)

    fun save() {
        val target = MacAppPaths.workerEndpoint
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, "worker", ".endpoint.tmp")
        try {
            Files.writeString(temporary, "$port\n$token\n")
            runCatching {
                Files.setPosixFilePermissions(
                    temporary,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
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
        runCatching { if (load() == this) Files.deleteIfExists(MacAppPaths.workerEndpoint) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 1_500

        fun load(): WorkerEndpoint? = runCatching {
            val lines = Files.readAllLines(MacAppPaths.workerEndpoint)
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

internal const val WORKER_PORT_ENV = "SYNCTOSH_WORKER_PORT"
internal const val WORKER_TOKEN_ENV = "SYNCTOSH_WORKER_TOKEN"

internal class WorkerControlServer(
    private val onCommand: (WorkerCommand) -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val endpoint: WorkerEndpoint = WorkerEndpoint.create(server.localPort)
    private val acceptJob: Job = scope.launch {
        while (isActive && !server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: continue
            launch { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = 1_500
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
