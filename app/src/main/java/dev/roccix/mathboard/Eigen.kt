package dev.roccix.mathboard

import java.math.BigInteger

/** Autovalori e diagonalizzazione con aritmetica esatta. I polinomi sono coefficienti crescenti: c[k]·λᵏ. */
object Eigen {

    /** p(λ) = det(λI − A), con Faddeev–LeVerrier (niente polinomi dentro la matrice). */
    fun charPoly(a: M): List<Q> {
        val n = a.size
        val c = MutableList(n + 1) { Q.ZERO }
        c[n] = Q.ONE
        var m: M = List(n) { List(n) { Q.ZERO } }
        for (k in 1..n) {
            val am = MatrixOps.mul(a, m)!!
            m = List(n) { i -> List(n) { j -> am[i][j] + if (i == j) c[n - k + 1] else Q.ZERO } }
            c[n - k] = -(MatrixOps.trace(MatrixOps.mul(a, m)!!)!! / Q.of(k.toLong()))
        }
        return c
    }

    /** Radici razionali con molteplicità, e il fattore che resta (senza radici razionali). */
    fun rationalRoots(poly: List<Q>): Pair<List<Pair<Q, Int>>, List<Q>> {
        var p = poly.toMutableList()
        val roots = LinkedHashMap<Q, Int>()
        while (p.size > 1 && p[0].isZero) {
            p.removeAt(0)
            roots[Q.ZERO] = (roots[Q.ZERO] ?: 0) + 1
        }
        search@ while (p.size > 1) {
            val ints = integerCoefficients(p)
            val a0 = ints.first().abs()
            val an = ints.last().abs()
            if (a0.bitLength() > 40 || an.bitLength() > 40) break   // troppo grandi per provarli tutti
            for (num in divisors(a0)) for (den in divisors(an)) for (sign in listOf(1L, -1L)) {
                val r = Q.of(num * BigInteger.valueOf(sign), den)
                if (eval(p, r).isZero) {
                    p = divide(p, r)
                    roots[r] = (roots[r] ?: 0) + 1
                    continue@search
                }
            }
            break
        }
        return roots.entries.map { it.key to it.value }.sortedByDescending { it.first } to p
    }

    /** Base del nucleo di [a], con vettori a coefficienti interi e primi tra loro. */
    fun nullspace(a: M): List<List<Q>> {
        val (r, _) = MatrixOps.rref(a)
        val cols = MatrixOps.cols(a)
        val pivots = r.mapNotNull { row -> row.indexOfFirst { !it.isZero }.takeIf { it >= 0 } }
        return (0 until cols).filter { it !in pivots }.map { free ->
            val v = MutableList(cols) { Q.ZERO }
            v[free] = Q.ONE
            pivots.forEachIndexed { row, pc -> v[pc] = -r[row][free] }
            integerVector(v)
        }
    }

    // ------------------------------------------------------------------ formattazione

