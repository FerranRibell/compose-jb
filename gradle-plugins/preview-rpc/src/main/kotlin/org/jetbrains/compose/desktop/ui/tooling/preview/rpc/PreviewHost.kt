/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.io.File
import java.net.Socket
import java.net.URLClassLoader
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val port = args[0].toInt()
    val connection = RemoteConnectionImpl(
        Socket(localhost, port),
        StdoutLogger("CLIENT"),
        onClose = {
            exitProcess(0)
        }
    )
    PreviewHost(connection)
}

internal class PreviewHost(private val remote: RemoteConnection) {
    private val log = StdoutLogger("PREVIEW HOST")
    private var previewClassLoader: URLClassLoader? = null

    init {
        thread {
            try {
                remote.sendCommand(Cmd(CmdType.ATTACH))
                while (true) {
                    remote.receiveCommand { request ->
                        when (request.type) {
                            CmdType.PREVIEW_CLASSPATH -> {
                                previewClassLoader?.close()

                                remote.receiveData { bytes ->
                                    val classpath = bytes.toString(Charsets.UTF_8).split(File.pathSeparator)
                                    val urls = Array(classpath.size) { File(classpath[it]).toURI().toURL() }
                                    previewClassLoader = URLClassLoader(urls)
                                }
                            }
                            CmdType.FRAME_REQUEST -> {
                                val classLoader = previewClassLoader
                                    ?: error("A frame is requested before preview classpath is configured")

                                // todo: validate args?
                                // todo: support reload
                                val fqName = request.args[0]
                                val width = request.args[1]
                                val height = request.args[2]
                                val previewFacade = classLoader.loadClass(PREVIEW_FACADE_CLASS_NAME)
                                val renderMethod =
                                    previewFacade.getMethod(
                                        "render", String::class.java, Int::class.java, Int::class.java
                                    )
                                val pixels = renderMethod.invoke(
                                    previewFacade, fqName, width.toInt(), height.toInt()
                                ) as ByteArray
                                val response = Cmd(CmdType.FRAME, width, height)
                                remote.sendCommand(response)
                                remote.sendData(pixels)
                            }
                            else -> {
                                error("Unexpected command: '${request.asString()}'")
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace(System.err)
                exitProcess(0)
            }
        }
    }

    companion object {
        private const val PREVIEW_FACADE_CLASS_NAME =
            "androidx.compose.desktop.ui.tooling.preview.runtime.NonInteractivePreviewFacade"
    }
}

