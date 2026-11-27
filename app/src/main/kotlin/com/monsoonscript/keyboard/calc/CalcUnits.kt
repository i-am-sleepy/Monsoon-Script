package com.monsoonscript.keyboard.calc

/** The units the calculator can convert between ("5 km in miles", "98.6 f to c"). Offline, so no currencies. Pure Kotlin. */
class CalcUnit(val dim: Dim, val symbol: String, val factor: Double, val offset: Double) {
    enum class Dim { LENGTH, MASS, VOLUME, AREA, TIME, TEMPERATURE, SPEED, DATA, ANGLE, ENERGY, POWER, PRESSURE }

    /** Value in this unit to the base unit of its kind: (v + offset) * factor. */
    fun toBase(v: Double): Double = (v + offset) * factor

    fun fromBase(b: Double): Double = b / factor - offset
}

object CalcUnits {
    private val table = HashMap<String, CalcUnit>(512)

    private fun reg(dim: CalcUnit.Dim, symbol: String, factor: Double, vararg names: String, offset: Double = 0.0) {
        val u = CalcUnit(dim, symbol, factor, offset)
        for (n in names) table[n] = u
    }

    init {
        val L = CalcUnit.Dim.LENGTH
        reg(L, "m", 1.0, "m", "meter", "meters", "metre", "metres")
        reg(L, "km", 1000.0, "km", "kms", "kilometer", "kilometers", "kilometre", "kilometres")
        reg(L, "cm", 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        reg(L, "mm", 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        reg(L, "µm", 1e-6, "um", "µm", "micron", "microns", "micrometer", "micrometers")
        reg(L, "in", 0.0254, "in", "inch", "inches")
        reg(L, "ft", 0.3048, "ft", "foot", "feet")
        reg(L, "yd", 0.9144, "yd", "yds", "yard", "yards")
        reg(L, "mi", 1609.344, "mi", "mile", "miles")
        reg(L, "nmi", 1852.0, "nmi", "nauticalmile", "nauticalmiles")
        val M = CalcUnit.Dim.MASS
        reg(M, "mg", 1e-6, "mg", "milligram", "milligrams")
        reg(M, "g", 0.001, "g", "gm", "gms", "gram", "grams")
        reg(M, "kg", 1.0, "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")
        reg(M, "t", 1000.0, "t", "tonne", "tonnes")
        reg(M, "lb", 0.45359237, "lb", "lbs", "pound", "pounds")
        reg(M, "oz", 0.028349523125, "oz", "ounce", "ounces")
        reg(M, "st", 6.35029318, "st", "stone", "stones")
        val V = CalcUnit.Dim.VOLUME
        reg(V, "ml", 0.001, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        reg(V, "cl", 0.01, "cl", "centiliter", "centiliters", "centilitre", "centilitres")
        reg(V, "dl", 0.1, "dl", "deciliter", "deciliters", "decilitre", "decilitres")
        reg(V, "L", 1.0, "l", "liter", "liters", "litre", "litres")
        reg(V, "m³", 1000.0, "m3", "m³", "cubicmeter", "cubicmeters")
        reg(V, "gal", 3.785411784, "gal", "gallon", "gallons")
        reg(V, "qt", 0.946352946, "qt", "quart", "quarts")
        reg(V, "pt", 0.473176473, "pt", "pint", "pints")
        reg(V, "cup", 0.2365882365, "cup", "cups")
        reg(V, "fl oz", 0.0295735295625, "floz")
        reg(V, "tbsp", 0.01478676478125, "tbsp", "tablespoon", "tablespoons")
        reg(V, "tsp", 0.00492892159375, "tsp", "teaspoon", "teaspoons")
        val A = CalcUnit.Dim.AREA
        reg(A, "m²", 1.0, "m2", "m²", "sqm")
        reg(A, "km²", 1e6, "km2", "km²", "sqkm")
        reg(A, "cm²", 1e-4, "cm2", "cm²", "sqcm")
        reg(A, "ft²", 0.09290304, "ft2", "ft²", "sqft")
        reg(A, "in²", 0.00064516, "in2", "in²", "sqin")
        reg(A, "yd²", 0.83612736, "yd2", "yd²", "sqyd")
        reg(A, "mi²", 2589988.110336, "mi2", "mi²", "sqmi")
        reg(A, "acre", 4046.8564224, "acre", "acres")
        reg(A, "ha", 10000.0, "ha", "hectare", "hectares")
        val T = CalcUnit.Dim.TIME
        reg(T, "ms", 0.001, "ms", "millisecond", "milliseconds")
        reg(T, "s", 1.0, "s", "sec", "secs", "second", "seconds")
        reg(T, "min", 60.0, "min", "mins", "minute", "minutes")
        reg(T, "h", 3600.0, "h", "hr", "hrs", "hour", "hours")
        reg(T, "day", 86400.0, "d", "day", "days")
        reg(T, "week", 604800.0, "wk", "week", "weeks")
        reg(T, "month", 2629800.0, "month", "months") // an average month, 30.4375 days
        reg(T, "year", 31557600.0, "yr", "yrs", "year", "years") // 365.25 days
        val C = CalcUnit.Dim.TEMPERATURE
        reg(C, "°C", 1.0, "c", "°c", "celsius", "centigrade")
        reg(C, "°F", 5.0 / 9.0, "f", "°f", "fahrenheit", offset = -32.0)
        reg(C, "K", 1.0, "k", "kelvin", offset = -273.15)
        val S = CalcUnit.Dim.SPEED
        reg(S, "m/s", 1.0, "m/s", "mps")
        reg(S, "km/h", 1.0 / 3.6, "km/h", "kmh", "kph", "kmph")
        reg(S, "mph", 0.44704, "mph", "mi/h")
        reg(S, "kn", 1852.0 / 3600.0, "kn", "kt", "knot", "knots")
        reg(S, "ft/s", 0.3048, "ft/s", "fps")
        val D = CalcUnit.Dim.DATA
        reg(D, "bit", 0.125, "bit", "bits")
        reg(D, "byte", 1.0, "byte", "bytes")
        reg(D, "KB", 1e3, "kb", "kilobyte", "kilobytes")
        reg(D, "MB", 1e6, "mb", "megabyte", "megabytes")
        reg(D, "GB", 1e9, "gb", "gigabyte", "gigabytes")
        reg(D, "TB", 1e12, "tb", "terabyte", "terabytes")
        reg(D, "KiB", 1024.0, "kib")
        reg(D, "MiB", 1048576.0, "mib")
        reg(D, "GiB", 1073741824.0, "gib")
        reg(D, "TiB", 1099511627776.0, "tib")
        val G = CalcUnit.Dim.ANGLE
        reg(G, "rad", 1.0, "rad", "radian", "radians")
        reg(G, "°", Math.PI / 180.0, "deg", "degree", "degrees", "°")
        reg(G, "grad", Math.PI / 200.0, "grad", "gon")
        reg(G, "turn", 2.0 * Math.PI, "turn", "turns", "rev")
        val E = CalcUnit.Dim.ENERGY
        reg(E, "J", 1.0, "j", "joule", "joules")
        reg(E, "kJ", 1000.0, "kj", "kilojoule", "kilojoules")
        reg(E, "cal", 4.184, "cal", "calorie", "calories")
        reg(E, "kcal", 4184.0, "kcal", "kilocalorie", "kilocalories")
        reg(E, "Wh", 3600.0, "wh")
        reg(E, "kWh", 3.6e6, "kwh")
        reg(E, "BTU", 1055.05585262, "btu")
        val P = CalcUnit.Dim.POWER
        reg(P, "W", 1.0, "w", "watt", "watts")
        reg(P, "kW", 1000.0, "kw", "kilowatt", "kilowatts")
        reg(P, "hp", 745.69987158227, "hp", "horsepower")
        val R = CalcUnit.Dim.PRESSURE
        reg(R, "Pa", 1.0, "pa", "pascal", "pascals")
        reg(R, "kPa", 1000.0, "kpa")
        reg(R, "bar", 1e5, "bar", "bars")
        reg(R, "mbar", 100.0, "mbar")
        reg(R, "atm", 101325.0, "atm")
        reg(R, "psi", 6894.757293168, "psi")
        reg(R, "mmHg", 133.322387415, "mmhg", "torr")
    }

    /** The unit called [name] (any case; "km/h" style names included), or null. */
    fun find(name: String): CalcUnit? = table[name.lowercase()]

    /** True for names like "km/h" that contain a slash (the lexer joins them into one word). */
    fun isSlashUnit(name: String): Boolean = name.indexOf('/') > 0 && find(name) != null

    /** [v] in [from] expressed in [to], or null when they measure different things. */
    fun convert(v: Double, from: CalcUnit, to: CalcUnit): Double? = if (from.dim != to.dim) null else to.fromBase(from.toBase(v))
}
