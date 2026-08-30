package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.Bearing
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Pure functions for parsing and searching the bearing reference dataset.
 *
 * The actual data lives in `assets/bearings.json` so it can be edited, versioned,
 * and shared with other platforms without recompiling Kotlin code.
 */
object BearingData {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a JSON array of [Bearing] objects.
     */
    fun fromJson(jsonString: String): List<Bearing> {
        return json.decodeFromString(jsonString)
    }

    /**
     * Returns true when all three dimensions are finite and greater than zero.
     */
    fun areValidDimensions(boreMm: Double, odMm: Double, widthMm: Double): Boolean {
        return listOf(boreMm, odMm, widthMm).all { it > 0 && it.isFinite() }
    }

    /**
     * Finds bearings whose dimensions match the requested values within the
     * given tolerance. Results are sorted by how closely they match.
     */
    fun findByDimensions(
        bearings: List<Bearing>,
        boreMm: Double,
        odMm: Double,
        widthMm: Double,
        toleranceMm: Double = 0.0
    ): List<Bearing> {
        if (!areValidDimensions(boreMm, odMm, widthMm)) {
            return emptyList()
        }
        return bearings.filter { bearing ->
            matches(bearing, boreMm, odMm, widthMm, toleranceMm)
        }.sortedBy { bearing ->
            deviation(bearing, boreMm, odMm, widthMm)
        }
    }

    private fun matches(
        bearing: Bearing,
        boreMm: Double,
        odMm: Double,
        widthMm: Double,
        toleranceMm: Double
    ): Boolean {
        return within(bearing.boreMm, boreMm, toleranceMm) &&
            within(bearing.odMm, odMm, toleranceMm) &&
            within(bearing.widthMm, widthMm, toleranceMm)
    }

    private fun within(value: Double, target: Double, tolerance: Double): Boolean {
        return abs(value - target) <= tolerance
    }

    private fun deviation(
        bearing: Bearing,
        boreMm: Double,
        odMm: Double,
        widthMm: Double
    ): Double {
        return abs(bearing.boreMm - boreMm) +
            abs(bearing.odMm - odMm) +
            abs(bearing.widthMm - widthMm)
    }
}
