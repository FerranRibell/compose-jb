/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

internal abstract class Logger {
    inline operator fun invoke(s: () -> String) {
        if (isEnabled) {
            log(s())
        }
    }

    inline fun error(msg: () -> String) {
        invoke { "error: ${msg()}" }
    }

    @PublishedApi
    internal abstract val isEnabled: Boolean

    @PublishedApi
    internal abstract fun log(s: String)
}

internal class StdoutLogger(private val prefix: String) : Logger() {
    override val isEnabled: Boolean = true

    override fun log(s: String)  {
        System.out.print(prefix)
        System.out.print(":")
        System.out.println(s)
    }
}