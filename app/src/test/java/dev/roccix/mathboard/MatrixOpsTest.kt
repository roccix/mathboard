package dev.roccix.mathboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatrixOpsTest {

    private fun editor(rows: Int, cols: Int, vararg cells: String) =
        Editor(Editor.Kind.MATRIX, rows, cols, 1).apply { fill(this, 0, cells) }

    private fun fill(e: Editor, grid: Int, cells: Array<out String>) {
        cells.forEachIndexed { i, c -> e.select(grid, i); c.forEach { e.type(it.toString()) } }
    }

    private fun Editor.cells() = grids[0].let { g -> List(g.cells.size) { g.text(it) } }

    @Test fun parse() {
        assertEquals("3/4", Q.parse("3/4").toString())
        assertEquals("-5/2", Q.parse("−2,5").toString())
        assertEquals("1/2", Q.parse("½").toString())
        assertEquals("0", Q.parse("").toString())
        assertNull(Q.parse("x"))
    }

    @Test fun scalarTimesMatrix() {
        val e = editor(2, 2, "1", "2", "0", "-1")
        e.start(Editor.Op.SCALE)
        fill(e, 1, arrayOf("3"))
        e.evaluate()
        assertEquals(listOf("3", "6", "0", "−3"), e.cells())
    }

    @Test fun symbolicLambda() {
        val e = editor(2, 2, "1", "2", "0", "-1")
        e.start(Editor.Op.SCALE)
        fill(e, 1, arrayOf("k"))
        e.evaluate()
        assertEquals(listOf("k", "2k", "0", "−k"), e.cells())
    }

    @Test fun product() {
        val e = editor(2, 2, "1", "2", "3", "4")
        e.start(Editor.Op.MUL)
        fill(e, 1, arrayOf("0", "1", "1", "0"))
        e.evaluate()
        assertEquals(listOf("2", "1", "4", "3"), e.cells())
    }

    @Test fun incompatibleSizes() {
        val e = editor(2, 2, "1", "2", "3", "4")
        e.start(Editor.Op.ADD)
        e.resize(1, 0)
        e.evaluate()
        assertEquals(Editor.Err.SIZE, e.error)
    }

    @Test fun determinantAndInverse() {
        val e = editor(2, 2, "1", "2", "3", "4")
        e.start(Editor.Op.DET)
        assertEquals("det A = −2", e.result)
        assertEquals("−2", e.output(tex = false))

        e.start(Editor.Op.INV)
        assertEquals(listOf("−2", "1", "3/2", "−1/2"), e.cells())
        assertEquals("\\begin{bmatrix} -2 & 1 \\\\ \\frac{3}{2} & -\\frac{1}{2} \\end{bmatrix}", e.output(tex = true))
    }

    @Test fun singular() {
        val e = editor(2, 2, "1", "2", "2", "4")
        e.start(Editor.Op.INV)
        assertEquals(Editor.Err.SINGULAR, e.error)
        e.start(Editor.Op.RANK)
        assertEquals("rank A = 1", e.result)
    }

    @Test fun rrefAndPower() {
        val e = editor(2, 3, "1", "2", "3", "2", "4", "7")
        e.start(Editor.Op.RREF)
        assertEquals(listOf("1", "2", "0", "0", "0", "1"), e.cells())

        val p = editor(2, 2, "1", "1", "0", "1")
        p.start(Editor.Op.POW)
        fill(p, 1, arrayOf("5"))
        p.evaluate()
        assertEquals(listOf("1", "5", "0", "1"), p.cells())
    }

    @Test fun resultsChainIntoNextOperation() {
        val e = editor(2, 2, "1", "2", "3", "4")
        e.start(Editor.Op.T)
        assertEquals(listOf("1", "3", "2", "4"), e.cells())
        e.start(Editor.Op.TRACE)
        assertEquals("tr A = 5", e.result)
    }
}
