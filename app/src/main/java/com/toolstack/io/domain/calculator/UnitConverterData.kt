package com.toolstack.io.domain.calculator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import com.toolstack.io.domain.model.UnitCategory
import com.toolstack.io.domain.model.UnitEntry

/**
 * All categories and units for the Unit Converter screen.
 *
 * Conversion strategy: every category has an implicit base unit.
 * [UnitEntry.toBase] converts from that unit → base; [UnitEntry.fromBase] converts base → that unit.
 * Linear units use simple multipliers; temperature uses offset lambdas (base = Celsius).
 *
 * Factors are written as exact expressions derived from their definitions
 * (e.g. 1 knot = 1852 m / 3600 s) rather than pre-rounded decimals.
 *
 * Factor sources: NIST SP 811, USDA, and standard engineering references.
 */
object UnitConverterData {

    // ── shared exact definitions ──────────────────────────────────────────────

    private const val M_PER_FT = 0.3048
    private const val KG_PER_LB = 0.45359237
    private const val HA_PER_ACRE = 0.40468564224
    private const val L_PER_US_GAL = 3.785411784
    private const val L_PER_IMP_GAL = 4.54609
    private const val J_PER_BTU = 1055.05585262
    private const val J_PER_FT_LBF = 1.35581795

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Create a linear unit where base = SI reference unit (factor × SI = this unit). */
    private fun linear(label: String, symbol: String, toBaseFactor: Double) = UnitEntry(
        label    = label,
        symbol   = symbol,
        toBase   = { v -> v * toBaseFactor },
        fromBase = { v -> v / toBaseFactor }
    )

    // ── categories ────────────────────────────────────────────────────────────

    /** Base unit: metre */
    private val length = UnitCategory(
        id          = "length",
        name        = "Length",
        icon        = Icons.Filled.Straighten,
        description = "ft · m · km · mi · in · cm",
        units = listOf(
            // Common default: feet → metres
            linear("Feet",        "ft",  M_PER_FT),
            linear("Metres",       "m",   1.0),
            linear("Kilometres",   "km",  1000.0),
            linear("Miles",        "mi",  1609.344),
            linear("Inches",        "in",  0.0254),
            linear("Yards",        "yd",  0.9144),
            linear("Centimetres",  "cm",  0.01),
            linear("Millimetres",  "mm",  0.001),
            linear("Nautical miles", "nmi", 1852.0),
            linear("Furlongs",     "fur", 201.168)
        )
    )

    /** Base unit: kilogram */
    private val weight = UnitCategory(
        id          = "mass",
        name        = "Mass",
        icon        = Icons.Filled.Scale,
        description = "lb · kg · oz · g · t · st",
        units = listOf(
            // Common default: pounds → kilograms
            linear("Pounds",           "lb",  KG_PER_LB),
            linear("Kilograms",        "kg",  1.0),
            linear("Ounces",           "oz",  KG_PER_LB / 16.0),
            linear("Grams",            "g",   0.001),
            linear("Tonnes (metric)",  "t",   1000.0),
            linear("Stone",           "st",  KG_PER_LB * 14.0),
            linear("Short tons (US)",  "tn",  KG_PER_LB * 2000.0),
            linear("Long tons (UK)",   "LT",  KG_PER_LB * 2240.0),
            linear("Milligrams",       "mg",  0.000001),
            linear("Grains",           "gr",  0.00006479891)
        )
    )

    /** Base unit: square metre */
    private val area = UnitCategory(
        id          = "area",
        name        = "Area",
        icon        = Icons.Filled.AreaChart,
        description = "m² · ha · ac · ft² · mi²",
        units = listOf(
            linear("Square millimetres",       "mm²",  0.000001),
            linear("Square centimetres",       "cm²",  0.0001),
            linear("Square metres",    "m²",   1.0),
            linear("Square kilometres",       "km²",  1_000_000.0),
            linear("Hectares",         "ha",   10_000.0),
            linear("Square inches",     "in²",  0.00064516),
            linear("Square feet",     "ft²",  0.09290304),
            linear("Square yards",     "yd²",  0.83612736),
            linear("Acres",            "ac",   HA_PER_ACRE * 10_000.0),
            linear("Square miles",     "mi²",  2_589_988.110336)
        )
    )

