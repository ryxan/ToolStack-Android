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
 * Factor sources: NIST SP 811, USDA, and standard engineering references.
 */
object UnitConverterData {

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
        name        = "Length",
        icon        = Icons.Filled.Straighten,
        description = "ft · m · km · mi · in · cm",
        units = listOf(
            // Common default: feet → metres
            linear("Foot",        "ft",  0.3048),
            linear("Metre",       "m",   1.0),
            linear("Kilometre",   "km",  1000.0),
            linear("Mile",        "mi",  1609.344),
            linear("Inch",        "in",  0.0254),
            linear("Yard",        "yd",  0.9144),
            linear("Centimetre",  "cm",  0.01),
            linear("Millimetre",  "mm",  0.001),
            linear("Nautical mi", "nmi", 1852.0),
            linear("Furlong",     "fur", 201.168)
        )
    )

    /** Base unit: kilogram */
    private val weight = UnitCategory(
        name        = "Mass",
        icon        = Icons.Filled.Scale,
        description = "lb · kg · oz · g · t · st",
        units = listOf(
            // Common default: pounds → kilograms
            linear("Pound",           "lb",  0.45359237),
            linear("Kilogram",        "kg",  1.0),
            linear("Ounce",           "oz",  0.02834952),
            linear("Gram",            "g",   0.001),
            linear("Tonne (metric)",  "t",   1000.0),
            linear("Stone",           "st",  6.35029318),
            linear("Short ton (US)",  "tn",  907.18474),
            linear("Long ton (UK)",   "LT",  1016.0469088),
            linear("Milligram",       "mg",  0.000001),
            linear("Grain",           "gr",  0.00006479891)
        )
    )

    /** Base unit: square metre */
    private val area = UnitCategory(
        name        = "Area",
        icon        = Icons.Filled.AreaChart,
        description = "m² · ha · ac · ft² · mi²",
        units = listOf(
            linear("Square mm",       "mm²",  0.000001),
            linear("Square cm",       "cm²",  0.0001),
            linear("Square metre",    "m²",   1.0),
            linear("Square km",       "km²",  1_000_000.0),
            linear("Hectare",         "ha",   10_000.0),
            linear("Square inch",     "in²",  0.00064516),
            linear("Square foot",     "ft²",  0.09290304),
            linear("Square yard",     "yd²",  0.83612736),
            linear("Acre",            "ac",   4046.8564224),
            linear("Square mile",     "mi²",  2_589_988.110336)
        )
    )

    /** Base unit: litre */
    private val volume = UnitCategory(
        name        = "Volume",
        icon        = Icons.Filled.WaterDrop,
        description = "gal · L · mL · m³ · qt · fl oz",
        units = listOf(
            // Common default: US gallons → litres
            linear("US gallon",        "gal",   3.785411784),
            linear("Litre",            "L",     1.0),
            linear("Millilitre",       "mL",    0.001),
            linear("Cubic metre",      "m³",    1000.0),
            linear("US quart",         "qt",    0.946352946),
            linear("US pint",          "pt",    0.473176473),
            linear("US fluid oz",      "fl oz", 0.02957352956),
            linear("US cup",           "cup",   0.2365882365),
            linear("Imperial gallon",  "Igal",  4.54609),
            linear("Imperial pint",    "Ipt",   0.56826125),
            linear("Cubic inch",       "in³",   0.016387064),
            linear("Cubic foot",       "ft³",   28.316846592),
            linear("Bushel (US)",      "bu",    35.23907016688),
            linear("Tablespoon",       "tbsp",  0.01478676478),
            linear("Teaspoon",         "tsp",   0.00492892159)
        )
    )

    /** Base unit: Celsius (offset conversions) */
    private val temperature = UnitCategory(
        name        = "Temperature",
        icon        = Icons.Filled.Thermostat,
        description = "°F · °C · K · °R",
        units = listOf(
            // Common default: Fahrenheit → Celsius
            UnitEntry(
                label    = "Fahrenheit",
                symbol   = "°F",
                toBase   = { v -> (v - 32.0) * 5.0 / 9.0 },
                fromBase = { v -> v * 9.0 / 5.0 + 32.0 }
            ),
            UnitEntry(
                label    = "Celsius",
                symbol   = "°C",
                toBase   = { v -> v },
                fromBase = { v -> v }
            ),
            UnitEntry(
                label    = "Kelvin",
                symbol   = "K",
                toBase   = { v -> v - 273.15 },
                fromBase = { v -> v + 273.15 }
            ),
            UnitEntry(
                label    = "Rankine",
                symbol   = "°R",
                toBase   = { v -> (v - 491.67) * 5.0 / 9.0 },
                fromBase = { v -> (v + 273.15) * 9.0 / 5.0 }
            )
        )
    )

    /** Base unit: metre per second */
    private val speed = UnitCategory(
        name        = "Speed",
        icon        = Icons.Filled.Speed,
        description = "km/h · mph · m/s · knots · ft/s",
        units = listOf(
            // Common default: km/h → mph
            linear("km/h",        "km/h",   0.27777778),
            linear("mph",         "mph",    0.44704),
            linear("m/s",         "m/s",    1.0),
            linear("Knot",        "kn",     0.51444444),
            linear("ft/s",        "ft/s",   0.3048),
            linear("ft/min",      "ft/min", 0.00508)
        )
    )

    /** Base unit: Pascal */
    private val pressure = UnitCategory(
        name        = "Pressure",
        icon        = Icons.Filled.FilterAlt,
        description = "PSI · kPa · bar · Pa · atm · mmHg",
        units = listOf(
            // Common default: PSI → kPa
            linear("PSI",           "psi",   6894.757),
            linear("Kilopascal",    "kPa",   1000.0),
            linear("Bar",           "bar",   100_000.0),
            linear("Pascal",        "Pa",    1.0),
            linear("Megapascal",    "MPa",   1_000_000.0),
            linear("Millibar",      "mbar",  100.0),
            linear("Atmosphere",    "atm",   101_325.0),
            linear("mmHg (torr)",   "mmHg",  133.3224),
            linear("inHg",          "inHg",  3386.389),
            linear("inH₂O",         "inH₂O", 249.0889)
        )
    )

    /** Base unit: litre per minute */
    private val flow = UnitCategory(
        name        = "Flow Rate",
        icon        = Icons.Filled.Water,
        description = "L/min · GPM · m³/hr · CFM · CFS",
        units = listOf(
            linear("mL/min",        "mL/min",  0.001),
            linear("L/min",         "L/min",   1.0),
            linear("L/hour",        "L/hr",    0.01666667),
            linear("m³/hour",       "m³/hr",   1000.0 / 60.0),
            linear("m³/s",          "m³/s",    60_000.0),
            linear("US gal/min",    "GPM",     3.785411784),
            linear("US gal/hour",   "GPH",     0.06309020),
            linear("Imp gal/min",   "IGPM",    4.54609),
            linear("ft³/min",       "CFM",     28.316846592),
            linear("ft³/s",         "CFS",     1699.0107955)
        )
    )

    /** Base unit: watt */
    private val power = UnitCategory(
        name        = "Power",
        icon        = Icons.Filled.Bolt,
        description = "W · kW · hp · BTU/h · ft·lb/min",
        units = listOf(
            linear("Watt",          "W",         1.0),
            linear("Kilowatt",      "kW",        1000.0),
            linear("Megawatt",      "MW",        1_000_000.0),
            linear("Horsepower",    "hp",        745.69987),
            linear("Metric hp",     "PS",        735.49875),
            linear("BTU/hour",      "BTU/h",     0.29307107),
            linear("ft·lbf/min",    "ft·lb/min", 0.02259697)
        )
    )

    /** Base unit: joule */
    private val energy = UnitCategory(
        name        = "Energy",
        icon        = Icons.Filled.LocalFireDepartment,
        description = "J · kJ · kWh · cal · BTU · ft·lbf",
        units = listOf(
            linear("Joule",         "J",      1.0),
            linear("Kilojoule",     "kJ",     1000.0),
            linear("Megajoule",     "MJ",     1_000_000.0),
            linear("Watt-hour",     "Wh",     3600.0),
            linear("Kilowatt-hour", "kWh",    3_600_000.0),
            linear("Calorie",       "cal",    4.184),
            linear("Kilocalorie",   "kcal",   4184.0),
            linear("BTU",           "BTU",    1055.05585),
            linear("ft·lbf",        "ft·lbf", 1.35581795),
            linear("eV",            "eV",     1.602176634e-19)
        )
    )

    /** Base unit: newton·metre */
    private val torque = UnitCategory(
        name        = "Torque",
        icon        = Icons.Filled.NorthEast,
        description = "N·m · ft·lb · in·lb · kgf·m",
        units = listOf(
            linear("Newton·metre",  "N·m",    1.0),
            linear("Kilonewton·m",  "kN·m",   1000.0),
            linear("ft·lbf",        "ft·lb",  1.35581795),
            linear("in·lbf",        "in·lb",  0.11298483),
            linear("kgf·m",         "kgf·m",  9.80665),
            linear("kgf·cm",        "kgf·cm", 0.0980665)
        )
    )

    /** Base unit: kg/ha */
    private val agrRate = UnitCategory(
        name        = "Agriculture — Mass Rate",
        icon        = Icons.Filled.Agriculture,
        description = "kg/ha · lb/ac · t/ha · bu/ac · oz/ac",
        units = listOf(
            linear("kg/ha",                    "kg/ha",  1.0),
            linear("g/ha",                     "g/ha",   0.001),
            linear("t/ha",                     "t/ha",   1000.0),
            linear("lb/acre",                  "lb/ac",  1.12085116),
            linear("oz/acre",                  "oz/ac",  0.07005320),
            linear("ton/acre (US)",             "tn/ac",  2241.70231),
            linear("bushel/acre (wheat 60 lb)", "bu/ac",  67.251)
        )
    )

    /** Base unit: L/ha — volume-per-area application rates (incompatible dimension with kg/ha). */
    private val agrLiquidRate = UnitCategory(
        name        = "Agriculture — Liquid Rate",
        icon        = Icons.Filled.Agriculture,
        description = "L/ha · mL/ha · gal/ac · fl oz/ac",
        units = listOf(
            linear("L/ha",           "L/ha",      1.0),
            linear("mL/ha",          "mL/ha",     0.001),
            linear("m³/ha",          "m³/ha",     1000.0),
            linear("gal/acre (US)",  "gal/ac",    9.35396),
            linear("fl oz/acre (US)", "fl oz/ac", 0.07300),
            linear("Imp gal/acre",   "Igal/ac",   11.2333)
        )
    )

    /** Base unit: L/100km (fuel economy — reciprocal conversions) */
    private val fuelEconomy = UnitCategory(
        name        = "Fuel",
        icon        = Icons.Filled.LocalGasStation,
        description = "mpg (US) · L/100km · mpg (Imp) · km/L",
        units = listOf(
            // Common default: mpg (US) → L/100km
            UnitEntry("mpg (US)",       "mpg",       toBase = { v -> if (v == 0.0) Double.NaN else 235.214583 / v }, fromBase = { v -> if (v == 0.0) Double.NaN else 235.214583 / v }),
            UnitEntry("L/100km",        "L/100km",   toBase = { v -> v },                                        fromBase = { v -> v }),
            UnitEntry("mpg (Imperial)", "mpg (Imp)", toBase = { v -> if (v == 0.0) Double.NaN else 282.480936 / v }, fromBase = { v -> if (v == 0.0) Double.NaN else 282.480936 / v }),
            UnitEntry("km/L",           "km/L",      toBase = { v -> if (v == 0.0) Double.NaN else 100.0 / v },     fromBase = { v -> if (v == 0.0) Double.NaN else 100.0 / v })
        )
    )

    /** Base unit: mg/L */
    private val concentration = UnitCategory(
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
        name        = "Time",
        icon        = Icons.Filled.Timer,
        description = "ms · s · min · hr · day · week · year",
        units = listOf(
            linear("Millisecond",   "ms",  0.001),
            linear("Second",        "s",   1.0),
            linear("Minute",        "min", 60.0),
            linear("Hour",          "hr",  3600.0),
            linear("Day",           "day", 86_400.0),
            linear("Week",          "wk",  604_800.0),
            linear("Month (avg)",   "mo",  2_629_746.0),
            linear("Year (Julian)", "yr",  31_557_600.0)
        )
    )

    /** Base unit: bit */
    private val dataSize = UnitCategory(
        name        = "Data",
        icon        = Icons.Filled.DataObject,
        description = "bit · B · KB · MB · GB · TB · KiB · GiB",
        units = listOf(
            linear("Bit",      "b",   1.0),
            linear("Byte",     "B",   8.0),
            linear("Kilobyte", "KB",  8_000.0),
            linear("Megabyte", "MB",  8_000_000.0),
            linear("Gigabyte", "GB",  8_000_000_000.0),
            linear("Terabyte", "TB",  8_000_000_000_000.0),
            linear("Kibibyte", "KiB", 8_192.0),
            linear("Mebibyte", "MiB", 8_388_608.0),
            linear("Gibibyte", "GiB", 8_589_934_592.0)
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

    /** Look up a category by name — used by the detail screen via SavedStateHandle. */
    fun findByName(name: String): UnitCategory? = categories.find { it.name == name }
}
