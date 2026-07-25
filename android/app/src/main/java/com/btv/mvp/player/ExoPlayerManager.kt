package com.btv.mvp.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerManager {

    private var player: ExoPlayer? = null
    private var progressCallback: ((Long) -> Unit)? = null
    private var isSeeking = false
    private val listeners = mutableListOf<Player.Listener>()

    @OptIn(UnstableApi::class)
    fun init(context: Context) {
        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && !isSeeking) {
                        progressCallback?.invoke(currentPosition)
                    }
                }
            })
            playWhenReady = false
        }
    }

    fun setVideoUrl(url: String, positionMs: Long = 0) {
        player?.let { p ->
            val mediaItem = MediaItem.fromUri(url)
            p.setMediaItem(mediaItem)
            p.prepare()
            if (positionMs > 0) {
                p.seekTo(positionMs)
            }
        }
    }

    fun play() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun seekTo(positionMs: Long) {
        player?.let { p ->
            isSeeking = true
            p.seekTo(positionMs)
            isSeeking = false
        }
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun isPlaying(): Boolean {
        return player?.playWhenReady ?: false
    }

    fun getDuration(): Long {
        return player?.duration?.takeIf { it > 0 } ?: 0L
    }

    fun setProgressListener(callback: (Long) -> Unit) {
        progressCallback = callback
    }

    fun startProgressUpdates() {
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    progressCallback?.invoke(getCurrentPosition())
                }
            }
        })
    }

    fun getPlayer(): ExoPlayer? = player

    fun release() {
        player?.release()
        player = null
    }
}
