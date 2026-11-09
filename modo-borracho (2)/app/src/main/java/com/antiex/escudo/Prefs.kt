package com.antiex.escudo

import android.content.Context
import android.content.SharedPreferences
import java.text.Normalizer
import java.util.Calendar

/**
 * Todo el estado de la app vive aquí (SharedPreferences, solo en el móvil).
 *
 * Regla clave: mientras el escudo está ACTIVO no se pueden quitar apps ni nombres de las listas
 * ni cambiar el horario ni el modo. Así el "yo borracho" no puede desmontar lo que montó el
 * "yo sobrio". Añadir (más protección) sí se permite siempre.
 */
object Prefs {
    private const val FILE = "escudo_prefs"

    /** Clave de la lista de apps vigiladas (el servicio la escucha para actualizar su filtro). */
    const val KEY_APPS = "apps"

    private const val K_NAMES_PREFIX = "names:" // names:<paquete> = chats bloqueados de esa app
    private const val K_MANUAL_UNTIL = "manual_until"
    private const val K_SCHEDULE_ON = "schedule_on"
    private const val K_START_MIN = "start_min"
    private const val K_END_MIN = "end_min"
    private const val K_BLOCKS = "blocks"
    private const val K_MODE = "mode"

    private val ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val SPACES = Regex("\\s+")

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun lines(raw: String?): List<String> =
        (raw ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** Para que el servicio se entere cuando cambia la lista de apps. */
    fun listen(ctx: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp(ctx).registerOnSharedPreferenceChangeListener(l)
    }

    fun unlisten(ctx: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp(ctx).unregisterOnSharedPreferenceChangeListener(l)
    }

    // ---------- Apps vigiladas ----------

    fun appsConfigured(ctx: Context): Boolean = sp(ctx).contains(KEY_APPS)

    /** Lista de paquetes vigilados, p. ej. "com.whatsapp". */
    fun apps(ctx: Context): List<String> = lines(sp(ctx).getString(KEY_APPS, ""))

    /** Primera vez que se abre la app: deja elegidas las apps indicadas. */
    fun initApps(ctx: Context, packages: List<String>) {
        if (appsConfigured(ctx)) return
        sp(ctx).edit().putString(KEY_APPS, packages.joinToString("\n")).apply()
    }

    /** Añadir siempre se permite. */
    fun addApp(ctx: Context, pkg: String): Boolean {
        val clean = pkg.trim()
        val current = apps(ctx)
        if (clean.isEmpty() || clean in current) return false
        sp(ctx).edit().putString(KEY_APPS, (current + clean).joinToString("\n")).apply()
        return true
    }

    /** Quitar solo se permite con el escudo en reposo. */
    fun removeApp(ctx: Context, pkg: String): Boolean {
        if (isActive(ctx)) return false
        val rest = apps(ctx).filter { it != pkg }
        sp(ctx).edit().putString(KEY_APPS, rest.joinToString("\n")).apply()
        return true
    }

    // ---------- Chats bloqueados (una lista por app) ----------

    fun names(ctx: Context, pkg: String): List<String> =
        lines(sp(ctx).getString(K_NAMES_PREFIX + pkg, ""))

    /** Añadir siempre se permite (más protección nunca es un problema). */
    fun addName(ctx: Context, pkg: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val current = names(ctx, pkg)
        if (current.any { normalize(it) == normalize(clean) }) return false
        sp(ctx).edit().putString(K_NAMES_PREFIX + pkg, (current + clean).joinToString("\n")).apply()
        return true
    }

    /** Quitar solo se permite con el escudo en reposo. */
    fun removeName(ctx: Context, pkg: String, name: String): Boolean {
        if (isActive(ctx)) return false
        val rest = names(ctx, pkg).filter { it != name }
        sp(ctx).edit().putString(K_NAMES_PREFIX + pkg, rest.joinToString("\n")).apply()
        return true
    }

    // ---------- Modo fiesta manual (temporizador) ----------

    fun manualUntil(ctx: Context): Long = sp(ctx).getLong(K_MANUAL_UNTIL, 0L)

    fun isManualActive(ctx: Context): Boolean = System.currentTimeMillis() < manualUntil(ctx)

    /** Solo puede alargar el tiempo, nunca acortarlo. */
    fun startManual(ctx: Context, hours: Int) {
        val until = System.currentTimeMillis() + hours * 3_600_000L
        if (until > manualUntil(ctx)) {
            sp(ctx).edit().putLong(K_MANUAL_UNTIL, until).apply()
        }
    }

    // ---------- Horario automático (p. ej. todas las noches de 22:00 a 07:00) ----------

    fun scheduleOn(ctx: Context): Boolean = sp(ctx).getBoolean(K_SCHEDULE_ON, false)

    fun startMin(ctx: Context): Int = sp(ctx).getInt(K_START_MIN, 22 * 60)

    fun endMin(ctx: Context): Int = sp(ctx).getInt(K_END_MIN, 7 * 60)

    fun setScheduleOn(ctx: Context, on: Boolean): Boolean {
        if (isActive(ctx)) return false
        sp(ctx).edit().putBoolean(K_SCHEDULE_ON, on).apply()
        return true
    }

    fun setStartMin(ctx: Context, minutes: Int): Boolean {
        if (isActive(ctx)) return false
        sp(ctx).edit().putInt(K_START_MIN, minutes).apply()
        return true
    }

    fun setEndMin(ctx: Context, minutes: Int): Boolean {
        if (isActive(ctx)) return false
        sp(ctx).edit().putInt(K_END_MIN, minutes).apply()
        return true
    }

    fun isScheduleActive(ctx: Context): Boolean {
        if (!scheduleOn(ctx)) return false
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val s = startMin(ctx)
        val e = endMin(ctx)
        return when {
            s == e -> false
            s < e -> now in s until e
            else -> now >= s || now < e // horario que cruza la medianoche
        }
    }

    fun isActive(ctx: Context): Boolean = isManualActive(ctx) || isScheduleActive(ctx)

    // ---------- Qué hacer al abrir un chat bloqueado ----------

    const val MODE_KICK = 0   // sacar al usuario del chat
    const val MODE_NOTICE = 1 // dejarle entrar, pero cambiar lo que escriba por el aviso fijo

    fun mode(ctx: Context): Int = sp(ctx).getInt(K_MODE, MODE_NOTICE)

    fun setMode(ctx: Context, mode: Int): Boolean {
        if (isActive(ctx)) return false
        sp(ctx).edit().putInt(K_MODE, mode).apply()
        return true
    }

    // ---------- Contador de intentos frustrados ----------

    fun blocks(ctx: Context): Int = sp(ctx).getInt(K_BLOCKS, 0)

    fun addBlock(ctx: Context) {
        sp(ctx).edit().putInt(K_BLOCKS, blocks(ctx) + 1).apply()
    }

    // ---------- Utilidad ----------

    /** Minúsculas, sin tildes y sin espacios repetidos: "José  Pérez" == "jose perez". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase()
            .replace(SPACES, " ")
            .trim()
}
