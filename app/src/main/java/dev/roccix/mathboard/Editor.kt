package dev.roccix.mathboard

/** Una matrice (o uno scalare 1×1) in composizione. Ogni casella è la lista dei simboli digitati. */
class Grid(rows: Int, cols: Int, val name: String) {
    var rows = rows
        private set
    var cols = cols
        private set
    var cells: MutableList<ArrayList<String>> = MutableList(rows * cols) { ArrayList() }
        private set

    /** λ o n: un solo valore, senza parentesi né ridimensionamento. */
    val isScalar get() = name == "λ" || name == "n"

    fun text(i: Int) = cells[i].joinToString("")

    /** Cambia dimensione mantenendo gli elementi che restano dentro. */
    fun resize(r: Int, c: Int) {
        cells = MutableList(r * c) { i ->
            val ri = i / c
            val ci = i % c
            if (ri < rows && ci < cols) cells[ri * cols + ci] else ArrayList()
        }
        rows = r
        cols = c
    }

    /** Valori numerici esatti; null se una casella non è un numero. Le caselle vuote valgono 0. */
    fun numeric(): M? = List(rows) { i -> List(cols) { j -> Q.parse(text(i * cols + j)) ?: return null } }

    companion object {
        fun of(m: M, name: String) = Grid(MatrixOps.rows(m), MatrixOps.cols(m), name).also { g ->
            m.flatten().forEachIndexed { i, q -> g.cells[i] += fmt(q) }
        }

        /** Il meno tipografico (−) è largo come una cifra, il trattino (-) no. */
        fun fmt(q: Q) = q.toString().replace('-', '−')
    }
}

/**
 * Struttura in composizione sopra la tastiera: una matrice (con calcoli) o una frazione.
 * La conversione in LaTeX avviene solo all'inserimento.
 */
class Editor(val kind: Kind, rows: Int, cols: Int, val bracket: Int = 0) {

    enum class Kind { MATRIX, FRACTION }

    /** Operazioni; [operand] è il nome del secondo dato da compilare (null se non serve). */
    enum class Op(val label: String, val operand: String?) {
        SCALE("λ·A", "λ"), ADD("A+B", "B"), SUB("A−B", "B"), MUL("A·B", "B"), POW("Aⁿ", "n"),
        T("Aᵀ", null), INV("A⁻¹", null), DET("det", null), RANK("rank", null), TRACE("tr", null), RREF("rref", null),
        POLY("p(λ)", null), EIG("λᵢ", null), DIAG("diag", null),
    }

    enum class Err { NOT_NUMERIC, SIZE, SQUARE, SINGULAR, EXPONENT, NOT_DIAGONALIZABLE, IRRATIONAL }

    val grids = mutableListOf(Grid(rows, cols, if (kind == Kind.MATRIX) "A" else ""))
    var g = 0
        private set
    var cur = 0
        private set
    var op: Op? = null
        private set
    /** Risultato non matriciale da mostrare, es. "det A = 5". */
    var result: String? = null
        private set
    /** Cosa inserisce ✓ al posto della matrice: (Unicode, LaTeX). */
    private var resultInsert: Pair<String, String>? = null
    /** Dopo "diag" le matrici sono P e D; la A di partenza resta qui per le operazioni successive. */
    var diagonal = false
        private set
    private var beforeDiag: Grid? = null
    var error: Err? = null
        private set

    val grid get() = grids[g]
    private val a get() = grids[0]

    fun type(s: String) {
        grid.cells[cur] += s
        touched()
    }

    fun backspace() {
        val c = grid.cells[cur]
        if (c.isNotEmpty()) c.removeAt(c.lastIndex)
        touched()
    }

    /** Casella precedente/successiva, passando da una matrice all'altra. */
    fun move(dir: Int) {
        var c = cur + dir
        if (c < 0 && g > 0) { g--; c = grid.cells.lastIndex }
        else if (c > grid.cells.lastIndex && g < grids.lastIndex) { g++; c = 0 }
        cur = c.coerceIn(0, grid.cells.lastIndex)
    }

    fun select(gi: Int, i: Int) {
        g = gi.coerceIn(0, grids.lastIndex)
        cur = i.coerceIn(0, grid.cells.lastIndex)
    }

    fun resize(dRows: Int, dCols: Int) {
        if (kind != Kind.MATRIX || grid.isScalar || diagonal) return
        grid.resize((grid.rows + dRows).coerceIn(1, MAX), (grid.cols + dCols).coerceIn(1, MAX))
        cur = cur.coerceIn(0, grid.cells.lastIndex)
        touched()
    }

