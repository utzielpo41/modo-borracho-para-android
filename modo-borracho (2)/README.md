# 🍺 Modo borracho

App Android que, cuando está activa, **bloquea los chats que tú elijas** en las apps de mensajes que tú elijas (por defecto WhatsApp, WhatsApp Business e Instagram) para que el "yo borracho" no pueda escribir a sus ex.

Está pensada para que la configure el "yo sobrio" y el "yo borracho" no pueda deshacerlo:

- Eliges **en qué apps** vigilar y, para cada una, **qué chats** bloquear.
- Con el escudo activo **no se puede quitar ninguna app ni ningún chat de las listas**, ni cambiar el horario ni el modo. (Añadir sí se puede.)
- El modo fiesta (2 / 4 / 8 / 12 h) **no se puede cancelar**, solo esperar a que acabe.
- Hay un **horario automático** (por defecto 22:00–07:00) para que se active solo.
- Cuenta los **intentos frustrados** para enseñárselo al día siguiente.

## Cómo funciona

Usa un servicio de accesibilidad de Android. Solo recibe eventos de las apps que hayas elegido. Cuando abres un chat de una de ellas con el escudo activo, comprueba dos cosas:

1. Que hay una caja de texto en pantalla (estás en una conversación, no en la lista de chats).
2. Que en la cabecera pone **exactamente** el nombre de alguien de la lista **de esa app**.

Si se cumplen las dos, hace lo que hayas elegido en la app (el modo se fija antes de activar el escudo):

- **Dejarme entrar, pero enviar el aviso** (por defecto): puedes abrir el chat, pero todo lo que escribas en la caja se sustituye al instante por el aviso fijo
  *«Esta persona esta en modo borracho no puede enviar ningun mensaje!!!»*. Al pulsar enviar, solo sale ese aviso; tu mensaje nunca llega a enviarse.
  Para no llenar de avisos a la otra persona, después de enviarse uno **no se prepara otro durante 10 minutos** para ese contacto (lo que escribas en ese rato simplemente se borra). Se cambia en `NOTICE_COOLDOWN_MS` (`GuardService.kt`).
- **Sacarme del chat**: te manda a la pantalla de inicio nada más abrirlo.

Todo en local: la app **no tiene permiso de Internet** y no guarda lo que escribes.

## Conseguir el APK sin instalar nada (GitHub)

El proyecto incluye `.github/workflows/build-apk.yml`, que compila el APK gratis en la nube:

1. Crea un repositorio nuevo (puede ser privado) en [github.com](https://github.com) y sube **todo** el contenido de esta carpeta, incluida la carpeta oculta `.github`.
2. Ve a la pestaña **Actions** → ejecución *Build APK* → espera ~5 minutos.
3. Abajo, en **Artifacts**, descarga `modo-borracho-apk` (es un zip con `app-debug.apk` dentro).
4. Pasa el APK al móvil y ábrelo. Android pedirá permitir «instalar apps de origen desconocido» para el navegador o el gestor de archivos que uses.

## Compilar e instalar con Android Studio

Necesitas [Android Studio](https://developer.android.com/studio) (o el SDK de Android + JDK 17).

1. Abre la carpeta `modo-borracho` con Android Studio y espera a que sincronice Gradle.
2. Conecta el móvil (depuración USB activada) y pulsa ▶ **Run**.
   - O genera el APK: `Build → Build APK(s)` (queda en `app/build/outputs/apk/debug/app-debug.apk`) y pásalo al móvil.

## Primer uso en el móvil

1. Abre la app y pulsa **Abrir ajustes de accesibilidad** → *Modo borracho* → activar.
   - Android 13+ con APK instalado a mano: si el interruptor sale en gris, ve a *Ajustes → Apps → Modo borracho → ⋮ → Permitir ajustes restringidos* y repite.
2. En **Apps vigiladas** comprueba que están las que quieres (pulsa **＋ Añadir apps** para sumar más, p. ej. Telegram).
3. En la tarjeta de cada app, añade los nombres **tal cual salen arriba al abrir el chat** (mayúsculas y tildes dan igual).
4. Elige el modo (aviso o sacarme del chat) y activa el horario automático y/o el modo fiesta.
5. **Prueba antes de la fiesta**: activa 2 h, abre el chat de un contacto de prueba y comprueba que funciona.

## Límites (sé honesto con tu amigo)

- Los nombres deben coincidir con lo que muestra el chat. Si el contacto cambia de nombre, hay que actualizarlo. Instagram cambia su interfaz a menudo; si no detecta algún chat, ajusta `GuardService.kt`.
- Pensado y ajustado para WhatsApp e Instagram. En el resto de apps (Telegram, Messenger, Signal, SMS…) la detección es genérica (caja de texto + nombre arriba) y puede no funcionar según cómo dibuje la app su pantalla.
- **Se puede esquivar** si desactiva el servicio de accesibilidad, desinstala la app o cambia la hora del móvil. Conviene que la hora sea automática. Para blindarlo más, se puede añadir un administrador de dispositivo que impida desinstalar.
- **No bloquea responder desde la notificación** ni las apps que no hayas añadido a la lista.
- En el modo aviso solo se controla **la caja de texto**: notas de voz, fotos, stickers y GIFs no pasan por ella y se podrían enviar. Si quieres cerrar eso, usa el modo «Sacarme del chat».
- El aviso lo envía la propia app de chat al pulsar enviar, así que la otra persona lo recibe como un mensaje normal de tu amigo.
- Un chat de grupo puede saltar si el nombre de la persona aparece en la cabecera de la pantalla (p. ej. como remitente del primer mensaje visible).
- El código no se ha probado en un dispositivo real.

## Estructura

```
app/src/main/java/com/antiex/escudo/
  MainActivity.kt   pantalla de configuración (apps, chats, horario, modo)
  GuardService.kt   el servicio de accesibilidad que vigila los chats
  BlockActivity.kt  pantalla de "ese chat está bloqueado"
  Prefs.kt          estado y reglas de bloqueo
  Apps.kt           lista de apps instaladas y apps de chat conocidas
```
