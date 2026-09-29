package dev.roccix.mathboard

/** Converte un simbolo Unicode nel corrispondente comando LaTeX. */
object Tex {
    /** Tasto "barra sopra" (chiusura): si applica al carattere appena scritto. */
    const val OVERLINE = "◌̅"

    private val map = hashMapOf(
        // logica
        "∀" to "\\forall", "∃" to "\\exists", "∄" to "\\nexists", "¬" to "\\neg", "~" to "\\sim",
        "∧" to "\\land", "⋀" to "\\bigwedge", "∨" to "\\lor", "⋁" to "\\bigvee", "⊻" to "\\veebar",
        "⇒" to "\\Rightarrow", "⟹" to "\\implies", "⇏" to "\\nRightarrow",
        "⇔" to "\\Leftrightarrow", "⟺" to "\\iff", "⇎" to "\\nLeftrightarrow",
        "⊢" to "\\vdash", "⊬" to "\\nvdash", "⊨" to "\\models", "⊭" to "\\nvDash",
        "⊤" to "\\top", "⊥" to "\\perp", "∴" to "\\therefore", "∵" to "\\because",
        "∎" to "\\blacksquare", "□" to "\\square", "≔" to "\\coloneqq", "≝" to "\\overset{\\text{def}}{=}",
        ":⇔" to ":\\Leftrightarrow",
        "⟨" to "\\langle", "⟩" to "\\rangle", "{" to "\\{", "}" to "\\}", "‖" to "\\|",
        "∣" to "\\mid", "∤" to "\\nmid", "…" to "\\dots", "⋯" to "\\cdots",
        "′" to "'", "″" to "''", "‴" to "'''",
        // insiemi
        "∈" to "\\in", "∉" to "\\notin", "∋" to "\\ni", "∌" to "\\not\\ni",
        "⊂" to "\\subset", "⊊" to "\\subsetneq", "⊄" to "\\not\\subset", "⊆" to "\\subseteq", "⊈" to "\\nsubseteq",
        "⊃" to "\\supset", "⊋" to "\\supsetneq", "⊅" to "\\not\\supset", "⊇" to "\\supseteq", "⊉" to "\\nsupseteq",
        "∪" to "\\cup", "⋃" to "\\bigcup", "⊔" to "\\sqcup", "∩" to "\\cap", "⋂" to "\\bigcap", "⊓" to "\\sqcap",
        "∖" to "\\setminus", "∅" to "\\emptyset",
        "ℕ" to "\\mathbb{N}", "ℤ" to "\\mathbb{Z}", "ℚ" to "\\mathbb{Q}", "ℝ" to "\\mathbb{R}",
        "ℂ" to "\\mathbb{C}", "ℙ" to "\\mathbb{P}", "ℍ" to "\\mathbb{H}", "𝔽" to "\\mathbb{F}",
        "×" to "\\times", "⨯" to "\\times", "∁" to "\\complement", "△" to "\\triangle", "⊖" to "\\ominus",
        "ℵ" to "\\aleph", "ℶ" to "\\beth", "𝔠" to "\\mathfrak{c}", "#" to "\\#", "%" to "\\%",
        "𝒫" to "\\mathcal{P}", "𝒜" to "\\mathcal{A}", "ℬ" to "\\mathcal{B}", "𝒞" to "\\mathcal{C}",
        "ℱ" to "\\mathcal{F}", "𝒰" to "\\mathcal{U}", "𝒮" to "\\mathcal{S}", "𝒯" to "\\mathcal{T}",
        // relazioni e operatori
        "≠" to "\\neq", "≈" to "\\approx", "≉" to "\\not\\approx", "≃" to "\\simeq", "≡" to "\\equiv",
        "≢" to "\\not\\equiv", "≅" to "\\cong", "≆" to "\\ncong", "∼" to "\\sim", "≁" to "\\nsim",
        "≍" to "\\asymp", "∝" to "\\propto", "≮" to "\\nless", "≪" to "\\ll", "≯" to "\\ngtr", "≫" to "\\gg",
        "≤" to "\\leq", "≰" to "\\nleq", "⩽" to "\\leqslant", "≥" to "\\geq", "≱" to "\\ngeq", "⩾" to "\\geqslant",
        "±" to "\\pm", "∓" to "\\mp", "⋅" to "\\cdot", "·" to "\\cdot", "∗" to "\\ast", "÷" to "\\div",
        "∕" to "/", "−" to "-", "∘" to "\\circ", "•" to "\\bullet", "⊕" to "\\oplus", "⊗" to "\\otimes",
        "⊙" to "\\odot", "∞" to "\\infty", "√" to "\\sqrt{}", "∛" to "\\sqrt[3]{}", "∜" to "\\sqrt[4]{}",
        "⌊" to "\\lfloor", "⌋" to "\\rfloor", "⌈" to "\\lceil", "⌉" to "\\rceil", "‼" to "!!",
        "°" to "^\\circ", "≺" to "\\prec", "≼" to "\\preceq", "≻" to "\\succ", "≽" to "\\succeq",
        "∥" to "\\parallel", "∦" to "\\nparallel", "∠" to "\\angle", "∡" to "\\measuredangle", "‰" to "\\text{‰}",
        // analisi
        "∑" to "\\sum", "∏" to "\\prod", "∐" to "\\coprod", "∫" to "\\int", "∬" to "\\iint",
        "∭" to "\\iiint", "∮" to "\\oint", "∯" to "\\oiint", "∂" to "\\partial", "∇" to "\\nabla", "∆" to "\\Delta",
        // greco
        "α" to "\\alpha", "β" to "\\beta", "γ" to "\\gamma", "δ" to "\\delta", "ε" to "\\varepsilon",
        "ϵ" to "\\epsilon", "ζ" to "\\zeta", "η" to "\\eta", "θ" to "\\theta", "ϑ" to "\\vartheta",
        "ι" to "\\iota", "κ" to "\\kappa", "ϰ" to "\\varkappa", "λ" to "\\lambda", "μ" to "\\mu",
        "ν" to "\\nu", "ξ" to "\\xi", "ο" to "o", "π" to "\\pi", "ϖ" to "\\varpi", "ρ" to "\\rho",
        "ϱ" to "\\varrho", "σ" to "\\sigma", "ς" to "\\varsigma", "τ" to "\\tau", "υ" to "\\upsilon",
        "φ" to "\\varphi", "ϕ" to "\\phi", "χ" to "\\chi", "ψ" to "\\psi", "ω" to "\\omega",
        "Γ" to "\\Gamma", "Δ" to "\\Delta", "Θ" to "\\Theta", "Λ" to "\\Lambda", "Ξ" to "\\Xi",
        "Π" to "\\Pi", "Σ" to "\\Sigma", "Υ" to "\\Upsilon", "Φ" to "\\Phi", "Ψ" to "\\Psi", "Ω" to "\\Omega",
        "Α" to "A", "Β" to "B", "Ε" to "E", "Ζ" to "Z", "Η" to "H", "Ι" to "I", "Κ" to "K",
        "Μ" to "M", "Ν" to "N", "Ο" to "O", "Ρ" to "P", "Τ" to "T", "Χ" to "X",
        // frecce
        "→" to "\\to", "⟶" to "\\longrightarrow", "↛" to "\\nrightarrow", "←" to "\\leftarrow",
        "⟵" to "\\longleftarrow", "↚" to "\\nleftarrow", "↔" to "\\leftrightarrow", "⟷" to "\\longleftrightarrow",
        "↮" to "\\nleftrightarrow", "↦" to "\\mapsto", "⟼" to "\\longmapsto", "⇐" to "\\Leftarrow",
        "⟸" to "\\Longleftarrow", "⇍" to "\\nLeftarrow", "↑" to "\\uparrow", "⇑" to "\\Uparrow",
        "↓" to "\\downarrow", "⇓" to "\\Downarrow", "↕" to "\\updownarrow", "⇕" to "\\Updownarrow",
        "↗" to "\\nearrow", "↘" to "\\searrow", "↙" to "\\swarrow", "↖" to "\\nwarrow",
        "↪" to "\\hookrightarrow", "↩" to "\\hookleftarrow", "↠" to "\\twoheadrightarrow", "↣" to "\\rightarrowtail",
        "⇀" to "\\rightharpoonup", "⇁" to "\\rightharpoondown", "↼" to "\\leftharpoonup", "↽" to "\\leftharpoondown",
        "⇄" to "\\rightleftarrows", "⇆" to "\\leftrightarrows", "⇌" to "\\rightleftharpoons",
        "⇋" to "\\leftrightharpoons", "⇝" to "\\rightsquigarrow", "⤳" to "\\leadsto",
        "↻" to "\\circlearrowright", "↺" to "\\circlearrowleft", "⊸" to "\\multimap",
        "†" to "^\\dagger",
        // topologia
        "𝒱" to "\\mathcal{V}", "𝒲" to "\\mathcal{W}", "𝒪" to "\\mathcal{O}", "𝒩" to "\\mathcal{N}",
        "𝒢" to "\\mathcal{G}", "ℋ" to "\\mathcal{H}", "𝒦" to "\\mathcal{K}", "ℒ" to "\\mathcal{L}",
        "ℳ" to "\\mathcal{M}", "𝔘" to "\\mathfrak{U}", "𝔅" to "\\mathfrak{B}", "𝔉" to "\\mathfrak{F}",
        "𝕊" to "\\mathbb{S}", "𝔻" to "\\mathbb{D}", "𝔹" to "\\mathbb{B}", "𝕋" to "\\mathbb{T}",
        "⨆" to "\\bigsqcup",
                // matrici e frazioni
        "&" to " & ", "\\\\" to " \\\\ ", "⋮" to "\\vdots", "⋱" to "\\ddots", "⁄" to "/",
        "½" to "\\frac{1}{2}", "⅓" to "\\frac{1}{3}", "¼" to "\\frac{1}{4}", "⅔" to "\\frac{2}{3}",
        "¾" to "\\frac{3}{4}", "⅕" to "\\frac{1}{5}", "⅙" to "\\frac{1}{6}", "⅛" to "\\frac{1}{8}",
    )

    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱˣʲᵏᵃᵐᵗᵉᵀᶜ"
    private const val SUP_PLAIN = "0123456789+-=()nixjkamteTc"
    private const val SUB = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₙᵢₓⱼₖₐₘₜₑ"
    private const val SUB_PLAIN = "0123456789+-=()nixjkamte"

