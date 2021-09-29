package com.varabyte.kobweb.server

import com.varabyte.kobweb.common.KobwebFolder
import com.varabyte.kobweb.common.conf.KobwebConfFile
import com.varabyte.kobweb.common.error.KobwebException
import com.varabyte.kobweb.server.io.ServerStateFile
import com.varabyte.kobweb.server.api.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.varabyte.kobweb.server.plugins.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists

private fun isPortInUse(port: Int): Boolean {
    try {
        ServerSocket(port).use {
            return false
        }
    } catch (ex: IOException) {
        return true
    }
}

fun main() = runBlocking {
    val folder = KobwebFolder.inWorkingDirectory()
        ?: throw KobwebException("Server must be started in the root of a Kobweb project")

    val conf = KobwebConfFile(folder).let { confFile ->
        KobwebConfFile(folder).content
            ?: throw KobwebException("Server cannot start without configuration: ${confFile.path} is missing")
    }

    val stateFile = ServerStateFile(folder)
    stateFile.content?.let { serverState ->
        if (ProcessHandle.of(serverState.pid).isPresent) {
            throw KobwebException("Server cannot start as one is already running at http://0.0.0.0:${serverState.port} with PID ${serverState.pid}")
        }
        stateFile.content = null
    }

    var port = conf.server.port
    while (isPortInUse(port)) {
        ++port
    }

    val globals = ServerGlobals()
    val env = ServerEnvironment.get()
    val engine = embeddedServer(Netty, port) {
        configureRouting(env, conf, globals)
        configureSerialization()
        configureHTTP()
        configureSecurity()
    }

    engine.start()

    val requestsFile = ServerRequestsFile(folder)
    requestsFile.path.deleteIfExists()

    val serverState = ServerState(
        port,
        ProcessHandle.current().pid()
    )
    stateFile.content = serverState

    var shouldStop = false
    while (!shouldStop) {
        requestsFile.removeRequests().forEach { request ->
            when (request) {
                is ServerRequest.Stop -> shouldStop = true
                is ServerRequest.IncrementVersion -> globals.version++
                is ServerRequest.SetStatus -> globals.status = request.message
                is ServerRequest.ClearStatus -> globals.status = null
            }
        }
        delay(300)
    }

    engine.stop(1, 5, TimeUnit.MILLISECONDS)
    requestsFile.path.deleteIfExists()
    stateFile.content = null
}