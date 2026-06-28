package com.lollipop.mediaflow.page.flow

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.tools.ClickHelper
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.VideoTouchHelper
import com.lollipop.mediaflow.tools.task
import com.lollipop.mediaflow.ui.PipVisibleFilter
import com.lollipop.mediaflow.ui.view.DeconstructSlider
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoTrackGroup
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class VideoPlayHolder(
    private val binding: PageVideoFlowBinding
) : RecyclerView.ViewHolder(binding.root), VideoTouchHelper.VideoController {

    companion object {
        /** 非沉浸模式下无交互自动隐藏控件的延时（毫秒） */
        private const val AUTO_HIDE_DELAY = 15_000L

        /** 随机模式下模糊背景的淡入时长（毫秒） */
        private const val RANDOM_BACKGROUND_FADE_DURATION = 300L

        fun create(layoutInflater: LayoutInflater, parent: ViewGroup? = null): VideoPlayHolder {
            return VideoPlayHolder(
                if (parent == null) {
                    PageVideoFlowBinding.inflate(layoutInflater)
                } else {
                    PageVideoFlowBinding.inflate(layoutInflater, parent, false)
                }
            )
        }
    }

    private val log = registerLog()

    private val clickHelper = ClickHelper(onClick = ::onClick)

    private var videoLength: Long = 0
    var videoProgress: Long = 0
        private set
    private var videoState = VideoState.Pending

    private var isTouchSeekMode = false

    private var seekModeStartPosition = 0L

    private var immersiveSeekActive = false

    private val savedButtonVisibility = mutableMapOf<Int, Int>()

    private val seekButtonIds = intArrayOf(
        R.id.subtitleButton,
        R.id.playButton
    )

    private var videoTouchHelper = VideoTouchHelper(
        baseWeight = Preferences.videoTouchSeekBaseWeight.get(),
        videoController = this,
        xThreshold = ViewConfiguration.get(itemView.context).scaledTouchSlop * 2F,
        yMaxRangeRatio = 0.5F,
        minWeight = 0.05F,
        sideRegionRatio = Preferences.gestureSideRegionRatio.get()
    )

    private var videoController: VideoController? = null

    private var videoTouchDisplay: VideoTouchDisplay? = null
    private val sliderAnimator: DeconstructSlider.AnimationDelegate

    private var changeDecorationCallback: DecorationVisibilityCallback? = null

    private var currentBrightness = 0.5F
    private var currentVolumePercent = 50

    private var isLandscapeVideo = false

    private var isLandscapeOrientation = false

    private val gestureIndicatorHideTask = task {
        binding.gestureIndicator.isVisible = false
    }

    private val autoHideControlsTask = task {
        autoHideControls()
    }

    val videoPlayerView: PlayerView
        get() {
            return binding.playerView
        }

    private var isControlVisibility = false

    private var lastChangeTime = 0L
    private var isSliderTouched = false

    private val controllerVisibleFilter = PipVisibleFilter(binding.controlLayout)
    private val pipButtonVisibleFilter = PipVisibleFilter(binding.pipButton)
    private val fullscreenButtonVisibleFilter = PipVisibleFilter(binding.fullscreenToggleButton)

    private var lastMediaFile: MediaInfo.File? = null

    private var backgroundFadeAnimator: Animator? = null

    private val sliderChangeListener = object : DeconstructSlider.SliderChangeListener {
        override fun onTouchDown() {
            isSliderTouched = true
            binding.progressTextView.isVisible = true
            val currentTime = (binding.progressSlider.progress * videoLength).toLong()
            seekTo(currentTime)
            lastChangeTime = now()
            updateProgressTextView(currentTime)
            sliderAnimator.onTouchDown()
            scheduleAutoHide()
        }

        override fun onTouchUp() {
            seekTo((binding.progressSlider.progress * videoLength).toLong())
            lastChangeTime = now()
            isSliderTouched = false
            sliderAnimator.onTouchUp()
            // 非沉浸显示模式下保持视频时间信息可见
            if (isControlVisibility) {
                updateProgressTextView(videoProgress)
            } else {
                binding.progressTextView.isVisible = false
            }
            scheduleAutoHide()
        }

        override fun onProgressChanged(progress: Float, fromUser: Boolean) {
            if (fromUser) {
                val now = now()
                if (now - lastChangeTime > 100) {
                    lastChangeTime = now
                    val currentTime = (videoLength * progress).toLong()
                    seekTo(currentTime)
                    updateProgressTextView(currentTime)
                    scheduleAutoHide()
                }
            }
        }
    }

    private val delayHideArtworkTask = task {
        binding.artworkView.isVisible = false
    }

    private var currentTracks: VideoTrackGroup? = null

    val videoListener = object : VideoListener {
        override fun onVideoBegin() {
            changeState(
                "onVideoBegin",
                if (videoController?.isPlaying() == true) {
                    VideoState.Playing
                } else {
                    VideoState.Ready
                }
            )
            delayHideArtworkTask.delayOnUI(12)
            updateSubtitle()
        }

        override fun onVideoProgress(ms: Long) {
            updateProgress(ms)
        }

        override fun onPlay() {
            animatePlayButtonToPauseAndHide()
            changeState("onPlay", VideoState.Playing)
        }

        override fun onPause() {
            log.i("onPause")
            if (videoState != VideoState.Pending) {
                changeState("onPause", VideoState.Paused)
                if (!isTouchSeekMode) {
                    showPlayButton()
                }
            }
        }

        override fun onVideoEnd() {
            changeState("onVideoEnd", VideoState.Ended)
            if (bindingAdapterPosition == RecyclerView.NO_POSITION) {
                videoTouchDisplay?.onVideoEnd(-1)
            } else {
                videoTouchDisplay?.onVideoEnd(bindingAdapterPosition)
            }
        }

        override fun onPlayerError(msg: String) {
            log.w("onPlayerError: $msg")
            Toast.makeText(itemView.context, msg, Toast.LENGTH_SHORT).show()
        }

        override fun onTracksChanged(tracks: VideoTrackGroup) {
            log.i("onTracksChanged: size = ${tracks.tracks.size}, enable = ${tracks.enable}")
            currentTracks = tracks
            val notEmpty = tracks.tracks.isNotEmpty()
            binding.subtitleButton.isVisible = notEmpty
            if (notEmpty) {
                binding.subtitleButton.setImageResource(
                    if (tracks.enable) {
                        R.drawable.subtitles_24
                    } else {
                        R.drawable.subtitles_off_24
                    }
                )
            }
        }
    }

    private fun changeState(tag: String, state: VideoState) {
        val oldState = this.videoState
        this.videoState = state
        log.i("changeState: ${tag}, old = ${oldState}, new = $state")
    }

    init {
        binding.playerView.setOnClickListener(clickHelper)
        sliderAnimator = DeconstructSlider.AnimationDelegate(binding.progressSlider)
        binding.progressSlider.sliderChangeListener = sliderChangeListener
        binding.subtitleButton.setOnClickListener {
            showSubtitleSelectDialog()
        }
        binding.gestureHost.also {
            it.registerPenetrate(binding.subtitleButton)
            it.registerPenetrate(binding.fullscreenToggleButton)
            it.registerPenetrate(binding.pipButton)
            it.registerPenetrate(binding.playButton)
            it.flowTouchListener = videoTouchHelper
        }
        binding.fullscreenToggleButton.setOnClickListener {
            videoTouchDisplay?.onFullscreenToggleClick()
        }
        binding.pipButton.setOnClickListener {
            videoTouchDisplay?.onEnterPipMode()
        }
        binding.playButton.setOnClickListener {
            togglePlayPause()
        }
        pipButtonVisibleFilter.preference.setVisible(
            Preferences.isShowPipBtn.get() && PIPHelper.isSupported(itemView.context)
        )

        initSliderAnimation()
        initVideoBackground()
    }

    private fun initVideoBackground() {
        binding.videoBackground.setRenderEffect(
            RenderEffect.createBlurEffect(
                50F, 50F, Shader.TileMode.CLAMP
            )
        )
        // 設置 40% 的黑色遮罩 (十六進制 66 代表約 40% 透明度)
        // #000000 是黑色，SRC_ATOP 會把黑色疊加在圖片上
        binding.videoBackground.setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP)
    }

    @OptIn(UnstableApi::class)
    private fun updateSubtitle() {
        // 在初始化 PlayerView 时设置
        videoPlayerView.subtitleView?.let {
            it.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            it.setStyle(
                CaptionStyleCompat(
                    // 字体颜色
                    Color.WHITE,
                    // 背景颜色（设为透明更现代）
                    Color.TRANSPARENT,
                    // 窗口颜色
                    Color.TRANSPARENT,
                    // 边缘效果：外阴影
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    // 阴影颜色
                    Color.BLACK,
                    // 字体样式
                    Typeface.DEFAULT
                )
            )
            // 设置字幕大小（比例单位）
            val playerWidth = it.width
            val playerHeight = it.height
            val subtitleWeight = if (playerWidth > playerHeight) {
                1F
            } else {
                0.6F
            }
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleWeight)
        }
    }

    private fun showSubtitleSelectDialog() {
        val tracks = currentTracks
        if (tracks == null || tracks.tracks.isEmpty()) {
            return
        }
        val dialog = SubtitleSelectDialog(itemView.context, tracks) {
            videoController?.selectTrack(it)
            updateSubtitle()
            log.i("selectTrack: ${it?.label}")
        }
        dialog.show()
    }

    private fun initSliderAnimation() {
        val context = itemView.context
        val activeColor = context.getColor(R.color.progress_active)
        val inactiveColor = context.getColor(R.color.progress_inactive)
        sliderAnimator.defaultColor(activeColor, inactiveColor)
        sliderAnimator.touchedColor(activeColor, inactiveColor)
        val displayMetrics = context.resources.displayMetrics
        val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1F, displayMetrics)
        sliderAnimator.defaultSize(
            active = (4F * dp).toInt(),
            inactive = (2F * dp).toInt(),
            gap = (3F * dp).toInt(),
        )
        sliderAnimator.touchedSize(
            active = (8F * dp).toInt(),
            inactive = (4F * dp).toInt(),
            gap = (6F * dp).toInt(),
        )
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgressTextView(currentTime: Long) {
        if (binding.progressTextView.isVisible) {
            val current = max(0, min(currentTime, videoLength))
            binding.progressTextView.text =
                "${formatTime(current)} / ${formatTime(videoLength)}"
        }
    }

    private fun seekTo(value: Long) {
        videoController?.seekTo(value)
    }

    private fun now(): Long {
        return System.currentTimeMillis()
    }

    fun onSelected(isDecorationShown: Boolean) {
        videoProgress = 0
        // 不在这里 seekTo(0)，进度由 VideoManager.play() 统一管理，避免多余的操作
        updateControlVisibility(isDecorationShown)
    }

    private fun updateProgress(ms: Long) {
        // 每20毫秒更新一次进度
        if (videoProgress / 40 != ms / 40) {
            videoProgress = ms
            if (videoLength < 0) {
                videoLength = 0
            }
            if (videoLength == 0L) {
                if (!isSliderTouched) {
                    binding.progressSlider.setProgress(0F)
                }
                return
            }
            if (!isSliderTouched) {
                binding.progressSlider.setProgress(videoProgress * 1F / videoLength)
            }
            // 非沉浸显示模式下持续刷新视频时间信息
            updateProgressTextView(videoProgress)
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms / 1000) % 60
        if (seconds < 10) {
            return "${minutes}:0${seconds}"
        }
        return "${minutes}:${seconds}"
    }

    private fun enterImmersiveSeek() {
        immersiveSeekActive = true
        isSliderTouched = true
        savedButtonVisibility.clear()
        for (id in seekButtonIds) {
            val view = binding.controlLayout.findViewById<android.view.View>(id)
            if (view != null) {
                savedButtonVisibility[id] = view.visibility
                view.visibility = android.view.View.GONE
            }
        }
        binding.controlLayout.animate().cancel()
        binding.controlLayout.alpha = 1F
        binding.controlLayout.isVisible = true
        binding.progressTextView.isVisible = true
        updateProgressTextView(seekModeStartPosition)
        sliderAnimator.onTouchDown()
    }

    private fun exitImmersiveSeek() {
        sliderAnimator.onTouchUp()
        binding.progressTextView.isVisible = false
        binding.controlLayout.isVisible = false
        for ((id, visibility) in savedButtonVisibility) {
            val view = binding.controlLayout.findViewById<android.view.View>(id)
            view?.visibility = visibility
        }
        savedButtonVisibility.clear()
        isSliderTouched = false
        immersiveSeekActive = false
    }

    fun onInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        binding.controlLayout.setPadding(left, top, right, bottom)
    }

    fun onFocusChange(
        controller: VideoController?,
        touchDisplay: VideoTouchDisplay?,
        decorationCallback: DecorationVisibilityCallback?,
        showArtwork: Boolean = true,
    ) {
        this.videoController = controller
        this.videoTouchDisplay = touchDisplay
        this.changeDecorationCallback = decorationCallback
        binding.artworkView.isVisible = videoController == null && showArtwork
        // 获得焦点时，如果模糊背景被隐藏（随机模式无缝切换），执行淡入
        if (controller != null) {
            fadeInVideoBackground()
        } else {
            // 失去焦点时取消自动隐藏计时，避免对非当前项触发隐藏
            cancelAutoHide()
        }
    }

    fun resetScaleGesture() {
        binding.gestureHost.resetScaleGesture()
    }

    fun setGestureControlEnabled(enabled: Boolean) {
        videoTouchHelper.gestureControlEnabled = enabled
        binding.gestureHost.shouldInterceptVerticalSwipe = if (enabled) {
            { touchDownX, viewWidth ->
                videoTouchHelper.shouldInterceptVerticalSwipe(touchDownX, viewWidth)
            }
        } else {
            null
        }
    }

    fun onBind(media: MediaInfo.File) {
        val isMediaChanged = lastMediaFile !== media
        lastMediaFile = media
        clickHelper.reset()
        resetScaleGesture()
        videoTouchHelper.gestureControlEnabled = Preferences.isGestureControlEnabled.get()
        binding.gestureHost.shouldInterceptVerticalSwipe = if (videoTouchHelper.gestureControlEnabled) {
            { touchDownX, viewWidth ->
                videoTouchHelper.shouldInterceptVerticalSwipe(touchDownX, viewWidth)
            }
        } else {
            null
        }
        if (isMediaChanged) {
            Glide.with(itemView)
                .load(media.uri)
                .fitCenter()
                .into(binding.artworkView)
            binding.artworkView.isVisible = true
            binding.playButton.isVisible = false
        }
        changeState("onBind", VideoState.Pending)
        MetadataLoader.load(itemView.context, media) { metadata ->
            if (lastMediaFile === media) {
                videoLength = metadata?.duration ?: 0
                log.i("onBind: duration = ${metadata?.duration}")
                val w = metadata?.width ?: 0
                val h = metadata?.height ?: 0
                isLandscapeVideo = w > h && w > 0 && h > 0
                updateFullscreenButton()
            }
        }
        binding.root.post {
            updateSubtitle()
        }
        if (isMediaChanged) {
            // 确保每次重新绑定都是干净的
            binding.videoBackground.setImageDrawable(null)
            binding.videoBackground.alpha = 1F
            cancelBackgroundFade()
            binding.subtitleButton.isVisible = false
            if (Preferences.isBlurVideoBackground.get()) {
                loadBlurBackground(media.uri, crossFade = true)
            }
        }
    }

    /**
     * 无缝重绑定：用于随机模式切换时把当前 holder 的数据换成正在播放的视频，
     * 但不重新显示全屏预览图，避免画面闪烁。
     */
    fun onBindSeamless(media: MediaInfo.File) {
        lastMediaFile = media
        clickHelper.reset()
        resetScaleGesture()
        videoTouchHelper.gestureControlEnabled = Preferences.isGestureControlEnabled.get()
        binding.gestureHost.shouldInterceptVerticalSwipe = if (videoTouchHelper.gestureControlEnabled) {
            { touchDownX, viewWidth ->
                videoTouchHelper.shouldInterceptVerticalSwipe(touchDownX, viewWidth)
            }
        } else {
            null
        }
        // 保持全屏预览图隐藏，防止切换时闪现
        binding.artworkView.isVisible = false
        MetadataLoader.load(itemView.context, media) { metadata ->
            if (lastMediaFile === media) {
                videoLength = metadata?.duration ?: 0
                log.i("onBindSeamless: duration = ${metadata?.duration}")
                val w = metadata?.width ?: 0
                val h = metadata?.height ?: 0
                isLandscapeVideo = w > h && w > 0 && h > 0
                updateFullscreenButton()
            }
        }
        binding.root.post {
            updateSubtitle()
        }
        binding.subtitleButton.isVisible = false
        if (Preferences.isBlurVideoBackground.get()) {
            // 随机模式无缝切换时，模糊背景先隐藏，等获得焦点后再淡入
            binding.videoBackground.alpha = 0F
            cancelBackgroundFade()
            loadBlurBackground(media.uri, crossFade = false)
        }
    }

    private fun loadBlurBackground(uri: Uri, crossFade: Boolean = true) {
        val request = Glide.with(itemView)
            .load(uri)
            .override(20)
        if (crossFade) {
            request.transition(
                DrawableTransitionOptions.withCrossFade(
                    DrawableCrossFadeFactory.Builder(1000) // 设置时长为 1s
                        .setCrossFadeEnabled(true) // 关键：开启真正的交叉淡入淡出，防止闪烁
                        .build()
                )
            )
        }
        request.into(binding.videoBackground)
    }

    private fun cancelBackgroundFade() {
        backgroundFadeAnimator?.cancel()
        backgroundFadeAnimator = null
    }

    private fun fadeInVideoBackground(duration: Long = RANDOM_BACKGROUND_FADE_DURATION) {
        if (binding.videoBackground.alpha >= 1F) {
            return
        }
        cancelBackgroundFade()
        binding.videoBackground.apply {
            alpha = 0F
            isVisible = true
        }
        backgroundFadeAnimator = ObjectAnimator.ofFloat(
            binding.videoBackground,
            View.ALPHA,
            0F,
            1F
        ).apply {
            this.duration = duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    backgroundFadeAnimator = null
                }
            })
            start()
        }
    }

    private fun updateControlVisibility(visible: Boolean) {
        isControlVisibility = visible
        controllerVisibleFilter.base.setVisible(visible)
        pipButtonVisibleFilter.base.setVisible(visible)
        updateFullscreenButton()
        // 非沉浸显示模式下显示视频时间信息（当前播放时间 / 总时长）
        binding.progressTextView.isVisible = visible
        if (visible) {
            updateProgressTextView(videoProgress)
        }
        changeDecorationCallback?.changeDecorationVisibility(visible)
        // 非沉浸模式下开启无交互自动隐藏倒计时，沉浸模式下取消
        if (visible) {
            scheduleAutoHide()
        } else {
            cancelAutoHide()
        }
    }

    /**
     * 重新计时非沉浸模式下的无交互自动隐藏倒计时。
     * 仅在非沉浸显示模式且未处于手势 seek 中时生效。
     */
    private fun scheduleAutoHide() {
        autoHideControlsTask.cancel()
        if (isControlVisibility && !isTouchSeekMode) {
            autoHideControlsTask.delayOnUI(AUTO_HIDE_DELAY)
        }
    }

    private fun cancelAutoHide() {
        autoHideControlsTask.cancel()
    }

    private fun autoHideControls() {
        // 连续 AUTO_HIDE_DELAY 毫秒无任何交互，自动切换至沉浸模式
        if (isControlVisibility && !isTouchSeekMode && !isSliderTouched) {
            updateControlVisibility(false)
        } else if (isControlVisibility) {
            // 处于交互态（如手势 seek 中），重新计时
            scheduleAutoHide()
        }
    }

    fun onPipChanged(isInPictureInPictureMode: Boolean) {
        controllerVisibleFilter.onPipChanged(isInPictureInPictureMode)
        pipButtonVisibleFilter.onPipChanged(isInPictureInPictureMode)
        fullscreenButtonVisibleFilter.onPipChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            delayHideArtworkTask.cancel()
            binding.artworkView.isVisible = false
            cancelAutoHide()
        }
    }

    fun updateOrientationState(isLandscape: Boolean) {
        isLandscapeOrientation = isLandscape
        binding.fullscreenToggleButton.setImageResource(
            if (isLandscape) R.drawable.fullscreen_exit_24 else R.drawable.fullscreen_24
        )
    }

    private fun updateFullscreenButton() {
        fullscreenButtonVisibleFilter.base.setVisible(isControlVisibility)
    }

    private fun togglePlayPause() {
        if (videoState == VideoState.Pending) {
            return
        }
        scheduleAutoHide()
        val isPlaying = videoController?.isPlaying() ?: false
        if (isPlaying) {
            videoController?.pause()
        } else if (videoState == VideoState.Paused || videoState == VideoState.Ready) {
            videoController?.play()
        }
    }

    private var playButtonAnimator: Animator? = null

    private fun showPlayButton() {
        playButtonAnimator?.cancel()
        binding.playButton.apply {
            setImageResource(R.drawable.play_circle_24px)
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            isVisible = true
        }
    }

    private fun animatePlayButtonToPauseAndHide() {
        if (!binding.playButton.isVisible) {
            return
        }
        playButtonAnimator?.cancel()
        binding.playButton.setImageResource(R.drawable.pause_circle_24px)
        val scaleX = ObjectAnimator.ofFloat(binding.playButton, "scaleX", 1f, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(binding.playButton, "scaleY", 1f, 1.5f)
        val alpha = ObjectAnimator.ofFloat(binding.playButton, "alpha", 1f, 0f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.playButton.isVisible = false
                    binding.playButton.scaleX = 1f
                    binding.playButton.scaleY = 1f
                    binding.playButton.alpha = 1f
                }
            })
            playButtonAnimator = this
            start()
        }
    }

    private fun onClick(clickCount: Int) {
        if (isTouchSeekMode) {
            log.i("onClick isTouchSeekMode = true, break")
            return
        }
        // 任意点击交互均重置自动隐藏倒计时
        scheduleAutoHide()
        when (clickCount) {
            1 -> {
                // 点击一次
                updateControlVisibility(!isControlVisibility)
                log.i("onClick clickCount == 1")
            }

            2 -> {
                // 点击两次
                log.i("onClick clickCount == 2 videoState = $videoState")
                updateControlVisibility(true)
                val isPlaying = videoController?.isPlaying() ?: false
                if (isPlaying) {
                    videoController?.pause()
                } else if (videoState == VideoState.Paused || videoState == VideoState.Ready) {
                    videoController?.play()
                }
            }

            3 -> {
                resetScaleGesture()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun startPlaybackSpeed() {
        videoController?.startPlaybackSpeed()
        videoTouchDisplay?.startPlaybackSpeed()
        isTouchSeekMode = true
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        val speed = Preferences.playbackSpeed.get()
        binding.speedIndicator.text = "${speed}x 倍速播放"
        binding.speedIndicator.isVisible = true
    }

    override fun stopPlaybackSpeed() {
        videoController?.stopPlaybackSpeed()
        videoTouchDisplay?.stopPlaybackSpeed()
        isTouchSeekMode = false
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        binding.speedIndicator.isVisible = false
        scheduleAutoHide()
    }

    @SuppressLint("SetTextI18n")
    override fun startSeekMode() {
        seekModeStartPosition = videoProgress
        videoController?.startSeekMode()
        videoTouchDisplay?.startSeekMode()
        isTouchSeekMode = true
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        binding.playButton.isVisible = false
        if (!isControlVisibility) {
            enterImmersiveSeek()
        } else {
            binding.progressTextView.isVisible = true
            updateProgressTextView(seekModeStartPosition)
            sliderAnimator.onTouchDown()
            isSliderTouched = true
        }
        // 进入手势 seek 时取消自动隐藏计时，避免拖拽过程中隐藏控件
        cancelAutoHide()
    }

    @SuppressLint("SetTextI18n")
    override fun onSeek(weight: Float, precision: Float) {
        videoController?.onTouchSeek(weight = weight, precision = precision)
        videoTouchDisplay?.onTouchSeek(weight = weight, precision = precision)
        val duration = videoLength
        if (duration > 0) {
            val targetPosition = (seekModeStartPosition + (weight * duration).toLong())
                .coerceIn(0, duration)
            binding.progressSlider.setProgress(targetPosition * 1F / duration)
            updateProgressTextView(targetPosition)
        }
    }

    override fun stopSeekMode(weight: Float) {
        videoController?.stopSeekMode(weight)
        videoTouchDisplay?.stopSeekMode(weight)
        isTouchSeekMode = false
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        if (immersiveSeekActive) {
            exitImmersiveSeek()
        } else {
            sliderAnimator.onTouchUp()
            isSliderTouched = false
            // 非沉浸显示模式下保持视频时间信息可见
            if (isControlVisibility) {
                updateProgressTextView(videoProgress)
            } else {
                binding.progressTextView.isVisible = false
            }
        }
        scheduleAutoHide()
    }

    override fun onScaleGestureChanged(matrix: Matrix) {
        binding.matrixFrameLayout.updateMatrix(matrix)
        scheduleAutoHide()
    }

    override fun onGestureVerticalBegin(type: VideoTouchHelper.GestureVerticalType) {
        scheduleAutoHide()
        when (type) {
            VideoTouchHelper.GestureVerticalType.Brightness -> {
                currentBrightness = try {
                    val window = (itemView.context as? android.app.Activity)?.window
                    val layoutParams = window?.attributes
                    layoutParams?.screenBrightness?.let { if (it >= 0) it else 0.5F } ?: 0.5F
                } catch (_: Exception) {
                    0.5F
                }
            }

            VideoTouchHelper.GestureVerticalType.Volume -> {
                val audioManager = itemView.context.getSystemService(Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                currentVolumePercent = if (maxVolume > 0) {
                    (currentVolume * 100 / maxVolume)
                } else {
                    0
                }
            }

            VideoTouchHelper.GestureVerticalType.None -> {}
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onGestureVerticalMove(type: VideoTouchHelper.GestureVerticalType, deltaRatio: Float) {
        gestureIndicatorHideTask.cancel()
        scheduleAutoHide()
        when (type) {
            VideoTouchHelper.GestureVerticalType.Brightness -> {
                currentBrightness = (currentBrightness + deltaRatio * 0.5F).coerceIn(0F, 1F)
                try {
                    val window = (itemView.context as? android.app.Activity)?.window
                    val layoutParams = window?.attributes
                    if (layoutParams != null) {
                        layoutParams.screenBrightness = currentBrightness
                        window.attributes = layoutParams
                    }
                } catch (_: Exception) {}
                binding.gestureIndicator.text = "亮度: ${(currentBrightness * 100).roundToInt()}%"
                binding.gestureIndicator.isVisible = true
            }

            VideoTouchHelper.GestureVerticalType.Volume -> {
                currentVolumePercent = (currentVolumePercent + (deltaRatio * 100F).roundToInt())
                    .coerceIn(0, 100)
                val audioManager = itemView.context.getSystemService(Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val targetVolume = (currentVolumePercent * maxVolume / 100F).roundToInt()
                    .coerceIn(0, maxVolume)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                binding.gestureIndicator.text = "音量: $currentVolumePercent%"
                binding.gestureIndicator.isVisible = true
            }

            VideoTouchHelper.GestureVerticalType.None -> {}
        }
    }

    override fun onGestureVerticalEnd(type: VideoTouchHelper.GestureVerticalType) {
        gestureIndicatorHideTask.delayOnUI(800)
        scheduleAutoHide()
    }

    enum class VideoState {
        Pending,
        Ready,
        Playing,
        Paused,
        Ended,
    }

    interface VideoTouchDisplay {
        fun startPlaybackSpeed()

        fun stopPlaybackSpeed()

        fun startSeekMode()

        fun onTouchSeek(weight: Float, precision: Float)

        fun stopSeekMode(weight: Float)

        fun onVideoEnd(position: Int)

        fun onFullscreenToggleClick()

        fun onEnterPipMode()
    }

    interface DecorationVisibilityCallback {
        fun changeDecorationVisibility(isShow: Boolean)
    }

}