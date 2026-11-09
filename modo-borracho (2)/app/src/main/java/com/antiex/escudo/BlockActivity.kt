package com.antiex.escudo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Pantalla que aparece 3-4 segundos cuando intentas abrir un chat bloqueado. */
class BlockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (32 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F1115"))
            setPadding(pad, pad, pad, pad)
        }

        root.addView(line("🍺🚫", 64f, Color.WHITE, false))
        root.addView(line("Ese chat está bloqueado", 26f, Color.WHITE, true))
        root.addView(
            line(
                "Esto lo configuraste tú, sobrio, a propósito.\nMañana me lo agradeces. 😉",
                16f,
                Color.parseColor("#9AA3B2"),
                false
            )
        )

        setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        handler.postDelayed({ finish() }, 3500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun line(s: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
}
