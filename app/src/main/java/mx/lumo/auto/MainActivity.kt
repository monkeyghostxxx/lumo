package mx.lumo.auto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var adapter: VideoAdapter
    private lateinit var permissionCard: View
    private lateinit var helpCard: View
    private lateinit var emptyText: TextView
    private lateinit var nowPlaying: View
    private lateinit var npTitle: TextView
    private lateinit var npTime: TextView
    private lateinit var npButton: ImageButton

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateNowPlaying()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionCard = findViewById(R.id.permission_card)
        helpCard = findViewById(R.id.help_card)
        emptyText = findViewById(R.id.empty)
        nowPlaying = findViewById(R.id.now_playing)
        npTitle = findViewById(R.id.np_title)
        npTime = findViewById(R.id.np_subtitle)
        npButton = findViewById(R.id.np_button)

        adapter = VideoAdapter { list, index -> PlayerHolder.play(this, list, index) }
        findViewById<RecyclerView>(R.id.video_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        findViewById<View>(R.id.btn_permission).setOnClickListener { requestAccess() }
        findViewById<View>(R.id.btn_open).setOnClickListener { openFile() }
        findViewById<View>(R.id.btn_help).setOnClickListener {
            helpCard.visibility = if (helpCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        npButton.setOnClickListener { PlayerHolder.toggle(this) }
    }

    override fun onStart() {
        super.onStart()
        PlayerHolder.get(this).addListener(listener)
        updateNowPlaying()
        loadVideos()
    }

    override fun onStop() {
        PlayerHolder.get(this).removeListener(listener)
        super.onStop()
    }

    private fun requestAccess() {
        val perms = VideoRepository.permissions() + "com.google.android.gms.permission.CAR_SPEED"
        requestPermissions(perms, REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) loadVideos()
    }

    private fun loadVideos() {
        val granted = VideoRepository.hasPermission(this)
        permissionCard.visibility = if (granted) View.GONE else View.VISIBLE
        if (!granted) {
            adapter.submit(emptyList())
            emptyText.visibility = View.GONE
            return
        }
        VideoRepository.loadAsync(this) { list ->
            adapter.submit(list)
            emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @Suppress("DEPRECATION")
    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/*")
        startActivityForResult(intent, REQ_OPEN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_OPEN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        PlayerHolder.play(this, listOf(VideoItem(uri, displayName(uri), 0L)), 0)
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        }
        return "Video"
    }

    private fun updateNowPlaying() {
        val p = PlayerHolder.get(this)
        val item = p.currentMediaItem
        if (item == null) {
            nowPlaying.visibility = View.GONE
            return
        }
        nowPlaying.visibility = View.VISIBLE
        npTitle.text = item.mediaMetadata.title ?: "Video"
        npTime.text = if (PlayerHolder.isActuallyPlaying(this)) "Reproduciendo en el auto" else "En pausa"
        npButton.setImageResource(
            if (PlayerHolder.isActuallyPlaying(this)) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private companion object {
        const val REQ_PERMS = 1
        const val REQ_OPEN = 2
    }
}
