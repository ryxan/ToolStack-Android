package com.toolstack.io.domain.model

data class TapDrillThread(
    val designation: String,
    val standard: String,
    val majorDiameter: Double,
    val pitch: Double,
    val tapDrill75: String,
    val tapDrill75Decimal: Double,
    val closestFractional: String? = null,
    val isSparkPlug: Boolean = false
)
