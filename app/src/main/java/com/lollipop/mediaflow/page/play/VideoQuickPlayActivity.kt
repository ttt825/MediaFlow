package com.lollipop.mediaflow.page.play

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.ActivityVideoQuickPlayBinding
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BlurHelper
import com.lollipop.mediaflow.ui.CustomOrientationActivity
import com.lollipop.mediaflow.ui.PipVisibleFilter
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoManager
import com.lollipop.mediaflow.video.VideoTrackGroup
import kotlinx.coroutines.launch


class VideoQuickPlayActivity : CustomOrientationActivity(), VideoPlayHolder.VideoTouchDisplay,
    VideoPlayHolder.DecorationVisibilityCallback {

    private val binding by lazy {
        ActivityVideoQuickPlayBinding.inflate(layoutInflater)
    }

    private val backBtnVisibleFilter by lazy {
        PipVisibleFilter(binding.backBtn)
    }

    private val titleVisibleFilter by lazy {
        PipVisibleFilter(binding.titleView)
    }

    private val tagVisibleFilter by lazy {
        PipVisibleFilter(binding.tagGroup)
    }

    private val videoHolder by lazy {
        VideoPlayHolder.create(layoutInflater)
    }

    private val videoListener = object : VideoListener {
        override fun onVideoBegin() {}

        override fun onVideoProgress(ms: Long) {}

        override fun onPlay() {
            onVideoPlay()
        }

        override fun onPause() {
            onVideoPause()
        }

        override fun onVideoEnd() {}

        override fun onPlayerError(msg: String) {}

        override fun onTracksChanged(tracks: VideoTrackGroup) {}
    }

    private val videoManager by lazy {
        VideoManager(this).also {
            it.eventObserver.add(videoListener)
        }
    }

    private var isDecorationShown = true
    private var metadataCache: MediaMetadata? = null

    private val pipActionAdapter = PIPHelper.registerPipActions(this) { action ->
        when (action) {
            PIPHelper.Action.PLAY -> videoManager.play()
            PIPHelper.Action.PAUSE -> videoManager.pause()
            PIPHelper.Action.PREVIOUS -> {
            }

            PIPHelper.Action.NEXT -> {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        pipActionAdapter
        initInsetsListener()
        binding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        updateBlur()
        changeDecoration(true)
        val videoUri = intent.data
        if (videoUri != null) {
            loadVideo(videoUri)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        backBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        titleVisibleFilter.onPipChanged(isInPictureInPictureMode)
        tagVisibleFilter.onPipChanged(isInPictureInPictureMode)
        videoHolder.onPipChanged(isInPictureInPictureMode)
    }

    private fun loadVideo(videoUri: Uri) {
        val act = this
        lifecycleScope.launch {
            log.i("loadVideo: $videoUri")
            val mediaFile = MediaLoader.loadMediaFileSync(act, videoUri)
            log.i("loadVideo: ${mediaFile?.docId}")
            if (mediaFile != null) {
                MetadataLoader.load(act, mediaFile) { metadata ->
                    updateTitle(
                        titleValue = mediaFile.name,
                        size = metadata?.formatFileSize(mediaFile.size) ?: "",
                        format = "",
                        duration = metadata?.durationFormat ?: ""
                    )
                    metadataCache = metadata
                    updatePipParams()
                }
                videoManager.resetMediaList(listOf(mediaFile))
                videoHolder.onBind(mediaFile)
                videoHolder.onSelected(isDecorationShown)
                videoManager.play(0)
                videoHolder.onPipChanged(isInPictureInPictureMode)
            }
        }
    }

    private fun onVideoPlay() {
        updatePipParams()
    }

    private fun onVideoPause() {
        updatePipParams()
    }

    private fun updatePipParams() {
        val isPlaying = videoManager.isPlaying()
        val pipOption = PIPHelper.Option(
            hasPrev = false,
            hasNext = false,
            hasPlay = !isPlaying,
            hasPause = isPlaying
        )
        PIPHelper.setParams(this, metadataCache, pipOption)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            binding.blurTarget,
            binding.backBtnBlur,
        )
    }

    private fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
        applyDecorationVisibility(isVisibility)
    }

    private fun applyDecorationVisibility(isVisibility: Boolean) {
        titleVisibleFilter.base.setVisible(isVisibility)
        tagVisibleFilter.base.setVisible(isVisibility)
        backBtnVisibleFilter.base.setVisible(isVisibility)
    }

    override fun onResume() {
        super.onResume()
        backBtnVisibleFilter.preference.setVisible(Preferences.isShowBackBtn.get())
        titleVisibleFilter.preference.setVisible(Preferences.isShowTitle.get())
        tagVisibleFilter.preference.setVisible(Preferences.isShowTag.get())
    }

    private fun createContentPanel(): View {
        videoManager.changeView(null, videoHolder.videoPlayerView)
        videoHolder.onFocusChange(
            controller = videoManager,
            touchDisplay = this,
            decorationCallback = this
        )
        videoManager.eventObserver.setFocus(videoHolder.videoListener)
        return videoHolder.itemView
    }

    override fun onOrientationChanged(orientation: Orientation) {
        super.onOrientationChanged(orientation)
        videoHolder.resetScaleGesture()
    }

    private fun updateTitle(
        titleValue: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        binding.root.post {
            binding.titleView.text = titleValue
            binding.sizeTagView.text = size
            binding.formatTagView.text = format
            binding.durationTagView.text = duration
            titleVisibleFilter.base.setVisible(titleValue.isNotEmpty())
            binding.sizeTagView.isVisible = size.isNotEmpty()
            binding.formatTagView.isVisible = format.isNotEmpty()
            binding.durationTagView.isVisible = duration.isNotEmpty()
        }
        log.i("updateTitle: $titleValue, $size, $format, $duration")
        if (titleValue.isEmpty()) {
            log.e("updateTitle: isEmpty", RuntimeException())
        }
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.root)
        bindGuidelineInsets(
            leftGuideline = binding.startGuideLine,
            topGuideline = binding.topGuideLine,
            rightGuideline = binding.endGuideLine,
            bottomGuideline = binding.bottomGuideLine,
        )
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        videoHolder.onInsetsChanged(left, top, right, bottom)
    }

    override fun startPlaybackSpeed() {
    }

    override fun stopPlaybackSpeed() {
    }

    override fun startSeekMode() {
    }

    override fun onTouchSeek(weight: Float, precision: Float) {
    }

    override fun stopSeekMode(weight: Float) {
    }

    override fun onVideoEnd(position: Int) {
    }

    override fun changeDecorationVisibility(isShow: Boolean) {
        changeDecoration(isShow)
    }

    override fun onFullscreenToggleClick() {
    }

    override fun onEnterPipMode() {
        if (!PIPHelper.isSupported(this) || PIPHelper.isInPictureInPictureMode(this)) {
            return
        }
        enterPictureInPictureMode()
    }
}