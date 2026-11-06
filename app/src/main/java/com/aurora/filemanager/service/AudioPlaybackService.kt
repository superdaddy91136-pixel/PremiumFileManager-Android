package com.aurora.filemanager.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AudioPlaybackService : MediaSessionService() {
    companion object {
        var sharedPlayer: ExoPlayer? = null
            private set
        var sharedSession: MediaSession? = null
            private set
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        sharedPlayer = player

        mediaSession = MediaSession.Builder(this, player).build()
        sharedSession = mediaSession
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        sharedPlayer?.release()
        sharedPlayer = null
        mediaSession?.release()
        mediaSession = null
        sharedSession = null
        super.onDestroy()
    }
}
