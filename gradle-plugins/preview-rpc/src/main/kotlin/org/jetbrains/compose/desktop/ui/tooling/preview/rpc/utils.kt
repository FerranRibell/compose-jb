/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.net.InetAddress

internal val localhost: InetAddress
    get() = InetAddress.getByName("127.0.0.1")

internal fun <T> Iterator<T>.nextOrNull(): T? =
    if (hasNext()) next() else null