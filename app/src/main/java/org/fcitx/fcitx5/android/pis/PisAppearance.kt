/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.graphics.Color
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs

/** The appearance belongs to pis; Fcitx provides key measuring, gestures and input. */
object PisAppearance {
    data class Palette(val background: String, val keys: String, val functions: String, val text: String)
    val light = Palette("#D5D8DE", "#F7F8FA", "#BFC4CD", "#1C1C1E")
    private val previousLight = Palette("#E9EAED", "#FFFFFF", "#BEC4CD", "#2D3036")
    private val previousPearl = Palette("#DEDEDD", "#F3F3F1", "#CECECB", "#171C1F")
    private val previousNeutral = Palette("#DEDEDE", "#F3F3F3", "#CECECE", "#171C1F")
    val dark = Palette("#20242B", "#363C46", "#505966", "#F5F6F8")
    val blue = Palette("#E3ECF6", "#FAFCFF", "#B6CCE7", "#27384E")

    private fun preferences(context: Context) = context.getSharedPreferences("pis_appearance", 0)
    fun palette(context: Context): Palette = preferences(context).run {
        Palette(getString("background", light.background)!!, getString("keys", light.keys)!!,
            getString("functions", light.functions)!!, getString("text", light.text)!!)
    }

    fun initialize(context: Context) {
        AppPrefs.getInstance().advanced.keyboardHeightPercentBase.setValue(
            org.fcitx.fcitx5.android.input.keyboard.KeyboardHeightPercentBase.RealSize
        )
        if (!preferences(context).getBoolean("initialized", false)) {
            val prefs = ThemeManager.prefs
            prefs.keyBorder.setValue(true)
            prefs.keyBorderStroke.setValue(false)
            prefs.keyRadius.setValue(5)
            prefs.keyHorizontalMargin.setValue(3)
            prefs.keyVerticalMargin.setValue(5)
            prefs.punctuationPosition.setValue(ThemePrefs.PunctuationPosition.TopRight)
            AppPrefs.getInstance().keyboard.keepLettersUppercase.setValue(true)
            AppPrefs.getInstance().keyboard.keyboardHeightPercent.setValue(34)
            apply(context, light)
            preferences(context).edit().putBoolean("initialized", true).apply()
        } else if (preferences(context).getInt("palette_revision", 0) < 4 && palette(context) in listOf(previousLight, previousPearl, previousNeutral)) {
            if (ThemeManager.prefs.keyRadius.getValue() == 4) ThemeManager.prefs.keyRadius.setValue(5)
            apply(context, light)
        }
        preferences(context).edit().putInt("palette_revision", 4).apply()
    }

    fun apply(context: Context, palette: Palette) {
        val background = Color.parseColor(palette.background)
        val keys = Color.parseColor(palette.keys)
        val functions = Color.parseColor(palette.functions)
        val ink = Color.parseColor(palette.text)
        val darkTheme = androidx.core.graphics.ColorUtils.calculateLuminance(background) < 0.35
        val secondary = if (darkTheme) Color.parseColor("#ABB3C0") else if (palette == light) Color.parseColor("#727780") else Color.parseColor("#A6ABB3")
        val theme = Theme.Custom(
            name = "Pis", isDark = darkTheme, backgroundImage = null,
            backgroundColor = background,
            barColor = if (palette == light) Color.parseColor("#E9EBEF") else background,
            keyboardColor = background,
            keyBackgroundColor = keys, keyTextColor = ink, candidateTextColor = ink,
            candidateLabelColor = secondary, candidateCommentColor = secondary,
            altKeyBackgroundColor = functions, altKeyTextColor = ink,
            accentKeyBackgroundColor = functions, accentKeyTextColor = ink,
            keyPressHighlightColor = if (darkTheme) 0x33ffffff else 0x1f000000,
            keyShadowColor = if (darkTheme) Color.parseColor("#12151A") else if (palette == light) Color.parseColor("#B1B6C0") else Color.parseColor("#B5B5B5"),
            popupBackgroundColor = keys, popupTextColor = ink, spaceBarColor = ink,
            dividerColor = secondary, clipboardEntryColor = keys,
            genericActiveBackgroundColor = functions, genericActiveForegroundColor = ink
        )
        ThemeManager.saveTheme(theme)
        ThemeManager.prefs.followSystemDayNightTheme.setValue(false)
        ThemeManager.setNormalModeTheme(theme)
        preferences(context).edit().putString("background", palette.background).putString("keys", palette.keys)
            .putString("functions", palette.functions).putString("text", palette.text).apply()
    }
}
