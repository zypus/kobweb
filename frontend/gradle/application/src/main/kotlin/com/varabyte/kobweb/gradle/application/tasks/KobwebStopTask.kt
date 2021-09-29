@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.application.tasks

import com.varabyte.kobweb.common.KobwebFolder
import com.varabyte.kobweb.gradle.application.extensions.KobwebConfig
import com.varabyte.kobweb.server.api.ServerRequest
import com.varabyte.kobweb.server.api.ServerRequestsFile
import com.varabyte.kobweb.server.api.ServerStateFile
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Stop a Kobweb web server started by the [KobwebStartTask].
 *
 * This task will block until it can confirm the server is no longer running.
 */
abstract class KobwebStopTask : KobwebTask("Stop a Kobweb server if one is running") {

    @TaskAction
    fun execute() {
        val kobwebFolder = KobwebFolder.inWorkingDirectory()
            ?: throw GradleException("This project is missing a Kobweb root folder")

        val stateFile = ServerStateFile(kobwebFolder)
        stateFile.content?.let { serverState ->
            if (ProcessHandle.of(serverState.pid).isPresent) {
                val requestsFile = ServerRequestsFile(kobwebFolder)
                requestsFile.enqueueRequest(ServerRequest.Stop())
                while (stateFile.content != null) {
                    Thread.sleep(300)
                }
            }
            else {
                Files.delete(stateFile.path)
            }
        }
    }
}