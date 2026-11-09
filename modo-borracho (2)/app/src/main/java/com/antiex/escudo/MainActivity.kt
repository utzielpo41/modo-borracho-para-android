package com.antiex.escudo

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantalla de configuración. La interfaz se construye por código para no necesitar librerías. */
class MainActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Primera vez: se eligen solas WhatsApp / WhatsApp Business / Instagram si están instaladas.
        if (!Prefs.appsConfigured(this)) {
            Prefs.initApps(
                this,
                Apps.installed(this).map { it.pkg }.filter { it in Apps.DEFAULTS }
            )
        }
        render()
    }

    private fun render() {
        val ctx = this
        content.removeAllViews()
        val active = Prefs.isActive(ctx)
        val watched = Prefs.apps(ctx)

        content.addView(label("🍺 Modo borracho", 28f, Color.WHITE, true))
        content.addView(
            label(
                "Bloquea chats concretos de las apps de mensajes que elijas mientras estás de fiesta.",
                14f, MUTED, false
            )
        )

        // ---- Estado ----
        card {
            val status = when {
                Prefs.isManualActive(ctx) -> "🟢 ACTIVO hasta las ${clock(Prefs.manualUntil(ctx))}"
                active -> "🟢 ACTIVO (horario nocturno)"
                else -> "⚪ En reposo"
            }
            addView(label(status, 20f, Color.WHITE, true))
            val tries = Prefs.blocks(ctx)
            if (tries > 0) {
                addView(label("Intentos frustrados: $tries 😅", 14f, MUTED, false))
            }
            if (active) {
                addView(
                    label(
                        "Mientras esté activo no se puede quitar nada de las listas ni cambiar el horario o el modo.",
                        13f, MUTED, false
                    )
                )
            }
        }

        // ---- Falta activar la accesibilidad ----
        if (!serviceEnabled()) {
            card {
                addView(label("⚠️ Falta un paso", 18f, WARN, true))
                addView(
                    label(
                        "Activa «Modo borracho» en Accesibilidad para que pueda vigilar los chats. " +
                            "Si sale en gris (Android 13+): Ajustes → Apps → Modo borracho → menú ⋮ → " +
                            "«Permitir ajustes restringidos», y vuelve a intentarlo.",
                        13f, MUTED, false
                    )
                )
                addView(btn("Abrir ajustes de accesibilidad") {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
        }

        // ---- Modo fiesta manual ----
        card {
            addView(label("Modo fiesta", 18f, Color.WHITE, true))
            addView(
                label(
                    "Actívalo antes de salir. Una vez en marcha NO se puede cancelar hasta que acabe el tiempo.",
                    13f, MUTED, false
                )
            )
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (h in listOf(2, 4, 8, 12)) {
                row.addView(
                    btn("$h h") { confirmStart(h) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            addView(row)
        }

        // ---- Horario automático ----
        card {
            addView(label("Horario automático", 18f, Color.WHITE, true))
            addView(
                label(
                    "Se activa solo cada día en esta franja, aunque no te acuerdes de nada.",
                    13f, MUTED, false
                )
            )
            val sw = Switch(ctx).apply {
                text = "Activar horario"
                setTextColor(Color.WHITE)
                isChecked = Prefs.scheduleOn(ctx)
                isEnabled = !active
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setScheduleOn(ctx, checked)
                    render()
                }
            }
            addView(sw)
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(btn("Desde ${hhmm(Prefs.startMin(ctx))}", !active) { pickTime(true) }, lp)
            row.addView(btn("Hasta ${hhmm(Prefs.endMin(ctx))}", !active) { pickTime(false) }, lp)
            addView(row)
        }

        // ---- Qué hacer al abrir un chat bloqueado ----
        card {
            val mode = Prefs.mode(ctx)
            addView(label("Al abrir un chat bloqueado", 18f, Color.WHITE, true))
            addView(
                btn((if (mode == Prefs.MODE_NOTICE) "✔ " else "") + "Dejarme entrar, pero enviar el aviso", !active) {
                    Prefs.setMode(ctx, Prefs.MODE_NOTICE)
                    render()
                }
            )
            addView(
                btn((if (mode == Prefs.MODE_KICK) "✔ " else "") + "Sacarme del chat directamente", !active) {
                    Prefs.setMode(ctx, Prefs.MODE_KICK)
                    render()
                }
            )
            val help = if (mode == Prefs.MODE_NOTICE) {
                "Lo que escribas se cambia al instante por: «" + ctx.getString(R.string.fixed_message) +
                    "». Al darle a enviar solo sale ese aviso (como mucho una vez cada 10 minutos por persona)."
            } else {
                "En cuanto abras el chat te mando a la pantalla de inicio."
            }
            addView(label(help, 13f, MUTED, false))
        }

        // ---- Apps vigiladas ----
        card {
            addView(label("Apps vigiladas", 18f, Color.WHITE, true))
            addView(
                label(
                    "Elige en qué apps de mensajes quieres bloquear chats. Está pensado para WhatsApp e " +
                        "Instagram; en las demás la detección es genérica y puede fallar según cómo dibuje " +
                        "la app su pantalla.",
                    13f, MUTED, false
                )
            )
            if (watched.isEmpty()) {
                addView(label("Todavía no has elegido ninguna app.", 14f, MUTED, false))
            }
            for (p in watched) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    label("📱 " + Apps.label(ctx, p), 16f, Color.WHITE, false),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(btn("✕", !active) {
                    Prefs.removeApp(ctx, p)
                    render()
                })
                addView(row)
            }
            addView(btn("＋ Añadir apps") { chooseApps() })
        }

        // ---- Chats bloqueados: una lista por cada app elegida ----
        for (pkg in watched) {
            val appName = Apps.label(ctx, pkg)
            card {
                addView(label("Chats bloqueados en $appName", 18f, Color.WHITE, true))
                addView(
                    label(
                        "Escribe el nombre TAL CUAL aparece arriba al abrir el chat (mayúsculas y tildes dan " +
                            "igual). Si lo tienes guardado como «Laura 💔», pon exactamente eso.",
                        13f, MUTED, false
                    )
                )
                val names = Prefs.names(ctx, pkg)
                if (names.isEmpty()) {
                    addView(label("Aún no hay nadie en la lista.", 14f, MUTED, false))
                }
                for (n in names) {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(
                        label("🚫 $n", 16f, Color.WHITE, false),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    row.addView(btn("✕", !active) {
                        Prefs.removeName(ctx, pkg, n)
                        render()
                    })
                    addView(row)
                }
                val input = EditText(ctx).apply {
                    hint = "Nombre del contacto"
                    setHintTextColor(MUTED)
                    setTextColor(Color.WHITE)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    setSingleLine()
                }
                addView(input)
                addView(btn("Añadir a la lista") {
                    if (!Prefs.addName(ctx, pkg, input.text.toString())) {
                        Toast.makeText(ctx, "Vacío o ya está en la lista", Toast.LENGTH_SHORT).show()
                    }
                    render()
                })
            }
        }

        // ---- Transparencia ----
        content.addView(
            label(
                "Todo funciona en local. Solo vigila las apps que elijas: compara el nombre del chat abierto " +
                    "con tu lista y, en un chat bloqueado, cambia lo que escribes por el aviso. No guarda lo " +
                    "que escribes y ni siquiera tiene permiso de Internet.",
                12f, MUTED, false
            )
        )
    }

    // ---------- Acciones ----------

    private fun chooseApps() {
        val chosen = Prefs.apps(this).toSet()
        val options = Apps.installed(this).filter { it.pkg !in chosen }
        if (options.isEmpty()) {
            Toast.makeText(this, "No hay más apps que añadir", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = options
            .map { if (it.pkg in Apps.KNOWN) "💬 " + it.label else it.label }
            .toTypedArray()
        val checked = BooleanArray(options.size)

        AlertDialog.Builder(this)
            .setTitle("¿Qué apps quieres vigilar?")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Añadir") { _, _ ->
                for (i in options.indices) {
                    if (checked[i]) Prefs.addApp(this, options[i].pkg)
                }
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmStart(hours: Int) {
        val until = System.currentTimeMillis() + hours * 3_600_000L
        AlertDialog.Builder(this)
            .setTitle("¿Activar $hours h?")
            .setMessage("No podrás desactivarlo ni editar las listas hasta las ${clock(until)}.")
            .setPositiveButton("Activar") { _, _ ->
                Prefs.startManual(this, hours)
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickTime(start: Boolean) {
        val current = if (start) Prefs.startMin(this) else Prefs.endMin(this)
        TimePickerDialog(
            this,
            { _, h, m ->
                val minutes = h * 60 + m
                if (start) Prefs.setStartMin(this, minutes) else Prefs.setEndMin(this, minutes)
                render()
            },
            current / 60, current % 60, true
        ).show()
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, GuardService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true)
        }
    }

    // ---------- Ayudantes de interfaz ----------

    private fun card(block: LinearLayout.() -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(16).toFloat()
            }
        }
        c.block()
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(16)
        content.addView(c, lp)
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun btn(s: String, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    private fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    companion object {
        private val BG = Color.parseColor("#0F1115")
        private val CARD = Color.parseColor("#1A1D24")
        private val MUTED = Color.parseColor("#9AA3B2")
        private val WARN = Color.parseColor("#FFB74D")
    }
}
