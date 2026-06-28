package com.lollipop.mediaflow.video

import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.ui.PlayerView
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.PlaybackMode
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.Preferences

@OptIn(UnstableApi::class)
class VideoManager(
    private val activity: AppCompatActivity
) : VideoController {

    companion object {

        private val LOG by lazy {
            registerLog()
        }

        fun findTrack(tracks: Tracks): VideoTrackGroup {
            val result = mutableListOf<VideoTrack>()
            var enable = false
            try {
                for (trackGroup in tracks.groups) {
                    // 筛选出字幕轨道
                    if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until trackGroup.length) {
                            val format = trackGroup.getTrackFormat(i)
                            val isSelected = trackGroup.isTrackSelected(i)
                            result.add(
                                VideoTrack(
                                    group = trackGroup.mediaTrackGroup,
                                    index = i,
                                    label = format.label ?: "", // 字幕名称，如 "中文"、"English"
                                    language = format.language ?: "",// 语言代码，如 "zh"、"en"
                                    isSelected = isSelected // 是否正在播放
                                )
                            )
                            enable = enable || isSelected
                        }
                    }
                }
            } catch (e: Throwable) {
                LOG.e("findTrack", e)
            }
            return VideoTrackGroup(
                enable = enable,
                tracks = result
            )
        }
    }

    private val log = registerLog()

    private val exoPlayer: ExoPlayer
    private val videoPreload: VideoPreload

    val eventObserver = VideoEventObserver(::fetchCurrentProgress)

    var currentIndex = -1
        private set

    private var playbackSpeed = 2F

    private var defaultVideoSpeed = 1F

    private var currentLifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED


    private val lifecycleObserver = LifecycleEventObserver { source, event ->
        currentLifecycleState = source.lifecycle.currentState
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate()
            Lifecycle.Event.ON_START -> onStart()
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_DESTROY -> onDestroy()
            else -> {}
        }
    }

    init {
        val preloadStatusControl = VideoPreloadStatusControl(::currentPlayingIndex)
        val preloadBuilder = DefaultPreloadManager.Builder(activity, preloadStatusControl)
        exoPlayer = preloadBuilder.buildExoPlayer()
        videoPreload = VideoPreload(preloadBuilder.build())
        activity.lifecycle.addObserver(lifecycleObserver)
        playbackSpeed = Preferences.playbackSpeed.get()
        defaultVideoSpeed = Preferences.defaultVideoSpeed.get()
    }

    private fun fetchCurrentProgress(): Long {
        try {
            if (exoPlayer.isReleased) {
                return 0
            }
            if (exoPlayer.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
                return exoPlayer.currentPosition
            }
        } catch (e: Throwable) {
            log.e("fetchCurrentProgress", e)
        }
        return 0
    }

    private fun currentPlayingIndex(): Int {
        return currentIndex
    }

    private var mediaListSize = 0

    fun resetMediaList(mediaList: List<MediaInfo.File>, startIndex: Int = 0) {
        log.i("resetMediaList: ${mediaList.size}, $startIndex")
        mediaListSize = mediaList.size
        videoPreload.reset(mediaList, startIndex)
    }

    fun play(index: Int, videoProgress: Long = 0L, autoPlay: Boolean = true) {
        log.i("play: $index, autoPlay=$autoPlay")
        videoPreload.setCurrentIndex(index)
        val source = videoPreload.getSource(index) ?: return
        currentIndex = index
        // 如果当前已经在播放同一个视频，且播放器处于可用状态，则避免重新 setMediaSource，
        // 防止画面重置或闪烁；只需调整进度并继续播放。
        val currentMediaItem = try { exoPlayer.currentMediaItem } catch (e: Throwable) { null }
        val isSameSource = currentMediaItem?.localConfiguration?.uri == source.mediaItem.localConfiguration?.uri
                && exoPlayer.playbackState != Player.STATE_IDLE
                && exoPlayer.playbackState != Player.STATE_ENDED
        if (isSameSource) {
            if (videoProgress > 0L) {
                exoPlayer.seekTo(videoProgress)
            }
            if (autoPlay) {
                play()
            }
            return
        }
        exoPlayer.setMediaSource(source, false)
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        // 明确 seek 到目标位置；传入 0 时也要重置到开头，避免 setMediaSource(false)
        // 保留上个视频的播放位置导致切换后继续播放。
        exoPlayer.seekTo(videoProgress)
        // 注意：如果之前已经 prepare 过了，且播放器没出错
        // 再次调用 setMediaSource 后，播放器会自动进入准备状态
        // 只有在 IDLE 或 ERROR 状态下才需要重新 prepare()
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        if (autoPlay) {
            play()
        }
    }

    override fun isPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    override fun play() {
        log.i("play")
        exoPlayer.play()
        exoPlayer.playbackParameters = PlaybackParameters(defaultVideoSpeed)
    }

    /**
     * 无缝切换：仅更新当前播放索引与预加载状态，不重新设置 MediaSource 或 seek。
     */
    fun onSeamlessSwitchTo(index: Int) {
        log.i("onSeamlessSwitchTo: $index")
        videoPreload.setCurrentIndex(index)
        currentIndex = index
    }

    override fun startPlaybackSpeed() {
        val params = PlaybackParameters(playbackSpeed) // 2.0倍速
        exoPlayer.playbackParameters = params
    }

    override fun stopPlaybackSpeed() {
        val params = PlaybackParameters(defaultVideoSpeed)
        exoPlayer.playbackParameters = params
    }

    private var touchSeekStartPosition = 0L
    // 记录进入 seek 模式前的播放状态，退出时恢复
    private var wasPlayingBeforeSeek = false
    // 记录 onPause/onStop 前的播放状态，用于恢复
    private var wasPlayingBeforePause = false

    override fun startSeekMode() {
        // 记录进入 seek 前的播放状态
        wasPlayingBeforeSeek = try {
            exoPlayer.playWhenReady
        } catch (e: Throwable) {
            false
        }
        touchSeekStartPosition = try {
            if (exoPlayer.isReleased) 0L else exoPlayer.currentPosition
        } catch (e: Throwable) {
            log.e("startSeekMode", e)
            0L
        }
        try {
            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            exoPlayer.pause()
        } catch (e: Throwable) {
            log.e("startSeekMode", e)
        }
    }

    override fun onTouchSeek(weight: Float, precision: Float) {
        seekWithDelta(weight)
    }

    override fun stopSeekMode(weight: Float) {
        try {
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
            // 只在进入 seek 前是播放状态时才恢复播放
            if (wasPlayingBeforeSeek) {
                exoPlayer.play()
            }
        } catch (e: Throwable) {
            log.e("stopSeekMode", e)
        }
    }

    private fun seekWithDelta(weight: Float) {
        val duration = exoPlayer.duration
        // 检查 duration 有效性，防止 TIME_UNSET (-9223372036854775807L) 导致计算错误
        if (duration == C.TIME_UNSET || duration <= 0) {
            return
        }
        // 计算目标位置：起始位置 + (权重 * 总时长)
        val targetPosition = touchSeekStartPosition + (weight * duration).toLong()
        // 限制在 [0, duration] 范围内
        val coercedPosition = targetPosition.coerceIn(0, duration)
        // 快速预览寻道
        exoPlayer.seekTo(coercedPosition)
        eventObserver.notifyProgressUpdate()
    }

    override fun seekTo(ms: Long) {
        log.i("seekTo: $ms")
        exoPlayer.seekTo(ms)
    }

    override fun pause() {
        log.i("pause")
        exoPlayer.pause()
    }

    fun onPlaybackModeChanged(mode: PlaybackMode) {
        log.i("onPlaybackModeChanged: $mode")
    }

    private fun onCreate() {
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer.addListener(eventObserver.playerListener)
        exoPlayer.playWhenReady = true
    }

    fun changeView(oldView: PlayerView?, newView: PlayerView) {
        oldView?.player = null
        newView.player = exoPlayer
    }

    private fun onStart() {
        var index = currentIndex
        if (index < 0) {
            index = 0
        }
        play(index)
        // 根据之前暂停时的状态决定是否恢复播放
        if (!wasPlayingBeforePause) {
            pause()
        }
    }

    private fun onResume() {
    }

    private fun onPause() {
        if (!PIPHelper.isInPictureInPictureMode(activity)) {
            // 记录暂停前的播放状态
            wasPlayingBeforePause = try {
                exoPlayer.playWhenReady
            } catch (e: Throwable) {
                false
            }
            pause()
        }
    }

    private fun onStop() {
        if (!PIPHelper.isInPictureInPictureMode(activity)) {
            // 记录停止前的播放状态
            wasPlayingBeforePause = try {
                exoPlayer.playWhenReady
            } catch (e: Throwable) {
                false
            }
            if (isPlaying()) {
                pause()
            }
        }
    }

    private fun onDestroy() {
        exoPlayer.release()
    }

    fun findTrack(): VideoTrackGroup {
        return findTrack(exoPlayer.currentTracks)
    }

    override fun selectTrack(track: VideoTrack?) {
        if (track == null) {
            updateTrack {
                it.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            return
        }
        updateTrack {
            it.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            it.setOverrideForType(
                TrackSelectionOverride(track.group, track.index)
            )
        }
    }

    override fun previous() {
        if (mediaListSize <= 0) return
        val newIndex = if (currentIndex > 0) currentIndex - 1 else mediaListSize - 1
        play(newIndex)
    }

    override fun next() {
        if (mediaListSize <= 0) return
        val newIndex = if (currentIndex < mediaListSize - 1) currentIndex + 1 else 0
        play(newIndex)
    }

    private fun updateTrack(block: (TrackSelectionParameters.Builder) -> Unit) {
        try {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            block(builder)
            exoPlayer.trackSelectionParameters = builder.build()
        } catch (e: Throwable) {
            log.e("updateTrack", e)
        }
    }

}