package com.synctosh.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerControlTest {
    @Test
    fun authenticatedLoopbackCommandIsAcknowledged() {
        val received = CountDownLatch(1)
        var command: WorkerCommand? = null
        WorkerControlServer {
            command = it
            received.countDown()
        }.use { server ->
            assertTrue(server.endpoint.send(WorkerCommand.SHOW))
            assertTrue(received.await(2, TimeUnit.SECONDS))
            assertEquals(WorkerCommand.SHOW, command)
        }
    }
}