    /** Base unit: litre */
    private val volume = UnitCategory(
        id          = "volume",
        name        = "Volume",
        icon        = Icons.Filled.WaterDrop,
        description = "gal · L · mL · m³ · qt · fl oz",
        units = listOf(
            // Common default: US gallons → litres
            linear("US gallons",        "gal",   L_PER_US_GAL),
            linear("Litres",            "L",     1.0),
            linear("Millilitres",       "mL",    0.001),
            linear("Cubic metres",      "m³",    1000.0),
            linear("US quarts",         "qt",    L_PER_US_GAL / 4.0),
            linear("US pints",          "pt",    L_PER_US_GAL / 8.0),
            linear("US fluid ounces",      "fl oz", L_PER_US_GAL / 128.0),
            linear("US cups",           "cup",   L_PER_US_GAL / 16.0),
            linear("Imperial gallons",  "Igal",  L_PER_IMP_GAL),
            linear("Imperial pints",    "Ipt",   L_PER_IMP_GAL / 8.0),
            linear("Cubic inches",       "in³",   0.016387064),
            linear("Cubic feet",       "ft³",   28.316846592),
            linear("Bushels (US)",      "bu",    35.23907016688),
            linear("Tablespoons",       "tbsp",  L_PER_US_GAL / 256.0),
            linear("Teaspoons",         "tsp",   L_PER_US_GAL / 768.0)
        )
    )

    /** Base unit: Celsius (offset conversions) */
    private val temperature = UnitCategory(
        id          = "temperature",
        name        = "Temperature",
        icon        = Icons.Filled.Thermostat,
        description = "°F · °C · K · °R",
        units = listOf(
            // Common default: Fahrenheit → Celsius
            UnitEntry(
                label    = "Fahrenheit",
                symbol   = "°F",
                toBase   = { v -> (v - 32.0) * 5.0 / 9.0 },
                fromBase = { v -> v * 9.0 / 5.0 + 32.0 },
                minValue = -459.67
            ),
            UnitEntry(
                label    = "Celsius",
                symbol   = "°C",
                toBase   = { v -> v },
                fromBase = { v -> v },
                minValue = -273.15
            ),
            UnitEntry(
                label    = "Kelvin",
                symbol   = "K",
                toBase   = { v -> v - 273.15 },
                fromBase = { v -> v + 273.15 },
                minValue = 0.0
            ),
            UnitEntry(
                label    = "Rankine",
                symbol   = "°R",
                toBase   = { v -> (v - 491.67) * 5.0 / 9.0 },
                fromBase = { v -> (v + 273.15) * 9.0 / 5.0 },
                minValue = 0.0
            )
        )
    )

    /** Base unit: metre per second */
    private val speed = UnitCategory(
        id          = "speed",
        name        = "Speed",
        icon        = Icons.Filled.Speed,
        description = "km/h · mph · m/s · knots · ft/s",
        units = listOf(
            // Common default: km/h → mph
            linear("Kilometres per hour",        "km/h",   1000.0 / 3600.0),
            linear("Miles per hour",         "mph",    0.44704),
            linear("Metres per second",         "m/s",    1.0),
            linear("Knots",        "kn",     1852.0 / 3600.0),
            linear("Feet per second",        "ft/s",   M_PER_FT),
            linear("Feet per minute",      "ft/min", M_PER_FT / 60.0)
        )
    )

    /** Base unit: Pascal */
    private val pressure = UnitCategory(
        id          = "pressure",
        name        = "Pressure",
        icon        = Icons.Filled.FilterAlt,
        description = "PSI · kPa · bar · Pa · atm · mmHg",
        units = listOf(
            // Common default: PSI → kPa
            linear("Pounds per square inch",           "psi",   6894.757293),
            linear("Kilopascals",    "kPa",   1000.0),
            linear("Bars",           "bar",   100_000.0),
            linear("Pascals",        "Pa",    1.0),
            linear("Megapascals",    "MPa",   1_000_000.0),
            linear("Millibars",      "mbar",  100.0),
            linear("Atmospheres",    "atm",   101_325.0),
            linear("mmHg (torr)",   "mmHg",  133.322387),
            linear("inHg",          "inHg",  3386.389),
            linear("inH₂O",         "inH₂O", 249.0889)
        )
    )

    /** Base unit: litre per minute */
    private val flow = UnitCategory(
        id          = "flow",
        name        = "Flow Rate",
        icon        = Icons.Filled.Water,
        description = "L/min · GPM · m³/hr · CFM · CFS",
        units = listOf(
            linear("Millilitres per minute",        "mL/min",  0.001),
            linear("Litres per minute",         "L/min",   1.0),
            linear("Litres per hour",        "L/hr",    1.0 / 60.0),
            linear("Cubic metres per hour",       "m³/hr",   1000.0 / 60.0),
            linear("Cubic metres per second",          "m³/s",    60_000.0),
            linear("US gallons per minute",    "GPM",     L_PER_US_GAL),
            linear("US gallons per hour",   "GPH",     L_PER_US_GAL / 60.0),
            linear("Imperial gallons per minute",   "IGPM",    L_PER_IMP_GAL),
            linear("Cubic feet per minute",       "CFM",     28.316846592),
            linear("Cubic feet per second",         "CFS",     28.316846592 * 60.0)
        )
    )

