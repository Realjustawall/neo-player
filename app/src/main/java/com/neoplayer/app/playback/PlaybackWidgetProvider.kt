package com.neoplayer.app.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.neoplayer.app.MainActivity
import com.neoplayer.app.R

/** Lightweight local widget. It talks directly to the existing MediaSession and needs no account. */
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, manager, appWidgetIds)
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> withController(context) { if (it.isPlaying) it.pause() else it.play() }
            ACTION_PREVIOUS -> withController(context) { it.seekToPreviousMediaItem() }
            ACTION_NEXT -> withController(context) { it.seekToNextMediaItem() }
            ACTION_REFRESH -> updateAll(context)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.neoplayer.app.widget.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.neoplayer.app.widget.PREVIOUS"
        const val ACTION_NEXT = "com.neoplayer.app.widget.NEXT"
        const val ACTION_REFRESH = "com.neoplayer.app.widget.REFRESH"

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, PlaybackWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val token = SessionToken(appContext, ComponentName(appContext, NeoPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                ids.forEach { id ->
                    val views = buildViews(appContext, controller)
                    manager.updateAppWidget(id, views)
                }
                runCatching { MediaController.releaseFuture(future) }
            }, ContextCompat.getMainExecutor(appContext))
        }

        private fun buildViews(context: Context, controller: MediaController?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.neo_widget)
            val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { context.getString(R.string.app_name) }
            val artist = controller?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty().ifBlank { "Local music" }
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_subtitle, artist)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (controller?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            views.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, ACTION_PREVIOUS, 11))
            views.setOnClickPendingIntent(R.id.widget_play_pause, broadcast(context, ACTION_PLAY_PAUSE, 12))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 13))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    14,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, PlaybackWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun withController(context: Context, block: (MediaController) -> Unit) {
            val appContext = context.applicationContext
            val token = SessionToken(appContext, ComponentName(appContext, NeoPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                runCatching { future.get() }.getOrNull()?.let(block)
                runCatching { MediaController.releaseFuture(future) }
                updateAll(appContext)
            }, ContextCompat.getMainExecutor(appContext))
        }
    }
}
