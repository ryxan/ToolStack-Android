package com.toolstack.io.domain.model

/**
 * A single row in the Wrench/Fastener reference table.
 *
 * @param wrenchLabel the wrench or head size (e.g. "1/2\"" or "13 mm")
 * @param fastenerLabel the matching fastener diameter (e.g. "3/8\"" or "M8")
 */
data class WrenchFastenerEntry(
    val wrenchLabel: String,
    val fastenerLabel: String
)
