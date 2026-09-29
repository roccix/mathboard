package dev.roccix.mathboard

import java.math.BigDecimal
import java.math.BigInteger

/** Numero razionale esatto: i calcoli sulle matrici non accumulano errori di arrotondamento. */
class Q private constructor(val n: BigInteger, val d: BigInteger) : Comparable<Q> {

    operator fun plus(o: Q) = of(n * o.d + o.n * d, d * o.d)
    operator fun minus(o: Q) = of(n * o.d - o.n * d, d * o.d)
    operator fun times(o: Q) = of(n * o.n, d * o.d)
    operator fun div(o: Q) = of(n * o.d, d * o.n)
    operator fun unaryMinus() = Q(-n, d)

    override fun compareTo(other: Q) = (n * other.d).compareTo(other.n * d)

    val isZero get() = n.signum() == 0
    val isInteger get() = d == BigInteger.ONE

    override fun equals(other: Any?) = other is Q && n == other.n && d == other.d
    override fun hashCode() = 31 * n.hashCode() + d.hashCode()
    override fun toString() = if (isInteger) "$n" else "$n/$d"

    companion object {
        val ZERO = Q(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Q(BigInteger.ONE, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger): Q {
            require(d.signum() != 0) { "division by zero" }
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            val s = if (d.signum() < 0) -BigInteger.ONE else BigInteger.ONE
            return Q(n / g * s, d / g * s)
        }

        fun of(n: Long) = of(BigInteger.valueOf(n), BigInteger.ONE)

        private val vulgar = mapOf(
            "½" to "1/2", "⅓" to "1/3", "¼" to "1/4", "⅔" to "2/3", "¾" to "3/4",
            "⅕" to "1/5", "⅙" to "1/6", "⅛" to "1/8",
        )
        private val decimal = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)")

        /** "3", "-2,5", "3/4", "3⁄4", "½", "" (= 0). null se non è un numero. */
        fun parse(raw: String): Q? {
            var s = raw.replace(" ", "").replace('−', '-').replace('⁄', '/').replace(',', '.')
            vulgar.forEach { (k, v) -> s = s.replace(k, v) }
            if (s.isEmpty()) return ZERO
            val parts = s.split('/')
            if (parts.size > 2) return null
            val num = dec(parts[0]) ?: return null
            if (parts.size == 1) return num
            val den = dec(parts[1]) ?: return null
            return if (den.isZero) null else num / den
        }

        private fun dec(s: String): Q? {
            if (!decimal.matches(s)) return null
            val bd = BigDecimal(s)
            return if (bd.scale() <= 0) of(bd.toBigIntegerExact(), BigInteger.ONE)
            else of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
        }
    }
}