    /** Base unit: watt */
    private val power = UnitCategory(
        id          = "power",
        name        = "Power",
        icon        = Icons.Filled.Bolt,
        description = "W · kW · hp · BTU/h · ft·lb/min",
        units = listOf(
            linear("Watts",          "W",         1.0),
            linear("Kilowatts",      "kW",        1000.0),
            linear("Megawatts",      "MW",        1_000_000.0),
            linear("Horsepower",    "hp",        745.69987),
            linear("Metric horsepower",     "PS",        735.49875),
            linear("BTU per hour",      "BTU/h",     J_PER_BTU / 3600.0),
            linear("ft·lbf/min",    "ft·lb/min", J_PER_FT_LBF / 60.0)
        )
    )

    /** Base unit: joule */
    private val energy = UnitCategory(
        id          = "energy",
        name        = "Energy",
        icon        = Icons.Filled.LocalFireDepartment,
        description = "J · kJ · kWh · cal · BTU · ft·lbf",
        units = listOf(
            linear("Joules",         "J",      1.0),
            linear("Kilojoules",     "kJ",     1000.0),
            linear("Megajoules",     "MJ",     1_000_000.0),
            linear("Watt-hours",     "Wh",     3600.0),
            linear("Kilowatt-hours", "kWh",    3_600_000.0),
            linear("Calories",       "cal",    4.184),
            linear("Kilocalories",   "kcal",   4184.0),
            linear("BTU",           "BTU",    J_PER_BTU),
            linear("ft·lbf",        "ft·lbf", J_PER_FT_LBF),
            linear("eV",            "eV",     1.602176634e-19)
        )
    )

    /** Base unit: newton·metre */
    private val torque = UnitCategory(
        id          = "torque",
        name        = "Torque",
        icon        = Icons.Filled.NorthEast,
        description = "N·m · ft·lb · in·lb · kgf·m",
        units = listOf(
            linear("Newton-metres",  "N·m",    1.0),
            linear("Kilonewton-metres",  "kN·m",   1000.0),
            linear("ft·lbf",        "ft·lb",  J_PER_FT_LBF),
            linear("in·lbf",        "in·lb",  J_PER_FT_LBF / 12.0),
            linear("kgf·m",         "kgf·m",  9.80665),
            linear("kgf·cm",        "kgf·cm", 9.80665 / 100.0)
        )
    )

    /** Base unit: kg/ha */
    private val agrRate = UnitCategory(
        id          = "agr_mass_rate",
        name        = "Agriculture — Mass Rate",
        icon        = Icons.Filled.Agriculture,
        description = "kg/ha · lb/ac · t/ha · bu/ac · oz/ac",
        units = listOf(
            linear("Kilograms per hectare",                    "kg/ha",  1.0),
            linear("Grams per hectare",                     "g/ha",   0.001),
            linear("Tonnes per hectare",                     "t/ha",   1000.0),
            linear("Pounds per acre",                  "lb/ac",  KG_PER_LB / HA_PER_ACRE),
            linear("Ounces per acre",                  "oz/ac",  KG_PER_LB / 16.0 / HA_PER_ACRE),
            linear("Short tons per acre (US)",             "tn/ac",  KG_PER_LB * 2000.0 / HA_PER_ACRE),
            linear("Bushels per acre (wheat 60 lb)", "bu/ac",  KG_PER_LB * 60.0 / HA_PER_ACRE)
        )
    )

    /** Base unit: L/ha — volume-per-area application rates (incompatible dimension with kg/ha). */
    private val agrLiquidRate = UnitCategory(
        id          = "agr_liquid_rate",
        name        = "Agriculture — Liquid Rate",
        icon        = Icons.Filled.Agriculture,
        description = "L/ha · mL/ha · gal/ac · fl oz/ac",
        units = listOf(
            linear("Litres per hectare",           "L/ha",      1.0),
            linear("Millilitres per hectare",          "mL/ha",     0.001),
            linear("Cubic metres per hectare",          "m³/ha",     1000.0),
            linear("US gallons per acre",  "gal/ac",    L_PER_US_GAL / HA_PER_ACRE),
            linear("US fluid ounces per acre", "fl oz/ac", L_PER_US_GAL / 128.0 / HA_PER_ACRE),
            linear("Imperial gallons per acre",   "Igal/ac",   L_PER_IMP_GAL / HA_PER_ACRE)
        )
    )

