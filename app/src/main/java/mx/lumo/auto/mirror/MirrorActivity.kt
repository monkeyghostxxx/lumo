package mx.lumo.auto.mirror

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/**
 * Pide permiso para compartir pantalla. En Android 14+ el diálogo deja elegir
 * "Una sola app": elige YouTube y solo eso se ve en el auto.
 */
class MirrorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, MirrorService::class.java)
                    .putExtra(MirrorService.EXTRA_CODE, resultCode)
                    .putExtra(MirrorService.EXTRA_DATA, data)
            )
            // Abre YouTube para que empiece a verse en el auto.
            packageManager.getLaunchIntentForPackage(YOUTUBE)?.let {
                runCatching { startActivity(it) }
            }
            Toast.makeText(this, "Espejo activo en el auto", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private companion object {
        const val REQ = 11
        const val YOUTUBE = "com.google.android.youtube"
    }
}
