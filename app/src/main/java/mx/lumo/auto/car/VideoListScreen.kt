package mx.lumo.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import mx.lumo.auto.PlayerHolder
import mx.lumo.auto.VideoItem
import mx.lumo.auto.VideoRepository

class VideoListScreen(carContext: CarContext) : Screen(carContext) {

    private var videos: List<VideoItem>? = null

    init {
        VideoRepository.loadAsync(carContext) {
            videos = it
            invalidate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("Videos")
            .setHeaderAction(Action.BACK)

        val list = videos ?: return builder.setLoading(true).build()

        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrDefault(6)

        val items = ItemList.Builder().setNoItemsMessage(
            if (VideoRepository.hasPermission(carContext)) "No hay videos en el teléfono"
            else "Abre Lumo en tu teléfono y permite el acceso a videos"
        )

        list.take(limit).forEachIndexed { index, video ->
            items.addItem(
                Row.Builder()
                    .setTitle(video.title)
                    .addText(video.durationLabel())
                    .setOnClickListener {
                        PlayerHolder.play(carContext, list, index)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return builder.setSingleList(items.build()).build()
    }
}
