package dev.roccix.mathboard

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class SetupActivity : Activity() {

    private val imm by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // L'app è edge-to-edge: spazio per barre di sistema e tastiera aperta.
        val scroll = findViewById<View>(R.id.scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                b.top to b.bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            v.setPadding(0, top, 0, bottom)
            insets
        }

        // Niente focus iniziale sul campo di prova, altrimenti la pagina si apre già scorsa.
        findViewById<View>(R.id.content).requestFocus()

        findViewById<View>(R.id.step_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<View>(R.id.step_select).setOnClickListener { imm.showInputMethodPicker() }

        val gestures = findViewById<LinearLayout>(R.id.gestures)
        listOf(
            "∈" to R.string.g_tap,
            "∈ › ∉" to R.string.g_hold,
            "‹ ␣ ›" to R.string.g_space,
            "TeX" to R.string.g_tex,
            "▦" to R.string.g_matrix,
            "ABC" to R.string.g_abc,
            "🕘" to R.string.g_recent,
        ).forEach { (chip, text) -> gestures.addView(gestureRow(chip, getString(text))) }

        val version = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.footer).text = getString(R.string.footer, version)
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    // Il selettore delle tastiere è una finestra di sistema: al ritorno del focus ricontrolla lo stato.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshSteps()
    }

    private fun refreshSteps() {
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true

        setStep(R.id.step_enable_badge, R.id.step_enable_sub, "1", enabled,
            R.string.step_enable_done, R.string.step_enable_todo)
        setStep(R.id.step_select_badge, R.id.step_select_sub, "2", current,
            R.string.step_select_done, R.string.step_select_todo)
        findViewById<View>(R.id.step_select).alpha = if (enabled) 1f else 0.5f
    }

    private fun setStep(badgeId: Int, subId: Int, number: String, done: Boolean, doneText: Int, todoText: Int) {
        findViewById<TextView>(badgeId).apply {
            text = if (done) "✓" else number
            setBackgroundResource(if (done) R.drawable.bg_badge_done else R.drawable.bg_badge)
            setTextColor(getColor(if (done) R.color.on_accent else R.color.accent))
        }
        findViewById<TextView>(subId).apply {
            setText(if (done) doneText else todoText)
            setTextColor(getColor(if (done) R.color.success else R.color.muted))
        }
    }

    private fun gestureRow(chip: String, description: String): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            addView(TextView(context).apply {
                text = chip
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.accent))
                setBackgroundResource(R.drawable.bg_chip)
                minWidth = (64 * d).toInt()
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            })
            addView(TextView(context).apply {
                text = description
                textSize = 14f
                setTextColor(getColor(R.color.on_surface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (14 * d).toInt() }
            })
        }
    }
}
