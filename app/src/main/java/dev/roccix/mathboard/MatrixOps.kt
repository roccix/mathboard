package dev.roccix.mathboard

/** Matrice di razionali, per righe. */
typealias M = List<List<Q>>

object MatrixOps {

    fun rows(a: M) = a.size
    fun cols(a: M) = a.firstOrNull()?.size ?: 0

    fun transpose(a: M): M = List(cols(a)) { j -> List(rows(a)) { i -> a[i][j] } }

    fun scale(a: M, k: Q): M = a.map { r -> r.map { it * k } }

    fun add(a: M, b: M): M? =
        if (rows(a) != rows(b) || cols(a) != cols(b)) null
        else List(rows(a)) { i -> List(cols(a)) { j -> a[i][j] + b[i][j] } }

    fun sub(a: M, b: M): M? = add(a, scale(b, -Q.ONE))

    fun mul(a: M, b: M): M? {
        if (cols(a) != rows(b)) return null
        return List(rows(a)) { i ->
            List(cols(b)) { j -> (0 until cols(a)).fold(Q.ZERO) { s, k -> s + a[i][k] * b[k][j] } }
        }
    }

    fun identity(n: Int): M = List(n) { i -> List(n) { j -> if (i == j) Q.ONE else Q.ZERO } }

    /** Aⁿ per n intero; con n < 0 usa l'inversa. null se non quadrata o non invertibile. */
    fun pow(a: M, n: Int): M? {
        if (rows(a) != cols(a)) return null
        var base = if (n < 0) inverse(a) ?: return null else a
        var e = kotlin.math.abs(n)
        var r = identity(rows(a))
        while (e > 0) {
            if (e and 1 == 1) r = mul(r, base)!!
            base = mul(base, base)!!
            e = e shr 1
        }
        return r
    }

    fun trace(a: M): Q? =
        if (rows(a) != cols(a)) null else (0 until rows(a)).fold(Q.ZERO) { s, i -> s + a[i][i] }

    /** Forma a scala ridotta (Gauss-Jordan) e numero di pivot. */
    fun rref(a: M): Pair<M, Int> {
        val m = a.map { it.toMutableList() }.toMutableList()
        var pivotRow = 0
        for (c in 0 until cols(a)) {
            if (pivotRow == m.size) break
            val p = (pivotRow until m.size).firstOrNull { !m[it][c].isZero } ?: continue
            m[p] = m[pivotRow].also { m[pivotRow] = m[p] }
            val pv = m[pivotRow][c]
            m[pivotRow] = m[pivotRow].map { it / pv }.toMutableList()
            for (r in m.indices) if (r != pivotRow && !m[r][c].isZero) {
                val f = m[r][c]
                m[r] = m[r].mapIndexed { j, x -> x - f * m[pivotRow][j] }.toMutableList()
            }
            pivotRow++
        }
        return m to pivotRow
    }

    fun rank(a: M) = rref(a).second

    fun det(a: M): Q? {
        if (rows(a) != cols(a)) return null
        val m = a.map { it.toMutableList() }.toMutableList()
        var det = Q.ONE
        for (c in m.indices) {
            val p = (c until m.size).firstOrNull { !m[it][c].isZero } ?: return Q.ZERO
            if (p != c) { m[p] = m[c].also { m[c] = m[p] }; det = -det }
            det *= m[c][c]
            for (r in c + 1 until m.size) {
                val f = m[r][c] / m[c][c]
                m[r] = m[r].mapIndexed { j, x -> x - f * m[c][j] }.toMutableList()
            }
        }
        return det
    }

    /** null se non quadrata o singolare. */
    fun inverse(a: M): M? {
        val n = rows(a)
        if (n != cols(a)) return null
        val aug = List(n) { i -> a[i] + identity(n)[i] }
        val (r, _) = rref(aug)
        if ((0 until n).any { i -> r[i][i] != Q.ONE }) return null
        return r.map { it.drop(n) }
    }
}
