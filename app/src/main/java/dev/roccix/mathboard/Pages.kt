package dev.roccix.mathboard

/**
 * Layout dei simboli. Ogni tasto è una stringa "principale alt1 alt2 ...":
 * il primo simbolo si digita col tocco, gli altri compaiono tenendo premuto
 * (e si scelgono scorrendo il dito, come in Gboard).
 */
class Page(val icon: String, val name: String, val rows: List<List<String>>, val matrix: Boolean = false)

object Pages {
    const val RECENTS = 0
    const val BASE = 1

    val all = listOf(
        Page("🕘", "Recenti", emptyList()),

        // Matematica di base: la scheda più usata, aperta per default e durante la composizione di matrici.
        Page("123", "Base", listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("+ ±", "− - ∓", "× · *", "÷ /", "= ≠ ≈", "( [ {", ") ] }", Editor.FRACTION, "½ ⅓ ¼ ⅔ ¾ ⅕ ⅙ ⅛", ". ,"),
            listOf("x", "y", "z", "n", "k", "i", "j", "^", "_", ","),
            listOf("a", "b", "c", "f", "t", "√ ∛ ∜", "² ³ ⁿ", "< ≤", "> ≥", "% ‰"),
        )),

        Page("∀∃", "Logica", listOf(
            listOf("∀", "∃ ∄ ∃!", "¬ ~", "∧ ⋀", "∨ ⋁", "⊻ ⊕", "⇒ ⟹ → ⇏", "⇔ ⟺ ↔ ⇎"),
            listOf("⊢ ⊬", "⊨ ⊭", "⊤", "⊥", "∴", "∵", "∎ □", "≔ ≝ :⇔"),
            listOf("( [ { ⟨", ") ] } ⟩", "| ‖ ∣ ∤", ":", ";", ",", "… ⋯", "′ ″"),
            listOf("= ≠", "≡ ≢", "∈ ∉", "P", "Q", "x", "y", "n"),
        )),

        Page("∈⊂", "Insiemi", listOf(
            listOf("∈ ∉ ∋ ∌", "⊂ ⊊ ⊄", "⊆ ⊈", "⊃ ⊋ ⊅", "⊇ ⊉", "∪ ⋃ ⊔", "∩ ⋂ ⊓", "∖"),
            listOf("∅", "ℕ ℕ₀ ℕ⁺", "ℤ ℤ⁺ ℤ⁻", "ℚ", "ℝ ℝ⁺ ℝ⁻ ℝⁿ", "ℂ", "ℙ ℍ 𝔽", "× ⨯"),
            listOf("{", "}", "|", ":", "∁ ᶜ", "△ ⊖", "ℵ ℵ₀ ℶ 𝔠", "#"),
            listOf("𝒫", "𝒜", "ℬ", "𝒞", "ℱ", "𝒰", "𝒮", "𝒯"),
        )),

        Page("≤±", "Relazioni e operatori", listOf(
            listOf("= ≠ ≔ ≝", "≈ ≉ ≃", "≡ ≢", "≅ ≆", "∼ ≁ ≍", "∝", "< ≮ ≪", "> ≯ ≫"),
            listOf("≤ ≰ ⩽", "≥ ≱ ⩾", "± ∓", "× ⋅ ∗", "÷ ∕", "· ∘ •", "⊕ ⊖ ⊗ ⊙", "∞"),
            listOf("√ ∛ ∜", "⌊", "⌋", "⌈", "⌉", "| ‖", "⟨", "⟩"),
            listOf("! ‼", "% ‰", "° ′ ″", "∣ ∤", "≺ ≼", "≻ ≽", "⊥ ∥ ∦", "∠ ∡ △"),
        )),

        Page("∫∂", "Analisi", listOf(
            listOf("∑ ∏ ∐", "∫ ∬ ∭ ∮ ∯", "∂", "∇ ∆", "lim limsup liminf", "→ ⟶ ↗ ↘", "↦ ⟼", "∞ +∞ -∞"),
            listOf("d dx dy dt", "′ ″ ‴", "ε ϵ", "δ Δ", "sin sinh arcsin", "cos cosh arccos", "tan tanh arctan", "log ln exp"),
            listOf("max min", "sup inf", "det tr rank", "dim ker Im", "e", "π", "i", "∘"),
        )),

