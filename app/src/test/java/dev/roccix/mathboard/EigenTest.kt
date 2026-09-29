package dev.roccix.mathboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EigenTest {

    private fun editor(rows: Int, vararg cells: String) =
        Editor(Editor.Kind.MATRIX, rows, rows, 1).apply {
            cells.forEachIndexed { i, c -> select(0, i); c.forEach { type(it.toString()) } }
        }

    private fun Editor.cells(g: Int) = grids[g].let { m -> List(m.cells.size) { m.text(it) } }

    @Test fun characteristicPolynomial() {
        val e = editor(2, "1", "2", "3", "4")
        e.start(Editor.Op.POLY)
        assertEquals("p(λ) = λ² − 5λ − 2", e.result)
        assertEquals("p(\\lambda) = \\lambda^{2} - 5\\lambda - 2", e.output(tex = true))
    }

    @Test fun rationalEigenvaluesWithMultiplicity() {
        val e = editor(3, "2", "0", "0", "0", "2", "0", "0", "0", "-1")
        e.start(Editor.Op.EIG)
        assertEquals("λ = 2 (×2), −1", e.result)
    }

    @Test fun irrationalAndComplexEigenvalues() {
        val sqrt = editor(2, "1", "1", "1", "-1")      // λ² − 2
        sqrt.start(Editor.Op.EIG)
        assertEquals("λ = ± √2", sqrt.result)

        val rot = editor(2, "0", "-1", "1", "0")        // λ² + 1
        rot.start(Editor.Op.EIG)
        assertEquals("λ = ± i", rot.result)

        val c = editor(2, "1", "-2", "2", "1")          // 1 ± 2i
        c.start(Editor.Op.EIG)
        assertEquals("λ = 1 ± 2i", c.result)
    }

    @Test fun diagonalize() {
        val e = editor(2, "4", "1", "2", "3")
        e.start(Editor.Op.DIAG)
        assertTrue(e.diagonal)
        assertEquals(listOf("1", "1", "1", "−2"), e.cells(0))   // P
        assertEquals(listOf("5", "0", "0", "2"), e.cells(1))    // D
        assertEquals(
            "P = \\begin{bmatrix} 1 & 1 \\\\ 1 & -2 \\end{bmatrix},\\quad D = \\begin{bmatrix} 5 & 0 \\\\ 0 & 2 \\end{bmatrix}",
            e.output(tex = true),
        )
        // la operazione successiva riparte dalla A originale
        e.start(Editor.Op.DET)
        assertFalse(e.diagonal)
        assertEquals("det A = 10", e.result)
    }

    @Test fun notDiagonalizable() {
        val e = editor(2, "1", "1", "0", "1")
        e.start(Editor.Op.DIAG)
        assertEquals(Editor.Err.NOT_DIAGONALIZABLE, e.error)

        val r = editor(2, "0", "-1", "1", "0")
        r.start(Editor.Op.DIAG)
        assertEquals(Editor.Err.IRRATIONAL, r.error)
    }
}
