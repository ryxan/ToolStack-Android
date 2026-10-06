package com.toolstack.io.ui.theme

import androidx.compose.ui.graphics.Color

val IndustrialBlue = Color(0xFF1565C0)
val IndustrialBlueLight = Color(0xFF1976D2)
val IndustrialBlueDark = Color(0xFF0D47A1)

// Calculator theme colors — light palette
val CalcBackground = Color(0xFFD4E7F4)
val CalcDigitButton = Color(0xFF5BA3D0)
val CalcFunctionDark = Color(0xFF3D6B8A)
val CalcClearRed = Color(0xFFE76F6F)
val CalcOperatorOrange = Color(0xFFF5A962)
val CalcEqualsGreen = Color(0xFF6FCF97)
val CalcDisplayDark = Color(0xFF2C5F7F)

// Calculator theme colors — dark palette
val CalcBackgroundDark = Color(0xFF16242E)
val CalcDigitButtonDark = Color(0xFF3E7494)
val CalcFunctionButtonDark = Color(0xFF2B4A5E)
val CalcClearRedDark = Color(0xFFB85656)
val CalcOperatorOrangeDark = Color(0xFFD89450)
val CalcEqualsGreenDark = Color(0xFF5AA87B)
val CalcDisplayTextDark = Color(0xFFD8EAF4)

/** The calculator's pastel button palette, resolved per theme via [calcColors]. */
data class CalcColors(
    val background: Color,
    val digit: Color,
    val function: Color,
    val clear: Color,
    val operator: Color,
    val equals: Color,
    val display: Color
)

val LightCalcColors = CalcColors(
    background = CalcBackground,
    digit = CalcDigitButton,
    function = CalcFunctionDark,
    clear = CalcClearRed,
    operator = CalcOperatorOrange,
    equals = CalcEqualsGreen,
    display = CalcDisplayDark
)

val DarkCalcColors = CalcColors(
    background = CalcBackgroundDark,
    digit = CalcDigitButtonDark,
    function = CalcFunctionButtonDark,
    clear = CalcClearRedDark,
    operator = CalcOperatorOrangeDark,
    equals = CalcEqualsGreenDark,
    display = CalcDisplayTextDark
)
