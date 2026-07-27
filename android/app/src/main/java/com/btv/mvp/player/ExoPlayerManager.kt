package com.btv.mvp.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerManager {

    private var player: ExoPlayer? = null
    var onReady: (() -> Unit)? = null
    var onBuffering: (() -> Unit)? = null
    var isSeeking = false

    @OptIn(UnstableApi::class)
    fun init(context: Context) {
        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            isSeeking = false
                            onReady?.invoke()
                        }
                        Player.STATE_BUFFERING -> {
                            onBuffering?.invoke()
                        }
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
        }
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun isPlaying(): Boolean = player?.playWhenReady ?: false

    fun getDuration(): Long = player?.duration?.takeIf { it > 0 } ?: 0L

    fun getPlaybackState(): Int = player?.playbackState ?: Player.STATE_IDLE

    fun getPlayer(): ExoPlayer? = player

    fun release() {
        player?.release()
        player = null
    }
}
