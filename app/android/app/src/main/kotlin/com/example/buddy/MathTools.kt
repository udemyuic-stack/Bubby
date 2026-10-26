package com.example.buddy

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow

/** Exact arithmetic and unit conversion for the AI, so numbers are never guessed. No Android classes: testable on the JVM. */
object MathTools {
    /** Number as short plain text: 12600000, 3.10686, 0.5 (10 significant digits). */
    fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) throw ArithmeticException("the result is not a finite number")
        if (v == 0.0) return "0"
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    // ---- calculator ----

    /** Evaluates + - * / ^ ( ), sqrt sin cos tan asin acos atan log ln exp abs floor ceil round, constants pi and e. */
    fun calc(exprIn: String): Double {
        val s = exprIn.lowercase().replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').replace("**", "^")
        if (s.isBlank()) throw IllegalArgumentException("empty expression")
        return Parser(s).parse()
    }

    private class Parser(val s: String) {
        var i = 0

        fun parse(): Double {
            val v = expr()
            skip()
            if (i < s.length) throw IllegalArgumentException("unexpected '${s[i]}'")
            return v
        }

        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun eat(c: Char): Boolean {
            skip()
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                v = if (eat('+')) v + term() else if (eat('-')) v - term() else return v
            }
        }

        fun term(): Double {
            var v = unary()
            while (true) {
                v = if (eat('*')) v * unary()
                else if (eat('/')) {
                    val d = unary()
                    if (d == 0.0) throw ArithmeticException("division by zero")
                    v / d
                } else return v
            }
        }

        fun unary(): Double = if (eat('-')) -unary() else if (eat('+')) unary() else power()

        // right-associative, and -2^2 is -(2^2) because unary() calls power()
        fun power(): Double {
            val b = atom()
            return if (eat('^')) b.pow(unary()) else b
        }

        fun atom(): Double {
            skip()
            if (eat('(')) {
                val v = expr()
                if (!eat(')')) throw IllegalArgumentException("missing )")
                return v
            }
            if (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number '${s.substring(st, i)}'")
            }
            if (i < s.length && s[i].isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                if (name == "pi") return Math.PI
                if (name == "e") return Math.E
                if (!eat('(')) throw IllegalArgumentException("unknown name '$name'")
                val a = expr()
                if (!eat(')')) throw IllegalArgumentException("missing )")
                return when (name) {
                    "sqrt" -> if (a < 0) throw ArithmeticException("sqrt of a negative number") else Math.sqrt(a)
                    "sin" -> Math.sin(Math.toRadians(a))
                    "cos" -> Math.cos(Math.toRadians(a))
                    "tan" -> Math.tan(Math.toRadians(a))
                    "asin" -> Math.toDegrees(Math.asin(a))
                    "acos" -> Math.toDegrees(Math.acos(a))
                    "atan" -> Math.toDegrees(Math.atan(a))
                    "log" -> Math.log10(a)
                    "ln" -> Math.log(a)
                    "exp" -> Math.exp(a)
                    "abs" -> abs(a)
                    "floor" -> Math.floor(a)
                    "ceil" -> Math.ceil(a)
                    "round" -> Math.rint(a)
                    else -> throw IllegalArgumentException("unknown function '$name'")
                }
            }
            throw IllegalArgumentException(if (i < s.length) "unexpected '${s[i]}'" else "the expression ends too early")
        }
    }

    // ---- units ----

    private class U(val cat: String, val factor: Double, val label: String, vararg val names: String)

    private val UNITS = listOf(
        U("length", 0.001, "mm", "mm", "millimeter", "millimetre", "millimetr", "миллиметр", "мм"),
        U("length", 0.01, "cm", "cm", "centimeter", "centimetre", "santimetr", "сантиметр", "см"),
        U("length", 1.0, "m", "m", "meter", "metre", "meters", "metres", "metr", "метр", "м"),
        U("length", 1000.0, "km", "km", "kilometer", "kilometre", "kilometers", "kilometres", "kilometr", "километр", "км"),
        U("length", 0.0254, "in", "in", "inch", "inches", "dyuym", "дюйм"),
        U("length", 0.3048, "ft", "ft", "foot", "feet", "фут"),
        U("length", 0.9144, "yd", "yd", "yard", "yards", "ярд"),
        U("length", 1609.344, "mi", "mi", "mile", "miles", "milya", "миля", "миль"),
        U("mass", 0.000001, "mg", "mg", "milligram", "миллиграмм", "мг"),
        U("mass", 0.001, "g", "g", "gram", "gramm", "grams", "грамм", "г"),
        U("mass", 1.0, "kg", "kg", "kilogram", "kilogramm", "kilograms", "килограмм", "кг"),
        U("mass", 1000.0, "t", "t", "tonne", "tonna", "ton", "tons", "тонна", "т"),
        U("mass", 0.028349523125, "oz", "oz", "ounce", "ounces", "унция"),
        U("mass", 0.45359237, "lb", "lb", "lbs", "pound", "pounds", "funt", "фунт"),
        U("volume", 0.001, "ml", "ml", "milliliter", "millilitre", "millilitr", "миллилитр", "мл"),
        U("volume", 1.0, "l", "l", "liter", "litre", "liters", "litres", "litr", "литр", "л"),
        U("volume", 3.785411784, "gal", "gal", "gallon", "gallons", "галлон"),
        U("volume", 0.0295735295625, "fl oz", "floz", "fl oz"),
        U("speed", 1.0, "m/s", "m/s", "mps"),
        U("speed", 1.0 / 3.6, "km/h", "km/h", "kmh", "kph", "km/soat", "км/ч"),
        U("speed", 0.44704, "mph", "mph", "mi/h"),
        U("speed", 0.514444444444, "kn", "kn", "knot", "knots", "узел"),
        U("area", 1.0, "m²", "m2", "m²", "sqm"),
        U("area", 1_000_000.0, "km²", "km2", "km²"),
        U("area", 10_000.0, "ha", "ha", "hectare", "hectares", "hektar", "гектар", "га"),
        U("area", 0.09290304, "ft²", "ft2", "ft²", "sqft"),
        U("area", 4046.8564224, "acre", "acre", "acres", "акр"),
        U("time", 1.0, "s", "s", "sec", "second", "seconds", "soniya", "секунда", "секунд"),
        U("time", 60.0, "min", "min", "minute", "minutes", "daqiqa", "минута", "минут"),
        U("time", 3600.0, "h", "h", "hr", "hour", "hours", "soat", "час", "часов"),
        U("time", 86400.0, "day", "d", "day", "days", "kun", "день", "дней"),
        U("time", 604800.0, "week", "wk", "week", "weeks", "hafta", "неделя"),
        U("data", 1.0, "B", "b", "byte", "bytes", "bayt"),
        U("data", 1e3, "KB", "kb", "kilobyte", "kilobytes"),
        U("data", 1e6, "MB", "mb", "megabyte", "megabytes"),
        U("data", 1e9, "GB", "gb", "gigabyte", "gigabytes"),
        U("data", 1e12, "TB", "tb", "terabyte", "terabytes"),
        U("temperature", 1.0, "°C", "c", "°c", "celsius", "degc", "цельсий", "градус"),
        U("temperature", 1.0, "°F", "f", "°f", "fahrenheit", "degf"),
        U("temperature", 1.0, "K", "k", "kelvin")
    )

    private val BY_NAME: Map<String, U> = HashMap<String, U>().also { m -> for (u in UNITS) for (n in u.names) m[n] = u }

    private fun unit(raw: String): U {
        val k = raw.trim().lowercase().trimEnd('.')
        return BY_NAME[k] ?: throw IllegalArgumentException("unknown unit '$raw'")
    }

    private fun toCelsius(v: Double, u: U): Double = when (u.label) { "°F" -> (v - 32) * 5 / 9; "K" -> v - 273.15; else -> v }
    private fun fromCelsius(c: Double, u: U): Double = when (u.label) { "°F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }

    /** "5 km = 3.10686 mi". Throws IllegalArgumentException for unknown units or mixed kinds. */
    fun convert(value: Double, fromRaw: String, toRaw: String): String {
        val a = unit(fromRaw)
        val b = unit(toRaw)
        if (a.cat != b.cat) throw IllegalArgumentException("cannot convert ${a.cat} to ${b.cat}")
        val r = if (a.cat == "temperature") fromCelsius(toCelsius(value, a), b) else value * a.factor / b.factor
        return "${fmt(value)} ${a.label} = ${fmt(r)} ${b.label}"
    }
}