    /** λ³ − 2λ² + λ − 4 (Unicode) oppure \lambda^{3} - 2\lambda^{2} + ... (LaTeX). */
    fun polyText(c: List<Q>, tex: Boolean, x: String = if (tex) "\\lambda" else "λ"): String {
        val sb = StringBuilder()
        for (k in c.indices.reversed()) {
            val q = c[k]
            if (q.isZero) continue
            val neg = q.n.signum() < 0
            val abs = if (neg) -q else q
            if (sb.isEmpty()) { if (neg) sb.append(if (tex) "-" else "−") }
            else sb.append(if (neg) (if (tex) " - " else " − ") else " + ")
            val coef = when {
                k == 0 -> num(abs, tex)
                abs == Q.ONE -> ""
                abs.isInteger -> num(abs, tex)
                else -> if (tex) num(abs, true) else "(${abs})"
            }
            sb.append(coef)
            if (k >= 1) sb.append(x)
            if (k >= 2) sb.append(if (tex) "^{$k}" else sup(k))
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    /** Radici di un polinomio di secondo grado senza radici razionali: 1 ± √2, 1 ± 2i. */
    fun quadraticRoots(p: List<Q>, tex: Boolean): String? {
        if (p.size != 3) return null
        val (c, b, a) = p
        val disc = b * b - Q.of(4) * a * c
        val centre = -b / (Q.of(2) * a)
        val neg = disc.n.signum() < 0
        // √(n/d) = √(n·d)/d = k√r / d
        val (k, r) = squareFree((disc.n * disc.d).abs()) ?: return null
        val t = Q.of(k, disc.d) / (Q.of(2) * a).let { if (it.n.signum() < 0) -it else it }
        val root = if (r == BigInteger.ONE) "" else if (tex) "\\sqrt{$r}" else "√$r"
        val i = if (neg) "i" else ""
        val coef = if (t == Q.ONE && (root.isNotEmpty() || neg)) "" else num(t, tex)
        val part = if (neg && root.isNotEmpty()) "$coef$i$root" else "$coef$root$i"
        val pm = if (tex) "\\pm" else "±"
        return if (centre.isZero) "$pm $part" else "${num(centre, tex)} $pm $part"
    }

    fun num(q: Q, tex: Boolean): String {
        if (!tex) return Grid.fmt(q)
        if (q.isInteger) return q.toString()
        val sign = if (q.n.signum() < 0) "-" else ""
        return "$sign\\frac{${q.n.abs()}}{${q.d}}"
    }

    private fun sup(k: Int) = k.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

    // ------------------------------------------------------------------ aritmetica

    private fun eval(p: List<Q>, x: Q) = p.asReversed().fold(Q.ZERO) { acc, c -> acc * x + c }

    /** p(λ) / (λ − r), divisione sintetica. */
    private fun divide(p: List<Q>, r: Q): MutableList<Q> {
        val desc = p.asReversed()
        val out = ArrayList<Q>()
        var acc = Q.ZERO
        for (i in 0 until desc.size - 1) {
            acc = acc * r + desc[i]
            out += acc
        }
        return out.asReversed().toMutableList()
    }

    private fun integerCoefficients(p: List<Q>): List<BigInteger> {
        val l = p.fold(BigInteger.ONE) { acc, q -> acc / acc.gcd(q.d) * q.d }
        return p.map { it.n * (l / it.d) }
    }

    private fun integerVector(v: List<Q>): List<Q> {
        val l = v.fold(BigInteger.ONE) { acc, q -> acc / acc.gcd(q.d) * q.d }
        val ints = v.map { it.n * (l / it.d) }
        val g = ints.fold(BigInteger.ZERO) { acc, x -> acc.gcd(x) }.let { if (it.signum() == 0) BigInteger.ONE else it }
        // primo elemento non nullo positivo, per risultati più leggibili
        val s = if ((ints.firstOrNull { it.signum() != 0 }?.signum() ?: 1) < 0) -BigInteger.ONE else BigInteger.ONE
        return ints.map { Q.of(it / g * s, BigInteger.ONE) }
    }

    private fun divisors(n: BigInteger): List<BigInteger> {
        val x = n.toLong()
        val small = ArrayList<Long>()
        val large = ArrayList<Long>()
        var i = 1L
        while (i * i <= x) {
            if (x % i == 0L) {
                small += i
                if (i != x / i) large += x / i
            }
            i++
        }
        return (small + large.asReversed()).map { BigInteger.valueOf(it) }
    }

    /** n = k²·r con r senza quadrati; null se n è troppo grande. */
    private fun squareFree(n: BigInteger): Pair<BigInteger, BigInteger>? {
        if (n.bitLength() > 40) return null
        var r = n.toLong()
        var k = 1L
        var f = 2L
        while (f * f <= r) {
            while (r % (f * f) == 0L) { r /= f * f; k *= f }
            f++
        }
        return BigInteger.valueOf(k) to BigInteger.valueOf(r)
    }
}
