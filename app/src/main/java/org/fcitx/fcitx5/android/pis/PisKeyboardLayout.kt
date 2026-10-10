/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.input.keyboard.*
import org.fcitx.fcitx5.android.input.picker.PickerWindow

/** Independently specified layout based on the selected visual reference. */
object PisKeyboardLayout {
    val text: List<List<KeyDef>> = listOf(
        "QWERTYUIOP".mapIndexed { index, key -> AlphabetKey(key.toString(), "1234567890"[index].toString()) },
        "ASDFGHJKL".mapIndexed { index, key -> AlphabetKey(key.toString(), arrayOf("~", "!", "@", "#", "%", "“", "”", "*", "?")[index]) },
        listOf(CapsKey()) + "ZXCVBNM".mapIndexed { index, key -> AlphabetKey(key.toString(), arrayOf("(", ")", "-", "_", ":", ";", "/")[index]) } + BackspaceKey(),
        listOf(
            TextPickerSwitchKey("符", PickerWindow.Key.Symbol, 0.125f, KeyDef.Appearance.Variant.Alternative),
            LayoutSwitchKey("123", NumberKeyboard.Name, 0.105f),
            KeyDef(KeyDef.Appearance.ImageText(",", 23f, src = R.drawable.pis_legacy_clipboard, cornerIcon = true, percentWidth = 0.105f),
                setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(",")), KeyDef.Behavior.LongPress(KeyAction.PisClipboardAction))),
            SpaceKey(),
            KeyDef(KeyDef.Appearance.ImageText(".", 23f, src = R.drawable.pis_legacy_translate, cornerIcon = true, percentWidth = 0.105f),
                setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(".")), KeyDef.Behavior.LongPress(KeyAction.PisTranslateAction))),
            KeyDef(
                KeyDef.Appearance.Text("中/英", textSize = 16f, percentWidth = 0.125f,
                    variant = KeyDef.Appearance.Variant.Alternative, viewId = R.id.button_lang),
                setOf(KeyDef.Behavior.Press(KeyAction.LangSwitchAction), KeyDef.Behavior.LongPress(KeyAction.ShowInputMethodPickerAction))
            ),
            ReturnKey(0.125f)
        )
    )
    private fun digit(value: Int) = NumPadKey(value.toString(), FcitxKeyMapping.FcitxKey_KP_0 + value, 28f, 0.25f)
    val number: List<List<KeyDef>> = listOf(
        listOf(digit(1), digit(2), digit(3), BackspaceKey(0.25f)),
        listOf(digit(4), digit(5), digit(6), TextPickerSwitchKey("符", PickerWindow.Key.Symbol, 0.25f)),
        listOf(digit(7), digit(8), digit(9),
            KeyDef(KeyDef.Appearance.Text("⎵", 23f, percentWidth = 0.25f, variant = KeyDef.Appearance.Variant.Alternative, viewId = R.id.button_mini_space),
                setOf(KeyDef.Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space)))))),
        listOf(LayoutSwitchKey("ABC", TextKeyboard.Name, 0.25f), digit(0), NumPadKey(".", 0xffae, 28f, 0.25f), ReturnKey(0.25f))
    )
}
