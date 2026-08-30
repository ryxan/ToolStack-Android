package com.toolstack.io.domain.model

import kotlinx.serialization.Serializable

/**
 * A single bearing entry with its basic ISO designation and boundary dimensions.
 *
 * @param designation the ISO basic designation, e.g. "6205"
 * @param type the bearing family, e.g. "Single-row deep groove ball bearing"
 * @param boreMm bore (inner) diameter in millimetres
 * @param odMm outside diameter in millimetres
 * @param widthMm width in millimetres
 * @param series the dimension series, e.g. "6000", "6200", "6300", "6400"
 */
@Serializable
data class Bearing(
    val designation: String,
    val type: String,
    val boreMm: Double,
    val odMm: Double,
    val widthMm: Double,
    val series: String
)
