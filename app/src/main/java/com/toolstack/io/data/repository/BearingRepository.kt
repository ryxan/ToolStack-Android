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
 * Loads the bearing catalog from `assets/bearings.json` and
 * `assets/insert_bearings.json` and exposes search.
 */
@Singleton
class JsonBearingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : BearingRepository {

    private val bearings by lazy {
        loadCatalogs("bearings.json", "insert_bearings.json")
    }

    private fun loadCatalogs(vararg fileNames: String): List<Bearing> {
        return fileNames.flatMap { fileName ->
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            BearingData.fromJson(json)
        }
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
