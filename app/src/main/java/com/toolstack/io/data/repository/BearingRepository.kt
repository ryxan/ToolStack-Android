package com.toolstack.io.data.repository

import android.content.Context
import com.toolstack.io.domain.calculator.BearingData
import com.toolstack.io.domain.model.Bearing
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository abstraction for bearing lookup data. The implementation can be
 * swapped between JSON, Room, or a static source without touching ViewModels.
 */
interface BearingRepository {
    fun findByDimensions(
        boreMm: Double,
        odMm: Double,
        widthMm: Double,
        toleranceMm: Double = 0.0
    ): List<Bearing>
}

/**
 * Loads the bearing catalog from `assets/bearings.json` and exposes search.
 */
@Singleton
class JsonBearingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : BearingRepository {

    private val bearings by lazy {
        val json = context.assets.open("bearings.json").bufferedReader().use { it.readText() }
        BearingData.fromJson(json)
    }

    override fun findByDimensions(
        boreMm: Double,
        odMm: Double,
        widthMm: Double,
        toleranceMm: Double
    ): List<Bearing> {
        return BearingData.findByDimensions(bearings, boreMm, odMm, widthMm, toleranceMm)
    }
}
