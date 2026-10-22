package mx.lumo.auto

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.concurrent.Executors

data class VideoItem(val uri: Uri, val title: String, val durationMs: Long) {
    fun durationLabel(): String = if (durationMs > 0) PlayerHolder.formatTime(durationMs) else "Video"

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri.toString())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}

object VideoRepository {
    private val io = Executors.newSingleThreadExecutor()

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermission(context: Context): Boolean = permissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun query(context: Context): List<VideoItem> {
        if (!hasPermission(context)) return emptyList()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val out = ArrayList<VideoItem>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = (c.getString(nameCol) ?: "Video").substringBeforeLast('.')
                out += VideoItem(
                    ContentUris.withAppendedId(collection, id),
                    name.ifBlank { "Video" },
                    c.getLong(durCol)
                )
            }
        }
        return out
    }

    fun loadAsync(context: Context, callback: (List<VideoItem>) -> Unit) {
        val app = context.applicationContext
        val main = ContextCompat.getMainExecutor(app)
        io.execute {
            val result = runCatching { query(app) }.getOrDefault(emptyList())
            main.execute { callback(result) }
        }
    }
}
