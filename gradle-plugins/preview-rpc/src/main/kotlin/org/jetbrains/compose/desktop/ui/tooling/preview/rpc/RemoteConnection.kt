/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.io.*
import java.net.Socket

interface RemoteConnection {
    fun receiveCommand(onResult: (Cmd) -> Unit)
    fun receiveData(onResult: (ByteArray) -> Unit)
    fun sendCommand(command: Cmd)
    fun sendData(data: ByteArray)
    fun close()
}

internal class RemoteConnectionImpl(
    private val socket: Socket,
    private val log: Logger,
    private val onClose: () -> Unit
): RemoteConnection {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())
    private var isClosing = false

    @Synchronized
    override fun close() {
        if (!socket.isClosed && !isClosing) {
            isClosing = true
            socket.close()
            log { "CLOSED" }
            onClose()
            isClosing = false
        }
    }

    @Synchronized
    override fun sendCommand(command: Cmd) {
        val commandStr = command.asString()
        val data = commandStr.toByteArray()
        writeData(output, data, maxDataSize = MAX_CMD_SIZE)
        log { "SENT COMMAND '$commandStr'" }
    }

    @Synchronized
    override fun sendData(data: ByteArray) {
        writeData(output, data, maxDataSize = MAX_BINARY_SIZE)
        log { "SENT DATA [${data.size}]" }
    }

    @Synchronized
    override fun receiveCommand(onResult: (Cmd) -> Unit) {
        val line = readData(input, MAX_CMD_SIZE)?.toString(Charsets.UTF_8)
        if (line != null) {
            val cmd = Cmd.fromString(line)
            if (cmd == null) {
                log { "GOT UNKNOWN COMMAND '$line'" }
            } else {
                log { "GOT COMMAND '$line'" }
                onResult(cmd)
            }
        } else {
            close()
        }
    }

    @Synchronized
    override fun receiveData(onResult: (ByteArray) -> Unit) {
        val data = readData(input, MAX_BINARY_SIZE)
        if (data != null) {
            log { "GOT [${data.size}]" }
            onResult(data)
        } else {
            close()
        }
    }

    private fun writeData(output: DataOutputStream, data: ByteArray, maxDataSize: Int): Boolean =
        try {
            val size = data.size
            assert(size < maxDataSize) { "Data is too big: $size >= $maxDataSize" }
            output.writeInt(size)
            var index = 0
            val bufSize = minOf(MAX_BUF_SIZE, size)
            while (index < size) {
                val len = minOf(bufSize, size - index)
                output.write(data, index, len)
                index += len
            }
            output.flush()
            true
        } catch (e: IOException) {
            false
        }

    private fun readData(input: DataInputStream, maxDataSize: Int): ByteArray? =
        try {
            val size = input.readInt()
            if (size == -1) {
                null
            } else {
                assert(size < maxDataSize) { "Data is too big: $size >= $maxDataSize" }
                log { "Receiving data [$size]" }
                val bytes = ByteArray(size)
                val bufSize = minOf(size, MAX_BUF_SIZE)
                var index = 0
                while (index < size) {
                    val len = minOf(bufSize, size - index)
                    val bytesRead = input.read(bytes, index, len)
                    index += bytesRead
                }
                bytes
            }
        } catch (e: IOException) {
            null
        }

    companion object {
        private const val MAX_CMD_SIZE = 1024
        // 100 Mb should be enough even for 8K screenshots
        private const val MAX_BINARY_SIZE = 100 * 1024 * 1024
        private const val MAX_BUF_SIZE = 8192
    }
}


