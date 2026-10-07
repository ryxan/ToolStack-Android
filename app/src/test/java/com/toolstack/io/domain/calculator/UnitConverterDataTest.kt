package com.toolstack.io.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Correctness tests for the conversion factor tables.
 *
 * [roundTrip] guards against structural mistakes (NaN, non-invertible lambdas);
 * the per-category spot checks compare against independently known values so a
 * wrong-but-plausible factor cannot pass.
 */
class UnitConverterDataTest {

    private fun assertConversion(
        category: String,
        from: String,
        to: String,
        input: Double,
        expected: Double,
        relTolerance: Double = 1e-6
    ) {
        val cat = UnitConverterData.categories.first { it.name == category }
        val fromU = cat.units.first { it.label == from }
        val toU = cat.units.first { it.label == to }
        val actual = toU.fromBase(fromU.toBase(input))
        assertEquals(
            "$category: $input $from -> $to",
            expected,
            actual,
            abs(expected) * relTolerance + 1e-12
        )
    }

    private fun unit(category: String, label: String) =
        UnitConverterData.categories
            .first { it.name == category }
            .units.first { it.label == label }

    @Test
    fun `every unit round-trips through the base unit`() {
        val values = listOf(0.001, 1.0, 37.0, 12345.678)
        UnitConverterData.categories.forEach { category ->
            category.units.forEach { unit ->
                values.forEach { v ->
                    val roundTripped = unit.fromBase(unit.toBase(v))
                    assertTrue(
                        "${category.name}/${unit.label} produced non-finite result for $v",
                        roundTripped.isFinite()
                    )
                    assertEquals(
                        "${category.name}/${unit.label} round-trip of $v",
                        v, roundTripped, abs(v) * 1e-9 + 1e-12
                    )
                }
            }
        }
    }

    @Test
    fun `unit labels are unique within each category`() {
        UnitConverterData.categories.forEach { category ->
            val labels = category.units.map { it.label }
            assertEquals(
                "duplicate labels in ${category.name}: ${labels - labels.toSet()}",
                labels.size, labels.toSet().size
            )
        }
    }

