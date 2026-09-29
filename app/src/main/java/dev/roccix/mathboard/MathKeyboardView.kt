package dev.roccix.mathboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.max

/**
 * Tastiera disegnata interamente su Canvas.
 *
 * Gesti (in stile Gboard):
 *  - tocco: inserisce il simbolo principale
 *  - pressione prolungata: apre le varianti; scorri e rilascia per scegliere
 *  - scorrimento sulla barra spaziatrice: sposta il cursore
 *  - ⌫ e ‹ › tenuti premuti: si ripetono
 *  - pressione prolungata su ABC: selettore tastiere
 */
@SuppressLint("ViewConstructor")
class MathKeyboardView(context: Context, private val l: Listener) : View(context) {

    interface Listener {
        fun onSymbol(s: String)
        fun onBackspace()
        fun onEnter()
        fun onSpace()
        fun onCursor(dir: Int)
        fun onSwitchIme()
        fun onImePicker()
        fun onTexToggled(enabled: Boolean)
        fun onPageChanged(page: Int)
        fun recents(): List<String>
        fun onMatrix(rows: Int, cols: Int, bracket: Int)
        fun onBracketChanged(bracket: Int)
        fun onEditorTap(cell: Int)
        fun onEditorAction(act: String)
        fun onEditorCancel()
        fun onEditorCommit()
    }

    private enum class T { SYM, BACK, ENTER, SPACE, ABC, LEFT, RIGHT, TAB, TEX, GRID, BRACKET, P_CELL, P_CANCEL, P_OK, P_ACT }

    private class K(val t: T, val label: String, val alts: List<String> = emptyList(), val tab: Int = -1, val act: String = "") {
        val r = RectF()
    }

    private class Popup(val choices: List<String>, val cells: List<RectF>, val bg: RectF) {
        var sel = 0
    }

    // ---- stato pubblico ----
    var page = 1
        set(v) { field = v; rebuild() }
    var tex = false
        set(v) { field = v; invalidate() }
    var enterLabel = "⏎"
        set(v) { field = v; invalidate() }
    /** Matrice o frazione in composizione: se presente, compare un pannello sopra la tastiera. */
    var editor: Editor? = null
        set(v) { field = v; editorChanged() }

    /** Da chiamare quando cambia la struttura dell'editor (dimensioni, operazione, risultato). */
    fun editorChanged() {
        requestLayout()
        rebuild()
    }
    /** Parentesi della matrice: indice in [Editor.BRACKETS]. */
    var bracket = 0
        set(v) { field = v; invalidate() }

    // ---- dimensioni ----
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val tabH = dp(if (landscape) 34f else 42f)
    private val rowH = dp(if (landscape) 40f else 54f)
    private val gap = dp(3f)
    private val headerH = dp(44f)
    private val cellH = dp(if (landscape) 30f else 38f)
    private val infoH = dp(26f)
    private val opRowH = dp(40f)
    private val radius = dp(7f)

