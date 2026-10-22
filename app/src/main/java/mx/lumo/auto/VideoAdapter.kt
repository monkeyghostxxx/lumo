package mx.lumo.auto

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class VideoAdapter(
    private val onPick: (List<VideoItem>, Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private var items: List<VideoItem> = emptyList()
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = object : LruCache<Uri, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Uri, value: Bitmap): Int = value.byteCount
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val title: TextView = view.findViewById(R.id.title)
        val meta: TextView = view.findViewById(R.id.meta)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<VideoItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.meta.text = item.durationLabel()
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onPick(items, pos)
        }
        bindThumb(holder, item)
    }

    private fun bindThumb(holder: VH, item: VideoItem) {
        holder.thumb.tag = item.uri
        val cached = thumbs.get(item.uri)
        if (cached != null) {
            holder.thumb.setImageBitmap(cached)
            return
        }
        holder.thumb.setImageDrawable(null)
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = holder.itemView.context.applicationContext.contentResolver
        io.execute {
            val bmp = loadThumb29(resolver, item.uri) ?: return@execute
            thumbs.put(item.uri, bmp)
            main.post { if (holder.thumb.tag == item.uri) holder.thumb.setImageBitmap(bmp) }
        }
    }

    @RequiresApi(29)
    private fun loadThumb29(resolver: ContentResolver, uri: Uri): Bitmap? =
        runCatching { resolver.loadThumbnail(uri, Size(320, 180), null) }.getOrNull()
}