    @Test
    fun `category ids are unique and stable`() {
        val ids = UnitConverterData.categories.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-z][a-z0-9_]*")) })
    }

    @Test
    fun `idFor resolves ids, names and legacy names`() {
        assertEquals("mass", UnitConverterData.idFor("mass"))
        assertEquals("mass", UnitConverterData.idFor("Mass"))
        assertEquals("mass", UnitConverterData.idFor("Weight / Mass"))
        assertEquals("fuel", UnitConverterData.idFor("Fuel Economy"))
        assertEquals("data", UnitConverterData.idFor("Data Size"))
        assertEquals(null, UnitConverterData.idFor("NoSuchCategory"))
    }

    @Test
    fun `findById resolves stable ids`() {
        assertEquals("Mass", UnitConverterData.findById("mass")?.name)
        assertEquals("Fuel", UnitConverterData.findById("fuel")?.name)
        assertEquals(null, UnitConverterData.findById("nope"))
    }

    @Test
    fun `length conversions`() {
        assertConversion("Length", "Feet", "Metres", 1.0, 0.3048)
        assertConversion("Length", "Miles", "Kilometres", 1.0, 1.609344)
        assertConversion("Length", "Inches", "Millimetres", 1.0, 25.4)
        assertConversion("Length", "Yards", "Metres", 1.0, 0.9144)
        assertConversion("Length", "Nautical miles", "Kilometres", 1.0, 1.852)
        assertConversion("Length", "Furlongs", "Metres", 1.0, 201.168)
    }

    @Test
    fun `mass conversions`() {
        assertConversion("Mass", "Pounds", "Kilograms", 1.0, 0.45359237)
        assertConversion("Mass", "Ounces", "Grams", 1.0, 28.349523125, 1e-5)
        assertConversion("Mass", "Stone", "Pounds", 1.0, 14.0)
        assertConversion("Mass", "Short tons (US)", "Pounds", 1.0, 2000.0)
        assertConversion("Mass", "Long tons (UK)", "Pounds", 1.0, 2240.0)
        assertConversion("Mass", "Tonnes (metric)", "Kilograms", 1.0, 1000.0)
        assertConversion("Mass", "Grains", "Milligrams", 1.0, 64.79891)
    }

    @Test
    fun `area conversions`() {
        assertConversion("Area", "Acres", "Square metres", 1.0, 4046.8564224)
        assertConversion("Area", "Acres", "Hectares", 1.0, 0.40468564224)
        assertConversion("Area", "Square feet", "Square inches", 1.0, 144.0)
        assertConversion("Area", "Hectares", "Square metres", 1.0, 10_000.0)
        assertConversion("Area", "Square miles", "Acres", 1.0, 640.0)
    }

    @Test
    fun `volume conversions`() {
        assertConversion("Volume", "US gallons", "Litres", 1.0, 3.785411784)
        assertConversion("Volume", "Imperial gallons", "Litres", 1.0, 4.54609)
        assertConversion("Volume", "US fluid ounces", "Millilitres", 1.0, 29.5735295625, 1e-6)
        assertConversion("Volume", "US cups", "Millilitres", 1.0, 236.5882365)
        assertConversion("Volume", "Tablespoons", "Teaspoons", 1.0, 3.0, 1e-5)
        assertConversion("Volume", "Cubic feet", "Litres", 1.0, 28.316846592)
        assertConversion("Volume", "US gallons", "US quarts", 1.0, 4.0)
        assertConversion("Volume", "Imperial gallons", "Imperial pints", 1.0, 8.0)
    }

    @Test
    fun `temperature conversions`() {
        assertConversion("Temperature", "Fahrenheit", "Celsius", 212.0, 100.0)
        assertConversion("Temperature", "Fahrenheit", "Celsius", 32.0, 0.0)
        assertConversion("Temperature", "Fahrenheit", "Celsius", -40.0, -40.0)
        assertConversion("Temperature", "Celsius", "Fahrenheit", 100.0, 212.0)
        assertConversion("Temperature", "Kelvin", "Celsius", 0.0, -273.15)
        assertConversion("Temperature", "Celsius", "Kelvin", 0.0, 273.15)
        assertConversion("Temperature", "Rankine", "Celsius", 0.0, -273.15)
        assertConversion("Temperature", "Rankine", "Fahrenheit", 491.67, 32.0)
        assertConversion("Temperature", "Fahrenheit", "Kelvin", -459.67, 0.0, 1e-4)
    }

    @Test
    fun `speed conversions`() {
        assertConversion("Speed", "Metres per second", "Kilometres per hour", 1.0, 3.6, 1e-5)
        assertConversion("Speed", "Knots", "Kilometres per hour", 1.0, 1.852, 1e-4)
        assertConversion("Speed", "Miles per hour", "Feet per second", 60.0, 88.0, 1e-6)
        assertConversion("Speed", "Kilometres per hour", "Miles per hour", 100.0, 62.1371, 1e-4)
        assertConversion("Speed", "Feet per minute", "Feet per second", 60.0, 1.0)
    }

    @Test
    fun `pressure conversions`() {
        assertConversion("Pressure", "Bars", "Kilopascals", 1.0, 100.0)
        assertConversion("Pressure", "Atmospheres", "Pascals", 1.0, 101_325.0)
        assertConversion("Pressure", "Atmospheres", "mmHg (torr)", 1.0, 760.0, 1e-4)
        assertConversion("Pressure", "Pounds per square inch", "Kilopascals", 1.0, 6.894757)
        assertConversion("Pressure", "inHg", "mmHg (torr)", 1.0, 25.4, 1e-4)
        assertConversion("Pressure", "Bars", "Pounds per square inch", 1.0, 14.5038, 1e-4)
        assertConversion("Pressure", "Millibars", "Bars", 1.0, 0.001)
    }

    @Test
    fun `flow conversions`() {
        assertConversion("Flow Rate", "US gallons per minute", "Litres per minute", 1.0, 3.785411784)
        assertConversion("Flow Rate", "Cubic feet per minute", "Litres per minute", 1.0, 28.316846592)
        assertConversion("Flow Rate", "Cubic metres per hour", "Litres per minute", 1.0, 1000.0 / 60.0, 1e-6)
        assertConversion("Flow Rate", "Cubic metres per second", "Cubic feet per second", 1.0, 35.3147, 1e-4)
        assertConversion("Flow Rate", "Litres per hour", "Litres per minute", 60.0, 1.0, 1e-5)
        assertConversion("Flow Rate", "Imperial gallons per minute", "Litres per minute", 1.0, 4.54609)
    }

    @Test
    fun `power conversions`() {
        assertConversion("Power", "Horsepower", "Watts", 1.0, 745.69987)
        assertConversion("Power", "Metric horsepower", "Horsepower", 1.0, 0.98632, 1e-4)
        assertConversion("Power", "Kilowatts", "BTU per hour", 1.0, 3412.14, 1e-4)
        assertConversion("Power", "ft·lbf/min", "Watts", 1.0, 0.02259697)
    }

    @Test
    fun `energy conversions`() {
        assertConversion("Energy", "Kilowatt-hours", "Joules", 1.0, 3_600_000.0)
        assertConversion("Energy", "Watt-hours", "Joules", 1.0, 3600.0)
        assertConversion("Energy", "Calories", "Joules", 1.0, 4.184)
        assertConversion("Energy", "Kilocalories", "Calories", 1.0, 1000.0)
        assertConversion("Energy", "BTU", "ft·lbf", 1.0, 778.169, 1e-4)
        assertConversion("Energy", "eV", "Joules", 1.0, 1.602176634e-19, 1e-6)
    }

    @Test
    fun `torque conversions`() {
        assertConversion("Torque", "ft·lbf", "Newton-metres", 1.0, 1.35581795)
        assertConversion("Torque", "in·lbf", "ft·lbf", 12.0, 1.0, 1e-6)
        assertConversion("Torque", "kgf·m", "Newton-metres", 1.0, 9.80665)
        assertConversion("Torque", "kgf·cm", "kgf·m", 100.0, 1.0)
    }

    @Test
    fun `agriculture mass rate conversions`() {
        assertConversion("Agriculture — Mass Rate", "Pounds per acre", "Kilograms per hectare", 1.0, 1.12085116)
        assertConversion("Agriculture — Mass Rate", "Kilograms per hectare", "Pounds per acre", 1.0, 0.8921787, 1e-5)
        assertConversion("Agriculture — Mass Rate", "Tonnes per hectare", "Pounds per acre", 1.0, 892.1787, 1e-5)
        assertConversion("Agriculture — Mass Rate", "Short tons per acre (US)", "Pounds per acre", 1.0, 2000.0, 1e-5)
        assertConversion("Agriculture — Mass Rate", "Ounces per acre", "Pounds per acre", 16.0, 1.0, 1e-4)
    }

    @Test
    fun `agriculture liquid rate conversions`() {
        assertConversion("Agriculture — Liquid Rate", "US gallons per acre", "Litres per hectare", 1.0, 9.35396)
        assertConversion("Agriculture — Liquid Rate", "US fluid ounces per acre", "Litres per hectare", 1.0, 0.0730778, 1e-4)
        assertConversion("Agriculture — Liquid Rate", "Imperial gallons per acre", "Litres per hectare", 1.0, 11.2336, 1e-4)
        assertConversion("Agriculture — Liquid Rate", "Cubic metres per hectare", "Litres per hectare", 1.0, 1000.0)
    }

    @Test
    fun `fuel economy conversions`() {
        assertConversion("Fuel", "Miles per gallon (US)", "Litres per 100 km", 10.0, 23.5214583)
        assertConversion("Fuel", "Litres per 100 km", "Miles per gallon (US)", 8.0, 29.40182, 1e-5)
        assertConversion("Fuel", "Miles per gallon (Imperial)", "Litres per 100 km", 10.0, 28.2480936)
        assertConversion("Fuel", "Kilometres per litre", "Litres per 100 km", 10.0, 10.0)
        assertConversion("Fuel", "Litres per 100 km", "Kilometres per litre", 8.0, 12.5)
    }

    @Test
    fun `fuel economy reciprocal at zero returns NaN`() {
        val mpg = unit("Fuel", "Miles per gallon (US)")
        assertTrue(mpg.toBase(0.0).isNaN())
        assertTrue(mpg.fromBase(0.0).isNaN())
    }

    @Test
    fun `concentration conversions`() {
        assertConversion("Concentration", "% w/v", "g/L", 1.0, 10.0)
        assertConversion("Concentration", "g/L", "mg/L (ppm)", 1.0, 1000.0)
        assertConversion("Concentration", "ppm", "µg/L (ppb)", 1.0, 1000.0)
        assertConversion("Concentration", "kg/m³", "g/L", 1.0, 1.0)
        assertConversion("Concentration", "ppt", "ppb", 1.0, 0.001)
    }

    @Test
    fun `time conversions`() {
        assertConversion("Time", "Hours", "Minutes", 1.0, 60.0)
        assertConversion("Time", "Days", "Hours", 1.0, 24.0)
        assertConversion("Time", "Weeks", "Days", 1.0, 7.0)
        assertConversion("Time", "Years (avg)", "Days", 1.0, 365.2425)
        // Gregorian mean: exactly 12 mean months per year
        assertConversion("Time", "Years (avg)", "Months (avg)", 1.0, 12.0)
        assertConversion("Time", "Minutes", "Milliseconds", 1.0, 60_000.0)
    }

    @Test
    fun `data size conversions`() {
        assertConversion("Data", "Bytes", "Bits", 1.0, 8.0)
        assertConversion("Data", "Kibibytes", "Bytes", 1.0, 1024.0)
        assertConversion("Data", "Kilobytes", "Bytes", 1.0, 1000.0)
        assertConversion("Data", "Mebibytes", "Kibibytes", 1.0, 1024.0)
        assertConversion("Data", "Gibibytes", "Megabytes", 1.0, 1073.741824)
        assertConversion("Data", "Terabytes", "Gigabytes", 1.0, 1000.0)
    }
}
