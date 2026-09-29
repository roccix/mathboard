package dev.roccix.mathboard

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTest {

    private fun matrix(rows: Int, cols: Int, bracket: Int, vararg cells: String) =
        Editor(Editor.Kind.MATRIX, rows, cols, bracket).apply {
            cells.forEachIndexed { i, c -> select(0, i); c.forEach { type(it.toString()) } }
        }

    @Test fun columnsAreCentredOnTheWidestElement() {
        val out = matrix(2, 3, 2, "200", "4", "500", "3", "5000", "3").output(tex = false)
        val f = " "
        assertEquals(
            "│${f}200$f$f${f}4$f$f$f${f}500$f│\n" +
            "│$f${f}3$f$f${f}5000$f$f${f}3$f$f│",
            out,
        )
        // tutte le righe hanno la stessa lunghezza: le parentesi finali sono allineate
        val lines = out.split('\n')
        assertEquals(lines[0].length, lines[1].length)
    }

    @Test fun followingRowsAreIndentedLikeThePrefix() {
        val out = matrix(2, 1, 1, "1", "2").output(tex = false, linePrefix = "A = ")
        val lines = out.split('\n')
        assertEquals("    ", lines[1].substringBefore('⎣'))
    }

    @Test fun texMatrix() {
        val out = matrix(2, 2, 1, "1", "α", "", "x").output(tex = true)
        assertEquals("\\begin{bmatrix} 1 & \\alpha \\\\  & x \\end{bmatrix}", out)
    }

    @Test fun stackedFraction() {
        val f = "\u2007"
        val e = Editor(Editor.Kind.FRACTION, 2, 1).apply { "a+b".forEach { type(it.toString()) }; select(0, 1); type("c") }
        val b = e.stackedFraction("x = ", " + 1")
        val lines = b.text.split('\n')
        val indent = "$f $f "                                  // rientro largo come "x = "
        assertEquals("$indent${f}a+b", lines[0])                 // margine + numeratore
        assertEquals("x = ─────" + " + 1", lines[1])
        assertEquals("$indent$f${f}c", lines[2])                 // denominatore centrato sotto "a+b"
        // il cursore resta subito dopo la linea di frazione
        assertEquals("x = ─────", b.text.substring(0, b.caret).substringAfter('\n'))
        assertEquals("\\frac{a+b}{c}", e.output(tex = true))
    }
}
