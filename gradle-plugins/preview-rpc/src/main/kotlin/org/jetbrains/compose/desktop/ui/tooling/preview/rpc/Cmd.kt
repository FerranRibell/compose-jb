/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

enum class CmdType {
    ATTACH,
    PREVIEW_CLASSPATH,
    FRAME_REQUEST,
    FRAME
}

class Cmd(val type: CmdType, val args: List<String>) {
    constructor(type: CmdType, vararg args: String) : this(type, args.toList())

    fun asString() =
        (sequenceOf(type.name) + args.asSequence()).joinToString(" ")

    companion object {
        private val typeByName: Map<String, CmdType> =
            CmdType.values().associateBy { it.name }

        fun fromString(s: String): Cmd? {
            val wordsIt = s.splitToSequence(" ").iterator()
            val cmdName = wordsIt.nextOrNull() ?: return null
            val type = typeByName[cmdName] ?: return null
            val args = arrayListOf<String>()
            wordsIt.forEachRemaining {
                args.add(it)
            }
            return Cmd(type, args)
        }
    }
}