    // ---- colori (seguono il tema chiaro/scuro di sistema) ----
    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val cBg = if (dark) 0xFF1F1F1F.toInt() else 0xFFE8EAED.toInt()
    private val cKey = if (dark) 0xFF3C4043.toInt() else 0xFFFFFFFF.toInt()
    private val cSpecial = if (dark) 0xFF2D2F31.toInt() else 0xFFD2D5DA.toInt()
    private val cPressed = if (dark) 0xFF5F6368.toInt() else 0xFFBDC1C6.toInt()
    private val cShadow = if (dark) 0xFF101010.toInt() else 0xFFB8BCC2.toInt()
    private val cText = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
    private val cHint = if (dark) 0xFF9AA0A6.toInt() else 0xFF80868B.toInt()
    private val cAccent = if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
    private val cOnAccent = if (dark) 0xFF202124.toInt() else 0xFFFFFFFF.toInt()
    private val cPopup = if (dark) 0xFF4A4E52.toInt() else 0xFFFFFFFF.toInt()
    private val cError = if (dark) 0xFFF28B82.toInt() else 0xFFD93025.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }

    // ---- tasti e interazione ----
    private val keys = ArrayList<K>()
    private var active: K? = null
    private var activeId = -1
    private var popup: Popup? = null
    private var longFired = false
    private var spaceLastX = 0f
    private var spaceMoved = false
    private val h = Handler(Looper.getMainLooper())
    private var gridR = 0
    private var gridC = 0

    private val longPress = Runnable {
        val k = active ?: return@Runnable
        longFired = true
        when (k.t) {
            T.SYM -> if (k.alts.isNotEmpty()) { openPopup(k); haptic() }
            T.ABC -> { haptic(); l.onImePicker() }
            else -> {}
        }
    }

    private val repeater = object : Runnable {
        override fun run() {
            val k = active ?: return
            fireRepeatable(k)
            h.postDelayed(this, 50)
        }
    }

    /** Altezza della barra di sistema (⌄ e globo) che Android disegna sopra il fondo della tastiera. */
    private var navInset = 0

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val sysBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        // Con la navigazione a gesti Android dichiara solo la maniglia (~24dp), ma sotto la tastiera
        // disegna la propria barra con ⌄ e il globo, alta navigation_bar_frame_height (~48dp).
        val bottom = max(sysBottom, imeNavBarHeight()) + dp(4f).toInt()
        if (bottom != navInset) {
            navInset = bottom
            requestLayout()
        }
        return super.onApplyWindowInsets(insets)
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun imeNavBarHeight(): Int {
        val res = android.content.res.Resources.getSystem()
        val id = res.getIdentifier("navigation_bar_frame_height", "dimen", "android")
        return if (id != 0) res.getDimensionPixelSize(id) else dp(48f).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (panelH() + tabH + rowH * 5 + gap * 2).toInt() + navInset)
    }

    private fun panelH(): Float {
        val e = editor ?: return 0f
        val area = e.grids.maxOf { it.rows } * gridCellH(e) + dp(12f)
        return headerH + area + if (e.kind == Editor.Kind.MATRIX) infoH + opRowH * 3 + dp(6f) else 0f
    }

    /** Con molte righe le caselle si abbassano, così il pannello non cresce troppo. */
    private fun gridCellH(e: Editor): Float {
        val rows = e.grids.maxOf { it.rows }
        return if (rows <= 4) cellH else max(dp(22f), cellH * 4 / rows)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    fun rebuild() {
        if (width == 0) return
        cancelActive()
        keys.clear()
        val w = width.toFloat()
        val top = panelH()
        editor?.let { layoutPanel(it, w) }

        // barra delle categorie + interruttore TeX
        val n = Pages.all.size + 1
        val tw = w / n
        Pages.all.forEachIndexed { i, p ->
            keys += K(T.TAB, p.icon, tab = i).also { it.r.set(i * tw, top, (i + 1) * tw, top + tabH) }
        }
        keys += K(T.TEX, "TeX").also { it.r.set(w - tw, top, w, top + tabH) }

        // griglia dei simboli
        val rows: List<List<String>> =
            if (page == Pages.RECENTS) l.recents().take(32).chunked(8)
            else Pages.all[page].rows
        val gridTop = top + tabH + gap
        val gridH = rowH * 4
        if (Pages.all[page].matrix) {
            // a sinistra la griglia per scegliere la dimensione, a destra i tasti
            val split = w * 0.46f
            keys += K(T.GRID, "").also { it.r.set(gap, gridTop + gap / 2, split - gap / 2, gridTop + gridH - gap / 2) }
            layoutRows(rows, split, w - split - gap / 2, gridTop, gridH, 4)
        } else {
            layoutRows(rows, gap / 2, w - gap, gridTop, gridH, 8)
        }

        // riga inferiore fissa
        val bottom = listOf(
            K(T.ABC, "ABC") to 1.5f, K(T.LEFT, "‹") to 1f, K(T.SPACE, "") to 4f,
            K(T.RIGHT, "›") to 1f, K(T.BACK, "⌫") to 1.5f, K(T.ENTER, "") to 1.5f,
        )
        val unit = (w - gap) / bottom.sumOf { it.second.toDouble() }.toFloat()
        var x = gap / 2
        val y = gridTop + gridH
        for ((k, weight) in bottom) {
            k.r.set(x + gap / 2, y + gap / 2, x + unit * weight - gap / 2, y + rowH - gap / 2)
            x += unit * weight
            keys += k
        }
        invalidate()
    }

    private fun layoutRows(rows: List<List<String>>, left: Float, areaW: Float, top: Float, areaH: Float, minCols: Int) {
        if (rows.isEmpty()) return
        val rh = areaH / rows.size
        val kw = areaW / max(minCols, rows.maxOf { it.size })
        rows.forEachIndexed { ri, row ->
            val x0 = left + (areaW - row.size * kw) / 2
            val y = top + ri * rh
            row.forEachIndexed { ci, spec ->
                val k = when {
                    spec in Editor.BRACKETS -> K(T.BRACKET, spec, tab = Editor.BRACKETS.indexOf(spec))
                    else -> spec.split(' ').filter { it.isNotEmpty() }.let { K(T.SYM, it[0], it.drop(1)) }
                }
                val x = x0 + ci * kw
                k.r.set(x + gap / 2, y + gap / 2, x + kw - gap / 2, y + rh - gap / 2)
                keys += k
            }
        }
    }

    private val gridBoxes = ArrayList<RectF>()
    private val opSymbols = ArrayList<Pair<String, Float>>()
    private val rowsNum = RectF()
    private val colsNum = RectF()
    private var areaTop = 0f
    private var areaBottom = 0f

    private fun act(act: String, label: String, l: Float, t: Float, r: Float, b: Float) {
        keys += K(T.P_ACT, label, act = act).also { it.r.set(l, t, r, b) }
    }

    private fun layoutPanel(e: Editor, w: Float) {
        keys += K(T.P_CANCEL, "✕").also { it.r.set(gap, gap, dp(46f), headerH - gap) }
        keys += K(T.P_OK, "✓").also { it.r.set(w - dp(118f), dp(6f), w - dp(8f), headerH - dp(6f)) }

        // − righe +  ×  − colonne +   (per la matrice selezionata)
        if (e.kind == Editor.Kind.MATRIX && !e.grid.isScalar && !e.diagonal) {
            val bw = dp(30f)
            val nw = dp(22f)
            val xw = dp(16f)
            var x = (w - (bw * 4 + nw * 2 + xw)) / 2 - dp(14f)
            val t = dp(7f)
            val b = headerH - dp(7f)
            act("r-", "−", x, t, x + bw, b); x += bw
            rowsNum.set(x, t, x + nw, b); x += nw
            act("r+", "+", x, t, x + bw, b); x += bw + xw
            act("c-", "−", x, t, x + bw, b); x += bw
            colsNum.set(x, t, x + nw, b); x += nw
            act("c+", "+", x, t, x + bw, b)
        }

        // matrici affiancate con il simbolo dell'operazione in mezzo
        val ch = gridCellH(e)
        areaTop = headerH
        areaBottom = headerH + e.grids.maxOf { it.rows } * ch + dp(12f)
        val items: List<Any> = when (e.op) {
            null -> if (e.diagonal) listOf("P =", 0, "D =", 1) else listOf(0)
            Editor.Op.SCALE -> listOf(1, "·", 0)
            Editor.Op.POW -> listOf(0, "^", 1)
            Editor.Op.ADD -> listOf(0, "+", 1)
            Editor.Op.SUB -> listOf(0, "−", 1)
            else -> listOf(0, "·", 1)
        }
        fun opW(sym: String) = if (sym.length > 1) dp(40f) else dp(26f)
        val brPad = if (e.kind == Editor.Kind.MATRIX) dp(16f) else 0f
        fun padOf(gi: Int) = if (e.grids[gi].isScalar) 0f else brPad
        val fixed = items.sumOf { (if (it is Int) padOf(it) * 2 else opW(it as String)).toDouble() }.toFloat()
        val totalCols = items.filterIsInstance<Int>().sumOf { e.grids[it].cols }
        val maxCell = if (e.kind == Editor.Kind.FRACTION) dp(220f) else dp(96f)
        val cw = minOf(maxCell, (w - dp(16f) - fixed) / totalCols)
        var x = (w - (fixed + cw * totalCols)) / 2
        gridBoxes.clear()
        repeat(e.grids.size) { gridBoxes += RectF() }
        opSymbols.clear()
        for (item in items) {
            if (item is String) {
                opSymbols += item to x + opW(item) / 2
                x += opW(item)
                continue
            }
            val gi = item as Int
            val grid = e.grids[gi]
            val pad = padOf(gi)
            val left = x + pad
            val top = areaTop + (areaBottom - areaTop - grid.rows * ch) / 2
            gridBoxes[gi].set(left, top, left + grid.cols * cw, top + grid.rows * ch)
            for (i in 0 until grid.rows) for (j in 0 until grid.cols) {
                val cx = left + j * cw
                val cy = top + i * ch
                keys += K(T.P_CELL, "", tab = gi * 1000 + i * grid.cols + j).also {
                    it.r.set(cx + dp(2.5f), cy + dp(2.5f), cx + cw - dp(2.5f), cy + ch - dp(2.5f))
                }
            }
            x += pad * 2 + grid.cols * cw
        }

        // operazione in attesa del secondo dato: un solo pulsante grande ed esplicito
        val pending = e.op
        if (e.kind == Editor.Kind.MATRIX && pending != null) {
            val top = areaBottom + infoH
            act("eq", "= " + context.getString(R.string.calc_button, pending.label),
                gap, top + gap / 2, w - gap, top + opRowH * 2 - gap / 2)
            act("opx", "✕  " + context.getString(R.string.op_cancel),
                gap, top + opRowH * 2 + gap / 2, w - gap, top + opRowH * 3 - gap / 2)
        } else if (e.kind == Editor.Kind.MATRIX) {
            val rowsOfOps = listOf(
                listOf(Editor.Op.SCALE, Editor.Op.ADD, Editor.Op.SUB, Editor.Op.MUL, Editor.Op.POW).map { "op:${it.name}" to it.label },
                listOf(Editor.Op.T, Editor.Op.INV, Editor.Op.DET, Editor.Op.RANK, Editor.Op.TRACE, Editor.Op.RREF).map { "op:${it.name}" to it.label },
                listOf(Editor.Op.POLY, Editor.Op.EIG, Editor.Op.DIAG).map { "op:${it.name}" to it.label },
            )
            val top = areaBottom + infoH
            rowsOfOps.forEachIndexed { ri, row ->
                val bw = (w - gap * 2) / row.size
                row.forEachIndexed { ci, (a, label) ->
                    val l = gap + ci * bw
                    val t = top + ri * opRowH
                    act(a, label, l + gap / 2, t + gap / 2, l + bw - gap / 2, t + opRowH - gap / 2)
                }
            }
        }
    }

    private fun drawPanel(c: Canvas, e: Editor) {
        fill.color = cSpecial
        c.drawRect(0f, 0f, width.toFloat(), panelH(), fill)
        if (e.kind == Editor.Kind.FRACTION) {
            label(c, context.getString(R.string.editor_fraction), width / 2f, headerH / 2, width * 0.4f, 15f, cText, bold = true)
        } else if (!e.grid.isScalar) {
            label(c, "${e.grid.rows}", rowsNum.centerX(), rowsNum.centerY(), rowsNum.width(), 17f, cText, bold = true)
            label(c, "×", (rowsNum.right + colsNum.left) / 2 + dp(15f), rowsNum.centerY(), dp(16f), 15f, cHint)
            label(c, "${e.grid.cols}", colsNum.centerX(), colsNum.centerY(), colsNum.width(), 17f, cText, bold = true)
        }

        stroke.color = cText
        stroke.strokeWidth = dp(2f)
        if (e.kind == Editor.Kind.FRACTION) {
            val b = gridBoxes[0]
            val y = (b.top + b.bottom) / 2
            c.drawLine(b.left + dp(6f), y, b.right - dp(6f), y, stroke)
        } else {
            e.grids.forEachIndexed { gi, grid ->
                if (grid.isScalar) return@forEachIndexed
                val b = gridBoxes[gi]
                drawBracket(c, e.bracket, b.left - dp(10f), b.top + dp(2f), b.bottom - dp(2f), true)
                drawBracket(c, e.bracket, b.right + dp(10f), b.top + dp(2f), b.bottom - dp(2f), false)
            }
        }
        stroke.strokeWidth = dp(1.5f)
        val midY = (areaTop + areaBottom) / 2
        for ((sym, x) in opSymbols) label(c, sym, x, midY, dp(38f), if (sym.length > 1) 15f else 20f, cText, bold = true)

        if (e.kind == Editor.Kind.MATRIX) {
            val y = areaBottom + infoH / 2
            val err = e.error
            val op = e.op
            when {
                err != null -> label(c, context.getString(errorText(err)), width / 2f, y, width * 0.94f, 13f, cError)
                e.result != null -> label(c, e.result!!, width / 2f, y, width * 0.94f, 16f, cAccent, bold = true)
                op != null -> label(c, context.getString(R.string.op_pending, op.operand), width / 2f, y, width * 0.94f, 13f, cHint)
                else -> label(c, context.getString(R.string.op_hint), width / 2f, y, width * 0.94f, 13f, cHint)
            }
        }
    }

    private fun errorText(e: Editor.Err) = when (e) {
        Editor.Err.NOT_NUMERIC -> R.string.err_numeric
        Editor.Err.SIZE -> R.string.err_size
        Editor.Err.SQUARE -> R.string.err_square
        Editor.Err.SINGULAR -> R.string.err_singular
        Editor.Err.EXPONENT -> R.string.err_exponent
        Editor.Err.NOT_DIAGONALIZABLE -> R.string.err_not_diag
        Editor.Err.IRRATIONAL -> R.string.err_irrational
    }

    /** Parentesi disegnata a mano, alta quanto la matrice. */
    private fun drawBracket(c: Canvas, type: Int, x: Float, top: Float, bottom: Float, isLeft: Boolean) {
        val s = if (isLeft) 1f else -1f
        val arm = dp(7f) * s
        val path = android.graphics.Path()
        when (type) {
            0 -> { path.moveTo(x + arm, top); path.quadTo(x - arm, (top + bottom) / 2, x + arm, bottom) }
            1 -> { path.moveTo(x + arm, top); path.lineTo(x, top); path.lineTo(x, bottom); path.lineTo(x + arm, bottom) }
            2 -> { path.moveTo(x, top); path.lineTo(x, bottom) }
            else -> {
                if (!isLeft) return
                val m = (top + bottom) / 2
                path.moveTo(x + arm, top)
                path.quadTo(x, top, x, top + dp(8f)); path.lineTo(x, m - dp(6f))
                path.quadTo(x, m, x - arm, m)
                path.quadTo(x, m, x, m + dp(6f)); path.lineTo(x, bottom - dp(8f))
                path.quadTo(x, bottom, x + arm, bottom)
            }
        }
        c.drawPath(path, stroke)
    }

    // ------------------------------------------------------------------ disegno

    override fun onDraw(c: Canvas) {
        c.drawColor(cBg)
        editor?.let { drawPanel(c, it) }
        for (k in keys) drawKey(c, k)
        if (page == Pages.RECENTS && keys.none { it.t == T.SYM }) {
            label(c, context.getString(R.string.recents_empty), width / 2f, panelH() + tabH + rowH * 2, width * 0.9f, 15f, cHint)
        }
        val a = active
        val p = popup
        if (p != null) drawPopup(c, p)
        else if (a != null && a.t == T.SYM) drawPreview(c, a)
    }

    private fun drawKey(c: Canvas, k: K) {
        when (k.t) {
            T.TAB -> {
                val sel = k.tab == page
                label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.85f, 16f, if (sel) cAccent else cHint)
                if (sel) {
                    fill.color = cAccent
                    val hw = k.r.width() * 0.3f
                    c.drawRoundRect(k.r.centerX() - hw, k.r.bottom - dp(3f), k.r.centerX() + hw, k.r.bottom, dp(2f), dp(2f), fill)
                }
            }
            T.TEX -> {
                val r = RectF(k.r).apply { inset(dp(4f), dp(8f)) }
                if (tex) {
                    fill.color = cAccent
                    c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
                } else {
                    stroke.color = cHint
                    c.drawRoundRect(r, r.height() / 2, r.height() / 2, stroke)
                }
                label(c, "TeX", r.centerX(), r.centerY(), r.width() * 0.8f, 13f, if (tex) cOnAccent else cHint, bold = true)
            }
            T.GRID -> drawGrid(c, k)
            T.P_CANCEL -> label(c, "✕", k.r.centerX(), k.r.centerY(), k.r.width(), 20f,
                if (k === active) cAccent else cHint)
            T.P_OK -> {
                fill.color = if (k === active) cPressed else cAccent
                c.drawRoundRect(k.r, k.r.height() / 2, k.r.height() / 2, fill)
                label(c, "✓ " + context.getString(R.string.editor_insert), k.r.centerX(), k.r.centerY(),
                    k.r.width() * 0.85f, 14f, cOnAccent, bold = true)
            }
            T.P_CELL -> {
                val e = editor ?: return
                val gi = k.tab / 1000
                val ci = k.tab % 1000
                val grid = e.grids.getOrNull(gi) ?: return
                val cur = gi == e.g && ci == e.cur
                fill.color = cKey
                c.drawRoundRect(k.r, dp(6f), dp(6f), fill)
                if (cur) {
                    stroke.color = cAccent
                    stroke.strokeWidth = dp(2f)
                    c.drawRoundRect(k.r, dp(6f), dp(6f), stroke)
                    stroke.strokeWidth = dp(1.5f)
                }
                val t = grid.text(ci)
                val size = if (k.r.height() < dp(30f)) 13f else 17f
                when {
                    t.isNotEmpty() -> label(c, t, k.r.centerX(), k.r.centerY(), k.r.width() * 0.9f, size, cText)
                    grid.isScalar -> label(c, grid.name, k.r.centerX(), k.r.centerY(), k.r.width(), size, cHint)
                    cur -> label(c, "│", k.r.centerX(), k.r.centerY(), k.r.width(), size, cAccent)
                }
            }
            T.P_ACT -> if (k.act == "eq") {
                fill.color = if (k === active) cPressed else cAccent
                c.drawRoundRect(k.r, dp(14f), dp(14f), fill)
                label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.85f, 22f,
                    if (k === active) cText else cOnAccent, bold = true)
            } else {
                val e = editor
                val on = e != null && ((k.act == "eq" && e.op != null) || k.act == "op:${e.op?.name}")
                fill.color = when {
                    k === active -> cPressed
                    on -> cAccent
                    else -> cKey
                }
                c.drawRoundRect(k.r, dp(8f), dp(8f), fill)
                val big = k.label.length == 1
                label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.85f, if (big) 19f else 14f,
                    if (on && k !== active) cOnAccent else cText, bold = big)
            }
            else -> {
                val pressed = k === active && popup == null && k.t != T.SPACE || (k === active && k.t == T.SPACE && !spaceMoved)
                fill.color = cShadow
                c.drawRoundRect(k.r.left, k.r.top + dp(1f), k.r.right, k.r.bottom + dp(1f), radius, radius, fill)
                val chosen = k.t == T.BRACKET && k.tab == bracket
                fill.color = when {
                    pressed -> cPressed
                    k.t == T.ENTER || chosen -> cAccent
                    k.t == T.SYM || k.t == T.SPACE -> cKey
                    else -> cSpecial
                }
                c.drawRoundRect(k.r, radius, radius, fill)

                when (k.t) {
                    T.SYM -> {
                        val size = if (k.label.length <= 2) 22f else 15f
                        label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.82f, size, cText)
                        if (k.alts.isNotEmpty()) {
                            label(c, k.alts[0], k.r.right - dp(8f), k.r.top + dp(9f), k.r.width() * 0.35f, 10f, cHint)
                        }
                    }
                    T.SPACE -> label(c, if (tex) "LaTeX" else "Unicode", k.r.centerX(), k.r.centerY(), k.r.width(), 12f, cHint)
                    T.ENTER -> label(c, when { editor?.op != null -> "="; editor != null -> "✓"; else -> enterLabel }, k.r.centerX(), k.r.centerY(), k.r.width() * 0.8f, 20f,
                        if (pressed) cText else cOnAccent)
                    T.BRACKET -> label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.7f, 18f,
                        if (chosen && !pressed) cOnAccent else cText)
                    T.ABC -> label(c, "ABC", k.r.centerX(), k.r.centerY(), k.r.width() * 0.8f, 14f, cText, bold = true)
                    else -> label(c, k.label, k.r.centerX(), k.r.centerY(), k.r.width() * 0.8f, 22f, cText)
                }
            }
        }
    }

    private fun gridCell(r: RectF): Pair<Float, RectF> {
        val labelH = dp(26f)
        val size = minOf(r.width(), r.height() - labelH) - dp(8f)
        val cell = size / GRID_MAX
        val left = r.centerX() - size / 2
        val top = r.top + labelH
        return cell to RectF(left, top, left + size, top + size)
    }

    private fun drawGrid(c: Canvas, k: K) {
        fill.color = cSpecial
        c.drawRoundRect(k.r, radius, radius, fill)
        val (cell, area) = gridCell(k.r)
        val active = k === this.active
        val caption = if (active) "$gridR × $gridC" else context.getString(R.string.matrix_hint)
        label(c, caption, k.r.centerX(), k.r.top + dp(14f), k.r.width() * 0.9f, if (active) 16f else 12f,
            if (active) cAccent else cHint, bold = active)
        for (i in 0 until GRID_MAX) for (j in 0 until GRID_MAX) {
            val on = active && i < gridR && j < gridC
            fill.color = if (on) cAccent else cKey
            val x = area.left + j * cell
            val y = area.top + i * cell
            c.drawRoundRect(x + dp(2f), y + dp(2f), x + cell - dp(2f), y + cell - dp(2f), dp(4f), dp(4f), fill)
        }
    }

    private fun updateGrid(k: K, x: Float, y: Float) {
        val (cell, area) = gridCell(k.r)
        val r = ((y - area.top) / cell).toInt().coerceIn(0, GRID_MAX - 1) + 1
        val c = ((x - area.left) / cell).toInt().coerceIn(0, GRID_MAX - 1) + 1
        if (r != gridR || c != gridC) {
            gridR = r
            gridC = c
            haptic()
            invalidate()
        }
    }

    private fun drawPreview(c: Canvas, k: K) {
        val w = max(k.r.width() * 1.3f, dp(46f))
        val hh = rowH * 1.05f
        val left = (k.r.centerX() - w / 2).coerceIn(dp(2f), width - w - dp(2f))
        val top = max(0f, k.r.top - hh + dp(6f))
        val r = RectF(left, top, left + w, top + hh)
        fill.color = cShadow
        c.drawRoundRect(r.left, r.top + dp(2f), r.right, r.bottom + dp(2f), radius, radius, fill)
        fill.color = cPopup
        c.drawRoundRect(r, radius, radius, fill)
        label(c, k.label, r.centerX(), r.centerY(), r.width() * 0.85f, 30f, cText)
    }

    private fun drawPopup(c: Canvas, p: Popup) {
        fill.color = cShadow
        c.drawRoundRect(p.bg.left, p.bg.top + dp(2f), p.bg.right, p.bg.bottom + dp(2f), radius, radius, fill)
        fill.color = cPopup
        c.drawRoundRect(p.bg, radius, radius, fill)
        p.cells.forEachIndexed { i, r ->
            val sel = i == p.sel
            if (sel) {
                fill.color = cAccent
                c.drawRoundRect(r, radius, radius, fill)
            }
            val size = if (p.choices[i].length <= 2) 24f else 15f
            label(c, p.choices[i], r.centerX(), r.centerY(), r.width() * 0.85f, size, if (sel) cOnAccent else cText)
        }
    }

    private fun label(c: Canvas, s: String, cx: Float, cy: Float, maxW: Float, sizeDp: Float, color: Int, bold: Boolean = false) {
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        text.textSize = dp(sizeDp)
        val w = text.measureText(s)
        if (w > maxW && w > 0) text.textSize *= maxW / w
        text.color = color
        val fm = text.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, text)
    }

    // ------------------------------------------------------------------ tocco

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (popup != null) return true
                // digitazione veloce a due pollici: il tasto precedente viene confermato subito
                active?.let { release(it) }
                val i = e.actionIndex
                val k = keyAt(e.getX(i), e.getY(i)) ?: return true
                press(k, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activeId)
                val k = active
                if (i < 0 || k == null) return true
                val x = e.getX(i)
                val y = e.getY(i)
                val p = popup
                when {
                    p != null -> {
                        val cw = p.cells[0].width()
                        val sel = ((x - p.bg.left) / cw).toInt().coerceIn(0, p.choices.lastIndex)
                        if (sel != p.sel) { p.sel = sel; haptic(); invalidate() }
                    }
                    k.t == T.GRID -> updateGrid(k, x, y)
                    k.t == T.SPACE -> {
                        val step = dp(12f)
                        while (abs(x - spaceLastX) >= step) {
                            val dir = if (x > spaceLastX) 1 else -1
                            l.onCursor(dir)
                            spaceLastX += dir * step
                            if (!spaceMoved) { spaceMoved = true; invalidate() }
                        }
                    }
                    k.t == T.SYM && !longFired -> {
                        val nk = keyAt(x, y)
                        if (nk != null && nk !== k && nk.t == T.SYM) {
                            h.removeCallbacks(longPress)
                            active = nk
                            if (nk.alts.isNotEmpty()) h.postDelayed(longPress, LONG_MS)
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activeId) active?.let { release(it) }
            }
            MotionEvent.ACTION_CANCEL -> cancelActive()
        }
        return true
    }

    private fun press(k: K, pointerId: Int, x: Float, y: Float) {
        active = k
        activeId = pointerId
        longFired = false
        haptic()
        when (k.t) {
            T.SYM -> if (k.alts.isNotEmpty()) h.postDelayed(longPress, LONG_MS)
            T.BACK, T.LEFT, T.RIGHT -> { fireRepeatable(k); h.postDelayed(repeater, 400) }
            T.SPACE -> { spaceLastX = x; spaceMoved = false }
            T.ABC -> h.postDelayed(longPress, 500)
            T.GRID -> { gridR = 0; gridC = 0; updateGrid(k, x, y) }
            else -> {}
        }
        invalidate()
    }

    private fun release(k: K) {
        val p = popup
        val wasLong = longFired
        cancelActive()
        when (k.t) {
            T.SYM -> l.onSymbol(if (p != null) p.choices[p.sel] else k.label)
            T.SPACE -> if (!spaceMoved) l.onSpace()
            T.ENTER -> l.onEnter()
            T.ABC -> if (!wasLong) l.onSwitchIme()
            T.TAB -> if (k.tab != page) { page = k.tab; l.onPageChanged(page) }
            T.TEX -> { tex = !tex; l.onTexToggled(tex) }
            T.GRID -> l.onMatrix(gridR, gridC, bracket)
            T.BRACKET -> { bracket = k.tab; l.onBracketChanged(bracket) }
            T.P_CELL -> l.onEditorTap(k.tab)
            T.P_CANCEL -> l.onEditorCancel()
            T.P_ACT -> l.onEditorAction(k.act)
            T.P_OK -> l.onEditorCommit()
            else -> {}
        }
    }

    private fun cancelActive() {
        h.removeCallbacks(longPress)
        h.removeCallbacks(repeater)
        active = null
        activeId = -1
        popup = null
        invalidate()
    }

    private fun fireRepeatable(k: K) = when (k.t) {
        T.BACK -> l.onBackspace()
        T.LEFT -> l.onCursor(-1)
        T.RIGHT -> l.onCursor(1)
        else -> {}
    }

    private fun openPopup(k: K) {
        val choices = listOf(k.label) + k.alts
        val n = choices.size
        val cw = minOf(max(k.r.width(), dp(42f)), (width - dp(8f)) / n)
        val ch = rowH * 0.95f
        val total = cw * n
        // la prima variante cade proprio sopra il dito: basta rilasciare per sceglierla
        val left = (k.r.centerX() - cw * 1.5f).coerceIn(dp(4f), width - total - dp(4f))
        val top = max(0f, k.r.top - ch - dp(4f))
        val bg = RectF(left - dp(3f), top - dp(3f), left + total + dp(3f), top + ch + dp(3f))
        val cells = List(n) { i -> RectF(left + i * cw, top, left + (i + 1) * cw, top + ch).apply { inset(dp(1.5f), dp(1.5f)) } }
        popup = Popup(choices, cells, bg).apply { sel = 1 }
        invalidate()
    }

    /** Il tasto più vicino al punto: gli spazi tra i tasti non sono "zone morte". */
    private fun keyAt(x: Float, y: Float): K? {
        var best: K? = null
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            val dx = max(max(k.r.left - x, 0f), x - k.r.right)
            val dy = max(max(k.r.top - y, 0f), y - k.r.bottom)
            val dist = dx * dx + dy * dy
            if (dist < bestD) { bestD = dist; best = k }
        }
        return best
    }

    private fun haptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        h.removeCallbacksAndMessages(null)
    }

    private companion object {
        const val LONG_MS = 300L
        const val GRID_MAX = 5
    }
}
