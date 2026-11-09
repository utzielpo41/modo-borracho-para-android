package com.antiex.escudo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Una app instalada que se puede vigilar. */
class AppInfo(val pkg: String, val label: String)

object Apps {

    /** Apps de mensajes conocidas: salen primero en el selector. */
    val KNOWN: Map<String, String> = linkedMapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger",
        "org.thoughtcrime.securesms" to "Signal",
        "com.snapchat.android" to "Snapchat",
        "com.discord" to "Discord",
        "com.google.android.apps.messaging" to "Mensajes (Google)",
        "com.samsung.android.messaging" to "Mensajes (Samsung)",
        "com.twitter.android" to "X (Twitter)",
        "com.zhiliaoapp.musically" to "TikTok"
    )

    /** Las que se eligen solas la primera vez, si están instaladas. */
    val DEFAULTS = setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android")

    /** Apps con icono en el menú instaladas en el móvil: primero las de chat conocidas. */
    fun installed(ctx: Context): List<AppInfo> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = ctx.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .filter { it != ctx.packageName }
            .distinct()
            .map { AppInfo(it, label(ctx, it)) }

        val order = KNOWN.keys.toList()
        val known = found.filter { it.pkg in KNOWN }.sortedBy { order.indexOf(it.pkg) }
        val others = found.filter { it.pkg !in KNOWN }.sortedBy { it.label.lowercase() }
        return known + others
    }

    /** Nombre bonito de una app; si no se encuentra, el conocido o el propio paquete. */
    fun label(ctx: Context, pkg: String): String =
        try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            KNOWN[pkg] ?: pkg
        }
}