        Page("𝒯", "Topologia", listOf(
            listOf("𝒯 𝒯ₓ", "𝒰 𝔘", "𝒱", "𝒲", "𝒪", "𝒩 𝒩ₓ", "ℬ 𝔅", "𝒮"),
            listOf("𝒜", "𝒞", "ℱ 𝔉", "𝒢", "ℋ", "𝒦", "ℒ", "ℳ"),
            listOf("𝕊 𝕊¹ 𝕊ⁿ", "𝔻 𝔻ⁿ", "𝔹 𝔹ⁿ", "𝕋 𝕋ⁿ", "ℝ ℝⁿ", "ℂ ℂⁿ", "ℍ", "ℙ ℝℙⁿ ℂℙⁿ"),
            listOf(Tex.OVERLINE, "° int", "∂", "∁ ᶜ", "≅ ≃ ≈", "⊔ ∐ ⨆", "∨ ∧", "π₁ Hₙ χ"),
            listOf("cl", "Int", "Fr", "diam", "dist", "↪", "↠", "∼ /"),
        )),

        // Pagina speciale: a sinistra la griglia per scegliere la dimensione, a destra questi tasti (4 colonne).
        Page("▦", "Matrici", listOf(
            listOf("( )", "[ ]", "| |", "{"),
            listOf("⋯", "⋮", "⋱", "ᵀ ⁻¹ †"),
            listOf("det", "tr rank", "dim ker", "I 0"),
            listOf("&", "\\\\", "⊗ ⊕ ∘", Editor.CALC),
        ), matrix = true),

        Page("αβ", "Greco", listOf(
            listOf("α Α", "β Β", "γ Γ", "δ Δ", "ε ϵ Ε", "ζ Ζ", "η Η", "θ ϑ Θ"),
            listOf("ι Ι", "κ ϰ Κ", "λ Λ", "μ Μ", "ν Ν", "ξ Ξ", "ο Ο", "π ϖ Π"),
            listOf("ρ ϱ Ρ", "σ ς Σ", "τ Τ", "υ Υ", "φ ϕ Φ", "χ Χ", "ψ Ψ", "ω Ω"),
            listOf("Γ", "Δ", "Θ", "Λ", "Π", "Σ", "Φ", "Ω"),
        )),

        Page("→⇒", "Frecce", listOf(
            listOf("→ ⟶ ↛", "← ⟵ ↚", "↔ ⟷ ↮", "↦ ⟼", "⇒ ⟹ ⇏", "⇐ ⟸ ⇍", "⇔ ⟺ ⇎", "↑ ⇑"),
            listOf("↓ ⇓", "↕ ⇕", "↗", "↘", "↙", "↖", "↪ ↩", "↠ ↣"),
            listOf("⇀ ⇁", "↼ ↽", "⇄ ⇆", "⇌ ⇋", "⇝ ⤳", "↻ ↺", "⊸", "∘"),
        )),

        Page("x²", "Apici e pedici", listOf(
            listOf("⁰ ₀", "¹ ₁", "² ₂", "³ ₃", "⁴ ₄", "⁵ ₅", "⁶ ₆", "⁷ ₇", "⁸ ₈", "⁹ ₉"),
            listOf("₀ ⁰", "₁ ¹", "₂ ²", "₃ ³", "₄ ⁴", "₅ ⁵", "₆ ⁶", "₇ ⁷", "₈ ⁸", "₉ ⁹"),
            listOf("⁺ ₊", "⁻ ₋", "⁼ ₌", "⁽ ₍", "⁾ ₎", "ⁿ ₙ", "ⁱ ᵢ", "ˣ ₓ", "ʲ ⱼ", "ᵏ ₖ"),
            listOf("^", "_", "ᵀ", "⁻¹", "†", "*", "ᵃ ₐ", "ᵐ ₘ", "ᵗ ₜ", "ᵉ ₑ"),
        )),

    )
}
