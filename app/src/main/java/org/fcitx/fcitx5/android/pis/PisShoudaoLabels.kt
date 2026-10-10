/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

/** Visible phonetic hints from the user's 首道 reference. Normal letter initials are implicit. */
object PisShoudaoLabels {
    data class Label(val initial:String="",val finals:String)
    val keys=mapOf(
        "Q" to Label(finals="iu"), "W" to Label(finals="ua"), "E" to Label("sh","e"),
        "R" to Label(finals="ie"), "T" to Label(finals="uan"), "Y" to Label(finals="ang"),
        "U" to Label(finals="u"), "I" to Label("ch","i"), "O" to Label(finals="o\nuo"), "P" to Label(finals="iao"),
        "A" to Label(finals="a"), "S" to Label(finals="ou"), "D" to Label(finals="ao"), "F" to Label(finals="eng"),
        "G" to Label(finals="uai\ning"), "H" to Label(finals="ong\niong"), "J" to Label(finals="an"),
        "K" to Label(finals="en\nia"), "L" to Label(finals="ai\nue"), "Z" to Label(finals="un"),
        "X" to Label(finals="iang\nuang"), "C" to Label(finals="in"), "V" to Label("zh","ü\nui"),
        "B" to Label(finals="üe"), "N" to Label(finals="ian"), "M" to Label(finals="ei")
    )
}