    private fun touched() {
        result = null
        resultInsert = null
        error = null
    }

    // ------------------------------------------------------------------ operazioni

    fun start(o: Op) {
        beforeDiag?.let { grids.clear(); grids += it }
        leaveDiagonal()
        dropOperand()
        touched()
        val name = o.operand
        if (name == null) {
            compute(o, null)
            return
        }
        op = o
        grids += if (name == "B") Grid(if (o == Op.MUL) a.cols else a.rows, a.cols, "B") else Grid(1, 1, name)
        g = 1
        cur = 0
    }

    /** Il tasto "=": calcola l'operazione in attesa del secondo dato. */
    fun evaluate() {
        val o = op ?: return
        compute(o, grids.getOrNull(1))
    }

    /** Annulla l'operazione in attesa e torna alla sola A. */
    fun cancelOp() {
        dropOperand()
        touched()
    }

    private fun dropOperand() {
        while (grids.size > 1) grids.removeAt(grids.lastIndex)
        op = null
        g = 0
        cur = 0
    }

    private fun compute(o: Op, b: Grid?) {
        if (o == Op.SCALE) {
            val s = b!!.text(0)
            if (s.isEmpty()) return fail(Err.NOT_NUMERIC)
            return replaceA(scale(a, s))
        }
        val am = a.numeric() ?: return fail(Err.NOT_NUMERIC)
        val square = MatrixOps.rows(am) == MatrixOps.cols(am)
        val res: M = when (o) {
            Op.ADD, Op.SUB, Op.MUL -> {
                val bm = b!!.numeric() ?: return fail(Err.NOT_NUMERIC)
                when (o) {
                    Op.ADD -> MatrixOps.add(am, bm)
                    Op.SUB -> MatrixOps.sub(am, bm)
                    else -> MatrixOps.mul(am, bm)
                } ?: return fail(Err.SIZE)
            }
            Op.POW -> {
                val n = b!!.text(0).replace('−', '-').trim().toIntOrNull() ?: return fail(Err.EXPONENT)
                if (!square) return fail(Err.SQUARE)
                MatrixOps.pow(am, n) ?: return fail(Err.SINGULAR)
            }
            Op.T -> MatrixOps.transpose(am)
            Op.INV -> {
                if (!square) return fail(Err.SQUARE)
                MatrixOps.inverse(am) ?: return fail(Err.SINGULAR)
            }
            Op.RREF -> MatrixOps.rref(am).first
            Op.DET -> return scalar("det A", MatrixOps.det(am) ?: return fail(Err.SQUARE))
            Op.TRACE -> return scalar("tr A", MatrixOps.trace(am) ?: return fail(Err.SQUARE))
            Op.RANK -> return scalar("rank A", Q.of(MatrixOps.rank(am).toLong()))
            Op.POLY -> {
                if (!square) return fail(Err.SQUARE)
                val c = Eigen.charPoly(am)
                val uni = "p(λ) = " + Eigen.polyText(c, false)
                return text(uni, uni, "p(\\lambda) = " + Eigen.polyText(c, true))
            }
            Op.EIG -> {
                if (!square) return fail(Err.SQUARE)
                return eigenvalues(am)
            }
            Op.DIAG -> {
                if (!square) return fail(Err.SQUARE)
                return diagonalize(am)
            }
            Op.SCALE -> throw IllegalStateException()
        }
        replaceA(Grid.of(res, "A"))
    }

    /** Il risultato diventa la nuova A: si possono concatenare più operazioni. */
    private fun replaceA(r: Grid) {
        leaveDiagonal()
        grids.clear()
        grids += r
        dropOperand()
    }

    private fun scalar(label: String, q: Q) = text("$label = ${Grid.fmt(q)}", Grid.fmt(q), Eigen.num(q, true))

    private fun text(shown: String, uni: String, tex: String) {
        dropOperand()
        result = shown
        resultInsert = uni to tex
    }

    private fun eigenvalues(am: M) {
        val (roots, rest) = Eigen.rationalRoots(Eigen.charPoly(am))
        fun list(tex: Boolean) = buildList {
            roots.forEach { (r, m) -> add(Eigen.num(r, tex) + if (m > 1) (if (tex) "\\ (\\times $m)" else " (×$m)") else "") }
            if (rest.size > 1) add(
                Eigen.quadraticRoots(rest, tex)
                    ?: (Eigen.polyText(rest, tex) + " = 0")
            )
        }.joinToString(if (tex) ",\\ " else ", ")
        val uni = "λ = " + list(false)
        text(uni, uni, "\\lambda = " + list(true))
    }