    private val funcs = setOf(
        "lim", "limsup", "liminf", "sin", "cos", "tan", "sinh", "cosh", "tanh",
        "arcsin", "arccos", "arctan", "log", "ln", "exp", "max", "min", "sup", "inf",
        "det", "dim", "ker",
    )
    private val opNames = setOf("tr", "rank", "Im", "cl", "int", "Int", "Fr", "diam", "dist")
    private val trailingCmd = Regex("\\\\[A-Za-z]+$")

    fun of(s: String): String {
        map[s]?.let { return spaced(it) }
        if (s in funcs) return "\\$s "
        if (s in opNames) return "\\operatorname{$s}"

        // Conversione carattere per carattere, raggruppando apici/pedici: ℝⁿ → \mathbb{R}^{n}
        val sb = StringBuilder()
        var mode = 0 // 0 = normale, 1 = apice, 2 = pedice
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val c = String(Character.toChars(cp))
            i += c.length
            val sup = SUP.indexOf(c)
            val sub = SUB.indexOf(c)
            val newMode = if (sup >= 0) 1 else if (sub >= 0) 2 else 0
            if (newMode != mode) {
                if (mode != 0) sb.append('}')
                if (newMode == 1) sb.append("^{") else if (newMode == 2) sb.append("_{")
                mode = newMode
            }
            when (mode) {
                1 -> sb.append(SUP_PLAIN[sup])
                2 -> sb.append(SUB_PLAIN[sub])
                else -> {
                    val t = map[c] ?: c
                    if (trailingCmd.containsMatchIn(sb) && t.first().isLetter()) sb.append(' ')
                    sb.append(t)
                }
            }
        }
        if (mode != 0) sb.append('}')
        return spaced(sb.toString())
    }

    /** Aggiunge uno spazio dopo un comando alfabetico finale, così "\in" + "x" non diventa "\inx". */
    private fun spaced(t: String) = if (trailingCmd.containsMatchIn(t)) "$t " else t
}
