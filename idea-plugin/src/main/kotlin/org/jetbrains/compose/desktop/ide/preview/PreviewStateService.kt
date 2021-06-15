/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ide.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.compose.desktop.ui.tooling.preview.rpc.PreviewManager
import java.awt.Canvas
import java.awt.Color
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.net.InetAddress.getByName
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

@Service
class PreviewStateService(private val myProject: Project) : Disposable {
    private val isAlive = AtomicBoolean(true)
    private val previewManager: PreviewManager = PreviewManager()
    private var myPanel: JPanel? = null
    private var myCanvas: Canvas? = null

    // todo: handle ipv6
    private val gradleCallbackSocket =
        ServerSocket(0, 0, getByName("127.0.0.1"))
            .apply { reuseAddress = true }

    val gradleCallbackPort: Int
        get() = gradleCallbackSocket.localPort

    private val gradleCallbackThread = thread {
        while (isAlive.get()) {
            // todo: catch interrupted?
            gradleCallbackSocket.accept().getInputStream().use { ins ->
                ins.reader().buffered().use { reader ->
                    val javaHome = reader.readLine()
                    val serverCP = reader.readLine()
                    val target = reader.readLine()
                    val cp = reader.readLine()

                    synchronized(this@PreviewStateService) {
                        val panel = myPanel
                        val canvas = myCanvas
                        if (panel != null && canvas != null) {
                            val width = panel.width
                            val height = panel.height
                            previewManager.requestPreview(
                                javaHome, serverCP, cp, target, width, height
                            ) { bytes ->
                                val raster = Raster.createInterleavedRaster(
                                    DataBufferByte(bytes, bytes.size),
                                    width,
                                    height,
                                    width * 4,
                                    4,
                                    intArrayOf(2, 1, 0, 3),
                                    null
                                )
                                val colorModel = ComponentColorModel(
                                    ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                    true,
                                    false,
                                    Transparency.TRANSLUCENT,
                                    DataBuffer.TYPE_BYTE
                                )
                                val image = BufferedImage(colorModel, raster!!, false, null)
                                SwingUtilities.invokeAndWait {
                                    val density = 2 // for Retina displays, otherwise 1.
                                    val g = canvas.getGraphics()
                                    g.color = Color.WHITE
                                    g.fillRect(0, 0, width / density, height / density)
                                    g.drawImage(image, 0, 0, width / density, height / density, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispose() {
        isAlive.set(false)
        gradleCallbackThread.join(1000)
        if (gradleCallbackThread.isAlive) {
            gradleCallbackThread.interrupt()
        }
        gradleCallbackSocket.close()
        previewManager.close()
        myPanel = null
        myCanvas = null
    }

    fun registerPreviewPanel(panel: JPanel, canvas: Canvas) {
        myPanel = panel
        myCanvas = canvas
    }
}

