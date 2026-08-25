package com.industrialutility.domain.model

/**
 * A single row in the SAE to Metric conversion table.
 *
 * @param fractionLabel human-readable fraction (e.g. "5/16" or "1-3/8")
 * @param decimalInch decimal inch value
 * @param metricMm equivalent millimetres (1 inch = 25.4 mm exactly)
 * @param isCommon true when the size is a common shop fraction
 */
data class SaeMetricEntry(
    val fractionLabel: String,
    val decimalInch: Double,
    val metricMm: Double,
    val isCommon: Boolean,
    val decimalDisplay: String = "",
    val metricDisplay: String = ""
)
