package com.antiex.escudo

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * El vigilante. Android le avisa cuando cambia algo en pantalla en las apps que has elegido.
 *
 * Considera que estás DENTRO de un chat bloqueado cuando se cumplen las dos cosas:
 *   1. Hay un campo de texto editable en pantalla (la caja donde escribes el mensaje).
 *   2. En la cabecera (parte de arriba) aparece EXACTAMENTE el nombre de alguien de la lista
 *      de ESA app.
 *
 * Qué hace entonces depende del modo elegido en la app:
 *   - MODE_KICK:   te manda a la pantalla de inicio.
 *   - MODE_NOTICE: deja que entres, pero TODO lo que escribas en la caja se sustituye al
 *                  instante por el aviso fijo. Así lo único que puede salir al pulsar
 *                  "enviar" es el aviso, nunca tu mensaje.
 */
class GuardService : AccessibilityService() {

    /** Resultado de mirar la pantalla: qué chat bloqueado es y dónde está la caja de texto. */
    private class Scan(val key: String, val input: AccessibilityNodeInfo)

    /** Paquetes de las apps vigiladas (se actualiza solo cuando cambias la lista en la app). */
    private var watched: Set<String> = emptySet()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_APPS) refreshWatched()
    }

    private var lastKick = 0L
    private var lastScan = 0L
    private var lastSet = 0L
    private var lastCount = 0L

    // Estado del aviso en curso
    private var noticePending = false
    private var noticeKey = ""
    private val noticeCooldown = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshWatched()
        Prefs.listen(this, prefListener)
    }

    override fun onDestroy() {
        Prefs.unlisten(this, prefListener)
        super.onDestroy()
    }

    /**
     * Carga la lista de apps y le dice a Android que solo nos mande eventos de ellas
     * (así no gastamos batería ni miramos apps que no has elegido).
     */
    private fun refreshWatched() {
        watched = Prefs.apps(this).toSet()
        val info = serviceInfo ?: return
        val filter = if (watched.isEmpty()) setOf(packageName) else watched
        info.packageNames = filter.toTypedArray()
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in watched) return
        if (!Prefs.isActive(this)) return

        // Los cambios de texto se atienden siempre (hay que ser rápido); el resto, con freno.
        val now = SystemClock.elapsedRealtime()
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && now - lastScan < SCAN_GAP_MS) {
            return
        }
        lastScan = now

        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg !in watched) return

        val names = Prefs.names(this, rootPkg)
            .map { Prefs.normalize(it) }
            .filter { it.isNotEmpty() }
        if (names.isEmpty()) return

        val scan = scanChat(root, rootPkg, names) ?: return

        if (Prefs.mode(this) == Prefs.MODE_KICK) kickOut() else swapForNotice(scan, now)
    }

    override fun onInterrupt() {
        // Nada que limpiar.
    }

    // ---------- Detectar el chat bloqueado ----------

    private fun scanChat(root: AccessibilityNodeInfo, pkg: String, names: List<String>): Scan? {
        val headerLimit = (HEADER_DP * resources.displayMetrics.density).toInt()
        val bounds = Rect()

        // Recorrido en anchura: la cabecera está arriba del árbol, así se llega a ella rápido.
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        var head = 0
        var headerName: String? = null
        var input: AccessibilityNodeInfo? = null
        var inputTop = -1

        while (head < queue.size && head < MAX_NODES) {
            val node = queue[head++]

            if (node.isEditable) {
                // La caja de mensaje es la editable que está más abajo en la pantalla.
                if (node.isVisibleToUser) {
                    node.getBoundsInScreen(bounds)
                    if (bounds.top > inputTop) {
                        inputTop = bounds.top
                        input = node
                    }
                }
            } else if (headerName == null && node.isVisibleToUser) {
                val label = (node.text ?: node.contentDescription)?.toString()
                if (!label.isNullOrBlank() && label.length <= MAX_LABEL) {
                    val norm = Prefs.normalize(label)
                    val exact = names.firstOrNull { it == norm }
                    if (exact != null) {
                        node.getBoundsInScreen(bounds)
                        if (bounds.top <= headerLimit) headerName = exact
                    } else if (node.viewIdResourceName?.endsWith(":id/conversation_contact_name") == true) {
                        // Cabecera de chat de WhatsApp (identificada por su id interno).
                        headerName = names.firstOrNull { norm.contains(it) }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        val name = headerName ?: return null
        val box = input ?: return null
        return Scan(pkg + "|" + name, box)
    }

    // ---------- Modo aviso ----------

    private fun swapForNotice(scan: Scan, now: Long) {
        val notice = getString(R.string.fixed_message)
        val typed = textOf(scan.input)

        if (typed == notice) {
            // El aviso está listo en la caja: si pulsa "enviar", sale esto.
            noticePending = true
            noticeKey = scan.key
            return
        }

        if (typed.isEmpty()) {
            if (noticePending && noticeKey == scan.key) {
                // El aviso ha desaparecido de la caja: se ha enviado (o lo han borrado).
                // Durante un rato no se vuelve a poner, para no llenar de avisos a la otra persona.
                noticePending = false
                noticeCooldown[scan.key] = now + NOTICE_COOLDOWN_MS
            }
            return
        }

        // Hay algo escrito que NO es el aviso: se descarta para que no pueda enviarse.
        if (now - lastSet < SET_GAP_MS) return // freno de seguridad contra bucles
        lastSet = now

        val cooling = now < (noticeCooldown[scan.key] ?: 0L)
        if (cooling) {
            setText(scan.input, "")
        } else {
            setText(scan.input, notice)
            noticePending = true
            noticeKey = scan.key
        }

        // Cuenta un intento y avisa con un toast como mucho una vez cada pocos segundos.
        if (now - lastCount > COUNT_GAP_MS) {
            lastCount = now
            Prefs.addBlock(this)
            Toast.makeText(
                this,
                if (cooling) R.string.cooldown_toast else R.string.swap_toast,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Texto real de la caja (sin confundir el texto de ayuda "Mensaje" con lo escrito). */
    private fun textOf(node: AccessibilityNodeInfo): String {
        if (node.isShowingHintText) return ""
        val t = node.text?.toString() ?: ""
        val hint = node.hintText?.toString()
        return if (t.isNotEmpty() && t == hint) "" else t
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        // Cursor al final, para que no quede escribiendo "antes" del texto.
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
    }

    // ---------- Modo "sacarme del chat" ----------

    private fun kickOut() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKick < COOLDOWN_MS) return
        lastKick = now

        Prefs.addBlock(this)
        performGlobalAction(GLOBAL_ACTION_HOME)

        try {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.blocked_toast, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val HEADER_DP = 96      // "cabecera" = los primeros 96 dp de la pantalla
        private const val MAX_NODES = 600     // tope de seguridad al recorrer la pantalla
        private const val MAX_LABEL = 80      // ignora textos largos (mensajes)
        private const val COOLDOWN_MS = 1000L // modo "sacarme": evita expulsar varias veces seguidas
        private const val SCAN_GAP_MS = 150L  // freno entre escaneos de pantalla
        private const val SET_GAP_MS = 50L    // freno entre sustituciones de texto
        private const val COUNT_GAP_MS = 5000L

        /** Tras enviarse un aviso a una persona, no se vuelve a preparar otro durante este tiempo. */
        private const val NOTICE_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
