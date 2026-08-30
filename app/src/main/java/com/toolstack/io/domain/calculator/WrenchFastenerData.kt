package com.toolstack.io.domain.calculator

import com.toolstack.io.domain.model.WrenchFastenerEntry

/**
 * Static reference data for the Wrench/Fastener chart.
 *
 * Sources:
 *   SAE: ANSI/ASME B18.2.1 Hex/Lag/Square bolt head sizes.
 *   Metric: ANSI/ISO metric hex-bolt head/wrench sizes.
 */
object WrenchFastenerData {

    private val SAE_WRENCH_TO_FASTENER = linkedMapOf(
        "1/4\"" to "#6, #8",
        "5/16\"" to "#10, #12",
        "3/8\"" to "1/4\"",
        "7/16\"" to "1/4\"",
        "1/2\"" to "5/16\"",
        "9/16\"" to "3/8\"",
        "5/8\"" to "7/16\"",
        "3/4\"" to "1/2\"",
        "13/16\"" to "9/16\"",
        "15/16\"" to "5/8\"",
        "1-1/8\"" to "3/4\"",
        "1-5/16\"" to "7/8\"",
        "1-1/2\"" to "1\"",
        "1-11/16\"" to "1-1/8\"",
        "1-7/8\"" to "1-1/4\"",
        "2-1/16\"" to "1-3/8\"",
        "2-1/4\"" to "1-1/2\""
    )

    private val METRIC_WRENCH_TO_FASTENER = linkedMapOf(
        "7 mm" to "M4",
        "8 mm" to "M5",
        "10 mm" to "M6",
        "13 mm" to "M8",
        "16 mm" to "M10",
        "18 mm" to "M12",
        "21 mm" to "M14",
        "24 mm" to "M16",
        "30 mm" to "M20"
    )

    fun saeEntries(): List<WrenchFastenerEntry> =
        SAE_WRENCH_TO_FASTENER.map { (wrench, fastener) ->
            WrenchFastenerEntry(wrenchLabel = wrench, fastenerLabel = fastener)
        }

    fun metricEntries(): List<WrenchFastenerEntry> =
        METRIC_WRENCH_TO_FASTENER.map { (wrench, fastener) ->
            WrenchFastenerEntry(wrenchLabel = wrench, fastenerLabel = fastener)
        }
}