    /** A = P·D·P⁻¹: le colonne di P sono basi degli autospazi. */
    private fun diagonalize(am: M) {
        val n = am.size
        val (roots, rest) = Eigen.rationalRoots(Eigen.charPoly(am))
        if (rest.size > 1) return fail(Err.IRRATIONAL)
        val columns = ArrayList<List<Q>>()
        val diag = ArrayList<Q>()
        for ((r, mult) in roots) {
            val shifted = List(n) { i -> List(n) { j -> am[i][j] - if (i == j) r else Q.ZERO } }
            val basis = Eigen.nullspace(shifted)
            if (basis.size < mult) return fail(Err.NOT_DIAGONALIZABLE)
            columns += basis
            repeat(basis.size) { diag += r }
        }
        val p = MatrixOps.transpose(columns)
        val d = List(n) { i -> List(n) { j -> if (i == j) diag[i] else Q.ZERO } }
        val a = grids[0]
        dropOperand()
        grids.clear()
        grids += Grid.of(p, "P")
        grids += Grid.of(d, "D")
        beforeDiag = a
        diagonal = true
        result = "A = P·D·P⁻¹"
    }

    private fun leaveDiagonal() {
        diagonal = false
        beforeDiag = null
    }

    private fun fail(e: Err) {
        error = e
    }

    /** λ·A anche con λ o elementi simbolici: λ = k su (1 2; 0 −1) dà (k 2k; 0 −k). */
    private fun scale(m: Grid, s: String): Grid {
        val sq = Q.parse(s)
        val out = Grid(m.rows, m.cols, "A")
        for (i in m.cells.indices) {
            val t = m.text(i)
            val tq = Q.parse(t)
            out.cells[i] += when {
                sq != null && tq != null -> Grid.fmt(tq * sq)
                sq != null -> coef(sq, t)
                tq != null -> coef(tq, s)
                else -> s + wrapSym(t)
            }
        }
        return out
    }

    private fun coef(q: Q, sym: String) = when {
        q.isZero -> "0"
        q == Q.ONE -> sym
        q == -Q.ONE -> "−" + wrapSym(sym)
        q.isInteger -> Grid.fmt(q) + wrapSym(sym)
        else -> "(${Grid.fmt(q)})" + wrapSym(sym)
    }

    private fun wrapSym(x: String) = if (x.length > 1 && x.any { it in "+-− " }) "($x)" else x

    // ------------------------------------------------------------------ output

    /**
     * Testo da inserire. [linePrefix] è ciò che precede il cursore sulla stessa riga:
     * le righe successive di una matrice Unicode vengono rientrate di altrettanto.
     */
    fun output(tex: Boolean, linePrefix: String = ""): String {
        resultInsert?.let { return if (tex) it.second else it.first }
        if (diagonal) {
            val (p, d) = grids
            if (tex) return "P = ${texMatrix(p)},\\quad D = ${texMatrix(d)}"
            val base = indentFor(linePrefix)
            return "P = " + unicodeMatrix(p, indentFor(linePrefix + "P = ")) +
                "\n" + base + "D = " + unicodeMatrix(d, base + indentFor("D = "))
        }
        return when (kind) {
            Kind.FRACTION ->
                if (tex) "\\frac{${texCell(a, 0)}}{${texCell(a, 1)}}"
                else stackedFraction("", "").text
            Kind.MATRIX ->
                if (tex) texMatrix(a) else unicodeMatrix(a, indentFor(linePrefix))
        }
    }

    // ------------------------------------------------------------------ LaTeX

    private val texFraction = Regex("([−-]?)(\\d+)/(\\d+)")

    private fun texToken(t: String): String {
        texFraction.matchEntire(t)?.let { m ->
            val (sign, n, d) = m.destructured
            return (if (sign.isEmpty()) "" else "-") + "\\frac{$n}{$d}"
        }
        return Tex.of(t)
    }

    private fun texCell(m: Grid, i: Int) =
        m.cells[i].joinToString("") { texToken(it) }.replace(Regex(" +"), " ").trim()

    private fun texMatrix(m: Grid): String {
        val env = TEX_ENV[bracket]
        val body = (0 until m.rows).joinToString(" \\\\ ") { r ->
            (0 until m.cols).joinToString(" & ") { c -> texCell(m, r * m.cols + c) }
        }
        return "\\begin{$env} $body \\end{$env}"
    }

    // ------------------------------------------------------------------ Unicode

    /** Testo che sostituisce l'intera riga corrente, con la posizione del cursore dopo l'inserimento. */
    class Block(val text: String, val caret: Int)

