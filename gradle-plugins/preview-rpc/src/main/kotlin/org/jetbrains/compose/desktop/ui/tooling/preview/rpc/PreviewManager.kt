/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PreviewManager {
    private val log = StdoutLogger("SERVER")
    private val serverSocket = ServerSocket(0, 0, localhost).apply { reuseAddress = true }
    private val port: Int
        get() = serverSocket.localPort
    private var connection: RemoteConnection? = null
    private var receiverThread: Thread? = null
    private var senderThread: Thread? = null
    private var previewProcess: Process? = null
    private var connectionNumber = 0
    private var isAlive = AtomicBoolean(true)

    init {
        thread {
            while (isAlive.get()) {
                try {
                    val socket = serverSocket.accept()
                    acceptConnection(socket)
                } catch (e: IOException) {
                    log.error { e.stackTraceToString() }
                }
            }
        }
    }

    private var request: FrameRequest? = null
    private var inProgressRequest: FrameRequest? = null
    private class FrameRequest(
        val classpath: String,
        val previewFqName: String,
        val width: Int,
        val height: Int,
        val onResult: (ByteArray) -> Unit
    )

    @Synchronized
    private fun acceptConnection(socket: Socket) {
        connection?.close()
        senderThread?.interrupt()
        val newConnection = RemoteConnectionImpl(
            socket = socket,
            log = StdoutLogger("SERVER CONNECTION #${++connectionNumber}"),
            onClose = {
                connection = null
            }
        )
        newConnection.receiveCommand { cmd ->
            if (cmd.type == CmdType.ATTACH) {
                connection = newConnection
                senderThread = thread {
                    while (isAlive.get()) {
                        sendRequest(newConnection)
                    }
                }
                receiverThread = thread {
                    while (isAlive.get()) {
                        receiveResponse(newConnection)
                    }
                }
            } else {
                newConnection.close()
            }
        }
    }

    @Synchronized
    fun close() {
        isAlive.set(false)
        serverSocket.close()
        connection?.close()
        previewProcess?.destroyForcibly()
    }

    @Synchronized
    fun requestPreview(
        java: String,
        serverClasspath: String,
        classpath: String,
        previewFqName: String,
        width: Int,
        height: Int,
        onResult: (ByteArray) -> Unit
    ) {
        if (connection == null && (previewProcess == null || previewProcess?.isAlive == false)) {
            previewProcess = ProcessBuilder(
                java,
                "-cp", serverClasspath,
                //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5009",
                "org.jetbrains.compose.desktop.ui.tooling.preview.rpc.PreviewHostKt",
                port.toString()
            ).apply {
                redirectOutput(ProcessBuilder.Redirect.INHERIT)
                redirectError(ProcessBuilder.Redirect.INHERIT)
            }.start()
        }
        request = FrameRequest(classpath, previewFqName, width, height, onResult)
    }

    @Synchronized
    private fun sendRequest(remote: RemoteConnection) {
        val request = request
        if (request != null && inProgressRequest == null) {
            this.request = null
            inProgressRequest = request

            remote.sendCommand(Cmd(CmdType.PREVIEW_CLASSPATH))
            remote.sendData(request.classpath.toByteArray(Charsets.UTF_8))
            remote.sendCommand(
                Cmd(CmdType.FRAME_REQUEST, request.previewFqName, request.width.toString(), request.height.toString())
            )
        }
    }

    @Synchronized
    private fun receiveResponse(remote: RemoteConnection) {
        remote.receiveCommand { cmd ->
            if (cmd.type == CmdType.FRAME) {
                val width = cmd.args[0].toInt()
                val height = cmd.args[1].toInt()
                remote.receiveData { bytes ->
                    val requested = inProgressRequest
                    if (requested != null) {
                        inProgressRequest = null

                        if (requested.width == width && requested.height == height) {
                            requested.onResult(bytes)
                        } else {
                            error("Unexpected frame")
                        }
                    }
                }
            }
        }
    }
}