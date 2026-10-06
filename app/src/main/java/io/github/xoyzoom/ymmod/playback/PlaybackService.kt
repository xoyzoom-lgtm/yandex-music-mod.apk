package io.github.xoyzoom.ymmod.playback

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import io.github.xoyzoom.ymmod.MainActivity
import io.github.xoyzoom.ymmod.R
import io.github.xoyzoom.ymmod.appContainer
import io.github.xoyzoom.ymmod.player.PlayerCommand
import io.github.xoyzoom.ymmod.player.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground-сервис, который не даёт системе остановить воспроизведение в свёрнутом
 * приложении, и MediaSession для уведомления, экрана блокировки и кнопок гарнитуры.
 * Сам звук играет WebView — сервис только передаёт команды через PlayerCommandBus.
 */
class PlaybackService : Service() {
    private val scope = MainScope()
    private lateinit var session: MediaSessionCompat
    private var lastState: PlayerState = PlayerState.EMPTY
    private var artworkUrl: String? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()

        session = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = send(PlayerCommand.PLAY)
                override fun onPause() = send(PlayerCommand.PAUSE)
                override fun onSkipToNext() = send(PlayerCommand.NEXT)
                override fun onSkipToPrevious() = send(PlayerCommand.PREVIOUS)
            })
            setSessionActivity(contentIntent())
            isActive = true
        }

        // startForeground нужно вызвать в течение нескольких секунд после startForegroundService.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(lastState),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )

        scope.launch {
            appContainer.player.state.collect { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Пользователь смахнул приложение — вместе с WebView пропадает и звук.
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        session.isActive = false
        session.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun send(command: PlayerCommand) = appContainer.commands.send(command)

    private fun render(state: PlayerState) {
        lastState = state
        val track = state.track

        if (track?.artworkUrl != artworkUrl) {
            artworkUrl = track?.artworkUrl
            artwork = null
            loadArtwork(artworkUrl)
        }

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track?.title.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track?.artist.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track?.album.orEmpty())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                .build(),
        )

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    if (state.hasPosition) state.positionMs else PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (state.isPlaying) 1f else 0f,
                    SystemClock.elapsedRealtime() - (System.currentTimeMillis() - state.updatedAtMs),
                )
                .build(),
        )

        notify(buildNotification(state))
    }

    private fun loadArtwork(url: String?) {
        artworkJob?.cancel()
        if (url == null) return
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadBitmap(url) }
            if (bitmap != null && url == artworkUrl) {
                artwork = bitmap
                render(lastState)
            }
        }
    }

    private fun buildNotification(state: PlayerState): Notification {
        val track = state.track
        val playPause = if (state.isPlaying) {
            action(android.R.drawable.ic_media_pause, R.string.notification_action_pause, PlaybackStateCompat.ACTION_PAUSE)
        } else {
            action(android.R.drawable.ic_media_play, R.string.notification_action_play, PlaybackStateCompat.ACTION_PLAY)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist ?: getString(R.string.nothing_playing))
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(state.isPlaying)
            .addAction(action(android.R.drawable.ic_media_previous, R.string.notification_action_previous, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(playPause)
            .addAction(action(android.R.drawable.ic_media_next, R.string.notification_action_next, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(icon: Int, title: Int, mediaAction: Long) = NotificationCompat.Action(
        icon,
        getString(title),
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, mediaAction),
    )

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // Разрешение проверяется явно выше вызова notify.
    @SuppressLint("MissingPermission")
    private fun notify(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_playback))
                .setDescription(getString(R.string.notification_channel_playback_description))
                .setShowBadge(false)
                .build(),
        )
    }

    companion object {
        private const val TAG = "YmModPlayback"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val MAX_ARTWORK_BYTES = 4 * 1024 * 1024

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ запрещает запуск foreground-сервиса из фона.
                Log.w(TAG, "Не удалось запустить сервис воспроизведения", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }

        private fun downloadBitmap(url: String): Bitmap? = try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                if (connection.contentLengthLong > MAX_ARTWORK_BYTES) {
                    null
                } else {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить обложку", e)
            null
        }
    }
}