    /**
     * Frazione su tre righe, con le regole delle matrici: numeratore e denominatore centrati
     * sul più lungo con spazi cifra, righe esterne rientrate come [prefix]. Il testo prima
     * ([prefix]) e dopo ([suffix]) il cursore resta sulla riga della linea di frazione, dove
     * rimane anche il cursore:
     *
     *         a + b
     *     x = ─────  + 1
     *           c
     */
    fun stackedFraction(prefix: String, suffix: String): Block {
        val n = a.text(0).trim()
        val d = a.text(1).trim()
        val w = maxOf(len(n), len(d), 1)
        fun centre(t: String): String {
            val pad = w - len(t)
            return (FIG + FIG.repeat(pad / 2) + t + FIG.repeat(pad - pad / 2) + FIG).trimEnd('\u2007')
        }
        val indent = indentFor(prefix)
        val head = indent + centre(n) + "\n" + prefix + BAR.repeat(w + 2)
        return Block(head + suffix + "\n" + indent + centre(d), head.length)
    }

    /**
     * Ogni colonna è larga quanto il suo elemento più lungo e gli altri vi sono centrati.
     * Il riempimento usa lo spazio cifra (U+2007), largo come una cifra anche nei font
     * proporzionali: con lo spazio normale, più stretto, le parentesi non resterebbero allineate.
     */
    private fun unicodeMatrix(m: Grid, indent: String): String {
        val texts = List(m.cells.size) { m.text(it) }
        val widths = List(m.cols) { c -> (0 until m.rows).maxOf { r -> len(texts[r * m.cols + c]) }.coerceAtLeast(1) }
        return (0 until m.rows).joinToString("\n$indent") { r ->
            val body = (0 until m.cols).joinToString(COL_SEP) { c ->
                val t = texts[r * m.cols + c]
                val pad = widths[c] - len(t)
                FIG.repeat(pad / 2) + t + FIG.repeat(pad - pad / 2)
            }
            val right = right(r, m.rows)
            if (right.isEmpty()) "${left(r, m.rows)}$FIG$body" else "${left(r, m.rows)}$FIG$body$FIG$right"
        }
    }

    private fun len(s: String) = s.codePointCount(0, s.length)

    /** Spazi di larghezza simile a ciascun carattere del prefisso. */
    private fun indentFor(prefix: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < prefix.length) {
            val cp = prefix.codePointAt(i)
            i += Character.charCount(cp)
            sb.append(
                when {
                    cp == ' '.code -> " "
                    cp == '\t'.code -> "\t"
                    Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> ""
                    cp.toChar() in NARROW -> PUNCT
                    else -> FIG
                }
            )
        }
        return sb.toString()
    }

    private fun left(i: Int, rows: Int): String = when (bracket) {
        0 -> piece(i, rows, "(", "⎛", "⎜", "⎝")
        1 -> piece(i, rows, "[", "⎡", "⎢", "⎣")
        2 -> "│"
        else -> when {
            rows == 1 -> "{"
            i == 0 -> "⎧"
            i == rows - 1 -> "⎩"
            i == rows / 2 && rows % 2 == 1 -> "⎨"
            else -> "⎪"
        }
    }

    private fun right(i: Int, rows: Int): String = when (bracket) {
        0 -> piece(i, rows, ")", "⎞", "⎟", "⎠")
        1 -> piece(i, rows, "]", "⎤", "⎥", "⎦")
        2 -> "│"
        else -> ""
    }

    private fun piece(i: Int, rows: Int, single: String, top: String, mid: String, bottom: String) = when {
        rows == 1 -> single
        i == 0 -> top
        i == rows - 1 -> bottom
        else -> mid
    }

    companion object {
        const val FRACTION = "a⁄b"
        /** Tasto che apre direttamente l'editor delle matrici. */
        const val CALC = "ƒ(A)"
        const val MAX = 8

        private const val FIG = " "    // spazio cifra: largo come una cifra
        private const val PUNCT = " "  // spazio punteggiatura: largo come un punto
        private const val COL_SEP = FIG + FIG
        private const val BAR = "─"          // si unisce senza interruzioni, largo circa come una cifra
        private const val NARROW = ".,:;'!|il()[]{}ıjft\"`"

        /** Tipi di parentesi, nello stesso ordine dei tasti della scheda matrici. */
        val BRACKETS = listOf("( )", "[ ]", "| |", "{")
        private val TEX_ENV = listOf("pmatrix", "bmatrix", "vmatrix", "cases")
    }
}
