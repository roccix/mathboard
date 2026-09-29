package dev.roccix.mathboard

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class MathIME : InputMethodService(), MathKeyboardView.Listener {

    private lateinit var prefs: SharedPreferences
    private var kb: MathKeyboardView? = null
    private var editor: Editor? = null
    private val recents = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.getString(KEY_RECENTS, "")!!.split(SEP).filter { it.isNotEmpty() }.forEach { recents.addLast(it) }
    }

    override fun onCreateInputView(): View {
        val v = MathKeyboardView(this, this)
        v.tex = prefs.getBoolean(KEY_TEX, false)
        v.page = prefs.getInt(KEY_PAGE, 1).coerceIn(0, Pages.all.lastIndex)
        v.bracket = prefs.getInt(KEY_BRACKET, 0).coerceIn(0, Editor.BRACKETS.lastIndex)
        v.editor = editor
        kb = v
        return v
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kb?.enterLabel = enterLabelFor(info)
        if (!restarting) setEditor(null)
        if (kb?.page == Pages.RECENTS) kb?.rebuild()
    }

    override fun onEvaluateFullscreenMode() = false

    // ------------------------------------------------------------------ Listener

    override fun onSymbol(s: String) {
        val e = editor
        if (e != null) {
            // durante la composizione i simboli vanno nella casella corrente, non nel campo di testo
            if (s == Tex.OVERLINE) e.type(BAR)
            else if (s != Editor.FRACTION && s != Editor.CALC) e.type(s)
            kb?.invalidate()
            return
        }
        if (s == Editor.FRACTION) {
            setEditor(Editor(Editor.Kind.FRACTION, 2, 1))
            return
        }
        if (s == Tex.OVERLINE) {
            overline()
            return
        }
        if (s == Editor.CALC) {
            onMatrix(2, 2, kb?.bracket ?: 0)
            return
        }
        val out = if (kb?.tex == true) Tex.of(s) else s
        // comandi come \sqrt{}: cursore dentro le prime graffe vuote
        insert(out, out.indexOf("{}").let { if (it >= 0) it + 1 else out.length })
        pushRecent(s)
    }

    /** Chiusura: barra sopra il carattere appena scritto (A → A̅, in LaTeX \\overline{A}). */
    private fun overline() {
        val ic = currentInputConnection ?: return
        if (kb?.tex != true) {
            ic.commitText(BAR, 1)
            return
        }
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val last = if (before.isEmpty()) "" else String(Character.toChars(before.codePointBefore(before.length)))
        if (last.isNotEmpty() && Character.isLetterOrDigit(last.codePointAt(0))) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(last.length, 0)
            ic.commitText("\\overline{$last}", 1)
            ic.endBatchEdit()
        } else {
            insert("\\overline{}", 10)
        }
    }

    override fun onMatrix(rows: Int, cols: Int, bracket: Int) {
        setEditor(Editor(Editor.Kind.MATRIX, rows, cols, bracket))
        // per riempire le caselle servono soprattutto numeri e lettere
        kb?.page = Pages.BASE
    }

    override fun onBracketChanged(bracket: Int) {
        prefs.edit().putInt(KEY_BRACKET, bracket).apply()
    }

    override fun onEditorTap(cell: Int) {
        editor?.select(cell / 1000, cell % 1000)
        kb?.editorChanged()
    }

    override fun onEditorAction(act: String) {
        val e = editor ?: return
        when {
            act == "r-" -> e.resize(-1, 0)
            act == "r+" -> e.resize(1, 0)
            act == "c-" -> e.resize(0, -1)
            act == "c+" -> e.resize(0, 1)
            act == "eq" -> e.evaluate()
            act == "opx" -> e.cancelOp()
            act.startsWith("op:") -> e.start(Editor.Op.valueOf(act.removePrefix("op:")))
        }
        kb?.editorChanged()
    }

    override fun onEditorCancel() = setEditor(null)

    override fun onEditorCommit() {
        val e = editor ?: return
        setEditor(null)
        if (e.kind == Editor.Kind.FRACTION && kb?.tex != true) return insertFraction(e)
        val before = currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
        val out = e.output(kb?.tex == true, before.substringAfterLast('\n'))
        insert(out, out.length)
    }

    /** Frazione su tre righe: riscrive la riga corrente mettendo il testo attorno sulla linea di frazione. */
    private fun insertFraction(e: Editor) {
        val ic = currentInputConnection ?: return
        val prefix = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty().substringAfterLast('\n')
        val suffix = ic.getTextAfterCursor(1000, 0)?.toString().orEmpty().substringBefore('\n')
        val start = selection(ic)?.first?.minus(prefix.length)
        val block = e.stackedFraction(prefix, suffix)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(prefix.length, suffix.length)
        ic.commitText(block.text, 1)
        if (start != null) ic.setSelection(start + block.caret, start + block.caret)
        ic.endBatchEdit()
    }

    private fun setEditor(e: Editor?) {
        editor = e
        kb?.editor = e
    }

    /** Inserisce il testo e mette il cursore in posizione [caret]. */
    private fun insert(text: String, caret: Int) {
        val ic = currentInputConnection ?: return
        if (caret == text.length) {
            ic.commitText(text, 1)
            return
        }
        val start = selection(ic)?.first
        ic.beginBatchEdit()
        ic.commitText(text, 1)
        if (start != null) ic.setSelection(start + caret, start + caret)
        ic.endBatchEdit()
    }

    /** Selezione corrente in posizioni assolute, se l'editor la espone. */
    private fun selection(ic: InputConnection): Pair<Int, Int>? {
        ic.getExtractedText(ExtractedTextRequest(), 0)?.let { et ->
            if (et.selectionStart >= 0) {
                val a = et.startOffset + minOf(et.selectionStart, et.selectionEnd)
                val b = et.startOffset + maxOf(et.selectionStart, et.selectionEnd)
                return a to b
            }
        }
        val before = ic.getTextBeforeCursor(100_000, 0) ?: return null
        val selected = ic.getSelectedText(0)?.length ?: 0
        return before.length to before.length + selected
    }

    override fun onBackspace() {
        editor?.let { it.backspace(); kb?.invalidate(); return }
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onEnter() {
        editor?.let {
            // con un'operazione in attesa il tasto invio fa "=", altrimenti inserisce
            if (it.op != null) { it.evaluate(); kb?.editorChanged() } else onEditorCommit()
            return
        }
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiLine = (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!multiLine && !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onSpace() {
        editor?.let { it.type(" "); kb?.invalidate(); return }
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onCursor(dir: Int) {
        editor?.let { it.move(dir); kb?.editorChanged(); return }
        sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onSwitchIme() {
        if (!switchToPreviousInputMethod()) onImePicker()
    }

    override fun onImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onTexToggled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TEX, enabled).apply()
    }

    override fun onPageChanged(page: Int) {
        prefs.edit().putInt(KEY_PAGE, page).apply()
    }

    override fun recents(): List<String> = recents.toList()

    // ------------------------------------------------------------------

    private fun pushRecent(s: String) {
        recents.remove(s)
        recents.addFirst(s)
        while (recents.size > 32) recents.removeLast()
        prefs.edit().putString(KEY_RECENTS, recents.joinToString(SEP)).apply()
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "➜"
            EditorInfo.IME_ACTION_SEARCH -> "⌕"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "⏎"
        }
    }

    private companion object {
        const val PREFS = "mathboard"
        const val KEY_TEX = "tex"
        const val KEY_PAGE = "page"
        const val KEY_RECENTS = "recents"
        const val KEY_BRACKET = "bracket"
        const val SEP = "\u001F"
        const val BAR = "\u0305"   // barra sopra, combinante
    }
}
