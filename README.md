# ∑ MathBoard

A Gboard-style Android keyboard for typing math: logic, set theory, analysis, topology, Greek letters, arrows, super/subscripts, and a matrix editor that can **compute** (λ·A, A·B, det, A⁻¹, rref, eigenvalues, diagonalization…) and insert the result in any text field, as Unicode or LaTeX.

<p align="center"><img src="docs/demo.gif" width="320" alt="MathBoard demo: filling a 4×4 matrix, multiplying it by λ and sending it in a chat"></p>

> [!NOTE]
> **This project was vibe-coded.** I built it by talking to an AI coding assistant ([Claude Code](https://claude.com/claude-code)), describing what I wanted and testing it on my phone. It works and I use it, but I didn't write most of the code by hand and I'm still learning how it works. See [What I still need to learn](#what-i-still-need-to-learn). Issues, advice and pull requests are very welcome.

## Features

- **Symbol pages** (tap for the symbol, hold for variants, like Gboard):
  - 🕘 Recents
  - 123 Basics: digits, operators, fractions, √, powers
  - ∀∃ Logic
  - ∈⊂ Sets
  - ≤± Relations and operators
  - ∫∂ Analysis
  - 𝒯 Topology: 𝒯 𝒰 𝒩 ℬ, 𝕊ⁿ 𝔻ⁿ 𝕋ⁿ ℝℙⁿ, closure A̅, ∂, ≅, ⊔, π₁
  - ▦ Matrices
  - αβ Greek
  - →⇒ Arrows
  - x² Super/subscripts
- **TeX mode**: one toggle and every key types LaTeX instead (`∀` → `\forall`, `ℝⁿ` → `\mathbb{R}^{n}`, `√` → `\sqrt{}` with the cursor inside).
- **Matrix editor and calculator**: pick a size on the grid or with − / + (up to 8×8), fill in the cells with any key, then:
  - operations: λ·A (λ can be a letter), A+B, A−B, A·B, Aⁿ, Aᵀ, A⁻¹, det, rank, tr, rref;
  - characteristic polynomial, eigenvalues (exact: `2 (×2)`, `1 ± √2`, `1 ± 2i`) and diagonalization A = P·D·P⁻¹;
  - exact rational arithmetic (`1/3` stays `1/3`), results can be chained, ✓ inserts the result.
- **Plain-text layout that stays aligned**: matrices and stacked fractions are centered with figure spaces (U+2007, as wide as a digit), so they line up even in proportional fonts like WhatsApp's. The text before the cursor is kept on the right line:
  ```
      a+b
  x = ─────
       c
  ```
- **Ergonomics**: key preview, slide to pick variants, drag on the space bar to move the cursor, repeat on ⌫ and ‹ ›, haptics, light/dark theme.
- **Privacy**: no permissions and no internet access. The release APK is about 100 KB.

## Install

Download the APK from [Releases](../../releases), open it on your phone, then open the **MathBoard** app and follow the two steps: *Enable* and *Switch to MathBoard*.

Requires Android 9 (API 28) or newer. So far it has only been tested on a **Pixel 10a with Android 17**.

## Build

You need JDK 17 and Android SDK Platform 36, or just open the folder in Android Studio.

```sh
export JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/Android/Sdk
./gradlew testReleaseUnitTest   # unit tests: matrix math, eigenvalues, text layout
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

## How it's organised

| File | What it does |
|---|---|
| `MathIME.kt` | The `InputMethodService`: talks to the text field through `InputConnection` |
| `MathKeyboardView.kt` | The whole keyboard, drawn on a `Canvas`: keys, popups, gestures, matrix panel |
| `Pages.kt` | Key layouts. Each key is `"main variant1 variant2 …"` |
| `Tex.kt` | Unicode → LaTeX conversion |
| `Editor.kt` | Matrix/fraction editor: cells, operations, Unicode/LaTeX output |
| `Q.kt`, `MatrixOps.kt`, `Eigen.kt` | Exact fractions, matrix operations, eigenvalues and diagonalization |
| `SetupActivity.kt` | The setup screen (English and Italian) |

## What I still need to learn

This is my to-do list as the author, since the code was written with AI help:

- **UI and design**: improve the keyboard and the setup screen, and study UI/UX design properly (spacing, hierarchy, accessibility, touch-target sizes).
- **Kotlin**: learn the language for real (null safety, data classes, lambdas and inline functions, collections, coroutines) so that I can read and change every file myself.
- **Android architecture**: activities and services, the `InputMethodService` lifecycle, `InputConnection`, window insets and edge-to-edge, resources and localization, Gradle builds and signing.
- **How keyboards work on Android and iOS**: how the system routes input to an IME, what a keyboard can and cannot do, and privacy expectations.
- **iOS port**: study custom keyboard extensions (`UIInputViewController`), their memory and sandbox limits, and whether to share logic between platforms (for example Kotlin Multiplatform for the math core).
- **Testing on more devices**: 3-button navigation, tablets and foldables, landscape, Android 9–14, Samsung/Xiaomi skins, different fonts and display sizes.

## Known limitations

- Unicode matrices and fractions are multi-line plain text. They line up exactly with digits; with letters the alignment is approximate because letters have different widths.
- Matrix operations are numeric only, except λ·A. Diagonalization needs rational eigenvalues. With irrational or complex ones, the eigenvalues are shown but P and D are not.
- No letter layout: **ABC** switches back to your normal keyboard.

## Italiano

Tastiera Android in stile Gboard per scrivere matematica, con un editor di matrici che fa i calcoli e inserisce il risultato in Unicode o LaTeX. **Il progetto è stato vibecodato**, cioè scritto dialogando con un assistente AI. Funziona, ma devo ancora:
- migliorare la UI e studiare il design;
- imparare Kotlin;
- studiare l'architettura di Android e iOS e come funzionano le rispettive tastiere;
- valutare l'integrazione su iOS;
- provarla su più dispositivi.

Suggerimenti e contributi sono benvenuti.

## License

[MIT](LICENSE)
