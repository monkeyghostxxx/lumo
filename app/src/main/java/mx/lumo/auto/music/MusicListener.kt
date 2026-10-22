package mx.lumo.auto.music

import android.service.notification.NotificationListenerService

/**
 * No lee ni guarda notificaciones. Android exige este permiso para
 * que una app pueda ver "qué se está reproduciendo" (Spotify, YouTube Music…)
 * y controlarlo desde el dock del auto.
 */
class MusicListener : NotificationListenerService()