    /** Base unit: L/100km (fuel economy — reciprocal conversions) */
    private val fuelEconomy = UnitCategory(
        id          = "fuel",
        name        = "Fuel",
        icon        = Icons.Filled.LocalGasStation,
        description = "mpg (US) · L/100km · mpg (Imp) · km/L",
        units = listOf(
            // Common default: mpg (US) → L/100km
            UnitEntry("Miles per gallon (US)",       "mpg",       toBase = { v -> if (v == 0.0) Double.NaN else 235.214583 / v }, fromBase = { v -> if (v == 0.0) Double.NaN else 235.214583 / v }, minValue = 0.0),
            UnitEntry("Litres per 100 km",        "L/100km",   toBase = { v -> v },                                        fromBase = { v -> v },                                        minValue = 0.0),
            UnitEntry("Miles per gallon (Imperial)", "mpg (Imp)", toBase = { v -> if (v == 0.0) Double.NaN else 282.480936 / v }, fromBase = { v -> if (v == 0.0) Double.NaN else 282.480936 / v }, minValue = 0.0),
            UnitEntry("Kilometres per litre",           "km/L",      toBase = { v -> if (v == 0.0) Double.NaN else 100.0 / v },     fromBase = { v -> if (v == 0.0) Double.NaN else 100.0 / v },     minValue = 0.0)
        )
    )

    /** Base unit: mg/L */
    private val concentration = UnitCategory(
        id          = "concentration",
        name        = "Concentration",
        icon        = Icons.Filled.Science,
        description = "mg/L · µg/L · g/L · ppm · ppb · % w/v",
        units = listOf(
            linear("mg/L (ppm)", "mg/L",   1.0),
            linear("µg/L (ppb)", "µg/L",   0.001),
            linear("g/L",        "g/L",    1000.0),
            linear("kg/m³",      "kg/m³",  1000.0),
            linear("% w/v",      "% w/v",  10_000.0),
            linear("ppm",        "ppm",    1.0),
            linear("ppb",        "ppb",    0.001),
            linear("ppt",        "ppt",    0.000001)
        )
    )

    /** Base unit: second */
    private val time = UnitCategory(
        id          = "time",
        name        = "Time",
        icon        = Icons.Filled.Timer,
        description = "ms · s · min · hr · day · week · year",
        units = listOf(
            linear("Milliseconds",   "ms",  0.001),
            linear("Seconds",        "s",   1.0),
            linear("Minutes",        "min", 60.0),
            linear("Hours",          "hr",  3600.0),
            linear("Days",           "day", 86_400.0),
            linear("Weeks",          "wk",  604_800.0),
            linear("Months (avg)",   "mo",  2_629_746.0),
            // Gregorian mean year (365.2425 d) = exactly 12 × mean month
            linear("Years (avg)",    "yr",  31_556_952.0)
        )
    )

    /** Base unit: bit */
    private val dataSize = UnitCategory(
        id          = "data",
        name        = "Data",
        icon        = Icons.Filled.DataObject,
        description = "bit · B · KB · MB · GB · TB · KiB · GiB",
        units = listOf(
            linear("Bits",      "b",   1.0),
            linear("Bytes",     "B",   8.0),
            linear("Kilobytes", "KB",  8_000.0),
            linear("Megabytes", "MB",  8_000_000.0),
            linear("Gigabytes", "GB",  8_000_000_000.0),
            linear("Terabytes", "TB",  8_000_000_000_000.0),
            linear("Kibibytes", "KiB", 8_192.0),
            linear("Mebibytes", "MiB", 8_388_608.0),
            linear("Gibibytes", "GiB", 8_589_934_592.0)
        )
    )

    /** All categories in display order. */
    val categories: List<UnitCategory> = listOf(
        length,
        weight,
        area,
        volume,
        temperature,
        speed,
        pressure,
        flow,
        power,
        energy,
        torque,
        agrRate,
        agrLiquidRate,
        fuelEconomy,
        concentration,
        time,
        dataSize
    )

    /**
     * Maps display names used by older app versions to the current stable id.
     * Single source of truth for preference/order migration.
     */
    private val LEGACY_NAME_TO_ID = mapOf(
        "Weight / Mass" to "mass",
        "Fuel Economy"  to "fuel",
        "Data Size"     to "data"
    )

    /**
     * Resolves any persisted key — a stable id, a current display name, or a
     * legacy display name — to the category's stable id. Returns null for
     * keys that don't correspond to any known category.
     */
    fun idFor(key: String): String? =
        categories.firstOrNull { it.id == key || it.name == key }?.id
            ?: LEGACY_NAME_TO_ID[key]

    /** Look up a category by its stable [id]. */
    fun findById(id: String): UnitCategory? = categories.find { it.id == id }
}
