package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.data.PlaybackMode
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.MediaDeleteHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.safeRun
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoManager
import com.lollipop.mediaflow.video.VideoTrackGroup
import kotlin.math.max

class VideoFlowActivity : BasicFlowActivity(), VideoPlayHolder.VideoTouchDisplay,
    VideoPlayHolder.DecorationVisibilityCallback {

    private val viewPager2 by lazy {
        ViewPager2(this)
    }

    /**
     * 完整列表，始终与首页排序顺序一致，用于侧边栏播放列表显示。
     */
    private val mediaData = mutableListOf<MediaInfo.File>()

    /**
     * Adapter 实际显示的列表。
     * 顺序模式：与 mediaData 同步（完整列表）。
     * 随机模式：只含 2 项 = [当前播放, 预计算的下一个随机视频]，
     * 用户下滑时直接显示预计算的下一项，不会出现先显示顺序下一项再闪变的问题。
     */
    private val displayData = mutableListOf<MediaInfo.File>()

    private val videoAdapter = PlayAdapter(displayData)

    private val mediaFlowStoreView by lazy {
        MediaFlowStoreView(::onItemClick, ::onPlaylistItemLongClick)
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

    private var lastHolder: VideoPlayHolder? = null

    /**
     * 随机模式下，当前播放视频在 mediaData 中的索引。
     */
    private var randomCurrentIdx = 0

    /**
     * 随机模式下，预计算的下一个随机视频在 mediaData 中的索引。
     */
    private var randomNextIdx = 0

    /**
     * 用户当前正在观看的 displayData 索引。
     */
    private var currentUserPosition = 0

    /**
     * 随机模式下，用户下滑到 position 1 后，需要等待滑动动画静止再推进随机序列。
     */
    private var pendingAdvanceRandom = false

    /**
     * 标识当前 ViewPager2 的页面切换是否由程序主动触发（非用户滑动）。
     * 用于在 onPageSelected 中区分用户滑动与程序跳转。
     */
    private var isProgrammaticChange = false

    /**
     * 随机模式下，用户下滑到 position 1 后，position 1 仅作为预览，
     * 等滑动动画结束 advanceRandom 切回 position 0 后再播放。
     */
    private var isRandomPreviewing = false

    /**
     * 随机模式 advanceRandom 切回 position 0 期间，原 holder 失去焦点时不应重新显示全屏预览图。
     */
    private var suppressArtworkOnFocusClear = false

    private val pipActionAdapter = PIPHelper.registerPipActions(this) { action ->
        when (action) {
            PIPHelper.Action.PLAY -> videoManager.play()
            PIPHelper.Action.PAUSE -> videoManager.pause()
            PIPHelper.Action.PREVIOUS -> playPrevious()
            PIPHelper.Action.NEXT -> playNext()
        }
    }

    private val mediaParams = MediaPlayLauncher.params()

    private var gallery: MediaStore.Gallery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        setAppearanceLightStatusBars(false)
        pipActionAdapter
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerOpen) {
                    changeDrawerState(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        reloadData()
    }

    /**
     * 侧边栏播放列表点击：position 是 mediaData 索引。
     */
    private fun onItemClick(position: Int) {
        when (playbackMode) {
            PlaybackMode.Sequential -> setCurrentItemProgrammatically(position)
            PlaybackMode.Shuffle -> {
                // 跳转到指定视频，重新预计算下一个
                val idx = position.coerceIn(0, mediaData.size - 1)
                buildDisplayData(idx)
                videoAdapter.notifyDataSetChanged()
                videoManager.resetMediaList(displayData, 0)
                selectAndPlay(0)
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
        val mediaIdx = currentMediaIndex()
        val isPlaying = videoManager.isPlaying()
        // 随机模式无上一个（禁止上滑），顺序模式列表多于 1 项即可前后切换
        val hasPrev = when (playbackMode) {
            PlaybackMode.Shuffle -> false
            else -> mediaData.size > 1
        }
        val hasNext = mediaData.size > 1
        val pipOption = PIPHelper.Option(
            hasPrev = hasPrev,
            hasNext = hasNext,
            hasPlay = !isPlaying,
            hasPause = isPlaying
        )
        MetadataLoader.load(this, mediaData[mediaIdx]) {
            PIPHelper.setParams(this, it, pipOption)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onPlaylistItemLongClick(position: Int) {
        val file = mediaData.getOrNull(position) ?: return
        MediaDeleteHelper.showDeleteConfirmDialog(this, file, gallery) {
            videoManager.pause()
            // 记录当前播放文件的 docId，删除后通过 docId 重新定位
            val currentDocId = mediaData.getOrNull(currentMediaIndex())?.docId
            mediaData.removeAt(position)
            mediaFlowStoreView.resetData(mediaData)
            val newIdx = if (currentDocId != null) {
                val idx = mediaData.indexOfFirst { it.docId == currentDocId }
                if (idx >= 0) idx else (mediaData.size - 1).coerceAtLeast(0)
            } else {
                (mediaData.size - 1).coerceAtLeast(0)
            }
            buildDisplayData(newIdx)
            videoAdapter.notifyDataSetChanged()
            videoManager.resetMediaList(displayData, 0)
            if (mediaData.isNotEmpty()) {
                val displayIndex = if (playbackMode == PlaybackMode.Sequential) newIdx else 0
                selectAndPlay(displayIndex)
                // 同步更新侧边栏选中位置
                mediaFlowStoreView.setSelectedPosition(newIdx)
            }
        }
    }

    private fun optRecyclerView(callback: (RecyclerView) -> Unit) {
        safeRun {
            val contentPager = viewPager2
            if (contentPager.isEmpty()) {
                return
            }
            contentPager.getChildAt(0).let { recyclerVier ->
                if (recyclerVier is RecyclerView) {
                    callback(recyclerVier)
                }
            }
        }
    }

    /**
     * 当前显示的 displayData 索引（ViewPager2 currentItem）。
     */
    private fun currentPosition(): Int {
        return if (displayData.size > 0) {
            viewPager2.currentItem.coerceIn(0, displayData.size - 1)
        } else {
            0
        }
    }

    /**
     * 当前播放视频在 mediaData 中的索引（与首页列表一致，用于返回首页定位和侧边栏选中）。
     */
    private fun currentMediaIndex(): Int {
        return when (playbackMode) {
            PlaybackMode.Shuffle -> {
                val pos = currentPosition()
                if (pos == 0) randomCurrentIdx else randomNextIdx
            }
            else -> currentPosition()
        }
    }

    /**
     * 程序主动跳转到指定 displayData 索引，会标记 isProgrammaticChange 以避免被 onPageSelected 拦截。
     */
    private fun setCurrentItemProgrammatically(position: Int, smoothScroll: Boolean = true) {
        if (displayData.size <= 0) {
            viewPager2.setCurrentItem(0, smoothScroll)
            return
        }
        val target = position.coerceIn(0, displayData.size - 1)
        isProgrammaticChange = true
        viewPager2.setCurrentItem(target, smoothScroll)
    }

    /**
     * 跳转到指定 displayData 索引并播放。
     * 若目标与当前 currentItem 相同（onPageSelected 不会触发），则手动调用 onSelected。
     */
    private fun selectAndPlay(position: Int) {
        if (displayData.isEmpty()) return
        val target = position.coerceIn(0, displayData.size - 1)
        isProgrammaticChange = false
        if (target == viewPager2.currentItem) {
            currentUserPosition = target
            onSelected(target)
        } else {
            setCurrentItemProgrammatically(target, false)
        }
    }

    /**
     * 从 mediaData 中随机抽取一个索引，尽量不与 exclude 重复（列表多于 1 项时）。
     */
    private fun pickRandomMediaIdx(exclude: Int): Int {
        if (mediaData.size <= 1) return 0
        var target: Int
        do {
            target = (0 until mediaData.size).random()
        } while (target == exclude)
        return target
    }

    /**
     * 根据播放模式构建 displayData。
     *
     * 顺序模式：displayData = mediaData 的副本（完整列表）。
     * 随机模式：displayData = [当前视频, 预计算的下一个随机视频]（仅 2 项），
     * 用户下滑时直接显示预计算的下一项，避免先显示顺序下一项再闪变。
     */
    private fun buildDisplayData(currentMediaIdx: Int) {
        displayData.clear()
        when (playbackMode) {
            PlaybackMode.Sequential -> {
                displayData.addAll(mediaData)
            }
            PlaybackMode.Shuffle -> {
                if (mediaData.isEmpty()) return
                randomCurrentIdx = currentMediaIdx.coerceIn(0, mediaData.size - 1)
                displayData.add(mediaData[randomCurrentIdx])
                if (mediaData.size > 1) {
                    randomNextIdx = pickRandomMediaIdx(randomCurrentIdx)
                    displayData.add(mediaData[randomNextIdx])
                } else {
                    randomNextIdx = 0
                }
            }
        }
    }

    /**
     * 随机模式前进：当前播放 → 预计算的下一个，并计算新的预计算下一个。
     *
     * 用户下滑到 position 1（显示预计算的下一个）后调用：
     * 1. 更新 randomCurrentIdx = randomNextIdx，计算新的 randomNextIdx
     * 2. 更新 displayData[0] = 新当前（原 displayData[1]，内容与用户当前看的相同）
     * 3. 更新 displayData[1] = 新预计算的随机视频
     * 4. 重置 ViewPager2 到 position 0（无动画），视觉无变化（position 0 显示原 position 1 内容）
     *
     * 这样用户继续下滑时，position 1 显示的是新的预计算视频，不闪变。
     */
    private fun advanceRandom() {
        if (mediaData.size <= 1) return
        randomCurrentIdx = randomNextIdx
        randomNextIdx = pickRandomMediaIdx(randomCurrentIdx)
        displayData[0] = mediaData[randomCurrentIdx]
        displayData[1] = mediaData[randomNextIdx]
        // 同步播放器的播放列表，否则 VideoManager 仍按旧的 displayData 播放
        videoManager.resetMediaList(displayData, 0)
        // 先无缝重绑定 offscreen 的 position 0，确保切回时显示的是当前视频 B 而不是旧视频 A
        videoAdapter.notifyItemChanged(0, PlayAdapter.PAYLOAD_SEAMLESS)
        // 当前可见页是 position 1，必须等切回 position 0 后再更新 position 1，
        // 否则 position 1 在可见状态下会被重绑成下一个随机视频并闪现其预览图。
        viewPager2.post {
            isProgrammaticChange = true
            suppressArtworkOnFocusClear = true
            viewPager2.setCurrentItem(0, false)
            viewPager2.post {
                isRandomPreviewing = false
                videoAdapter.notifyItemChanged(1)
            }
        }
    }

    /**
     * 播放下一个：顺序模式循环到下一个；随机模式调用 advanceRandom。
     */
    private fun playNext() {
        if (displayData.isEmpty()) return
        when (playbackMode) {
            PlaybackMode.Sequential -> {
                val next = (currentPosition() + 1) % displayData.size
                setCurrentItemProgrammatically(next)
            }
            PlaybackMode.Shuffle -> advanceRandom()
        }
    }

    /**
     * 播放上一个：顺序模式循环到上一个；随机模式无上一个（禁止上滑），不操作。
     */
    private fun playPrevious() {
        if (displayData.isEmpty()) return
        when (playbackMode) {
            PlaybackMode.Sequential -> {
                val prev = (currentPosition() - 1 + displayData.size) % displayData.size
                setCurrentItemProgrammatically(prev)
            }
            PlaybackMode.Shuffle -> {
                // 随机模式无上一个，不操作
            }
        }
    }

    override fun createContentPanel(): View {
        return viewPager2.also {
            buildContentPanel(it)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = mediaParams.visibility
        var mediaGallery = gallery
        if (mediaGallery == null) {
            mediaGallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
            gallery = mediaGallery
        }
        mediaGallery.loadChoose { gallery, success ->
            mediaData.clear()
            // 播放列表顺序始终与首页一致（由 Gallery 的 sortType 决定）
            mediaData.addAll(gallery.fileList)
            val clickedPosition = mediaParams.currentPosition
            // 通过 docId 在新列表中重新定位点击的文件
            val clickedDocId = gallery.fileList.getOrNull(clickedPosition)?.docId
            val newMediaIdx = when {
                clickedPosition < 0 && mediaData.isNotEmpty() -> {
                    (0 until mediaData.size).random()
                }
                clickedDocId != null -> {
                    val idx = mediaData.indexOfFirst { it.docId == clickedDocId }
                    if (idx >= 0) idx else 0
                }
                mediaData.isNotEmpty() -> {
                    (0 until mediaData.size).random()
                }
                else -> {
                    0
                }
            }
            buildDisplayData(newMediaIdx)
            videoManager.resetMediaList(displayData, 0)
            videoAdapter.notifyDataSetChanged()
            // 侧边栏始终显示完整列表
            mediaFlowStoreView.resetData(mediaData)
            mediaFlowStoreView.setSelectedPosition(newMediaIdx)
            // 顺序模式下跳转到点击的视频；随机模式 displayData[0] 就是当前视频
            val startDisplayIndex = if (playbackMode == PlaybackMode.Sequential) newMediaIdx else 0
            selectAndPlay(startDisplayIndex)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$newMediaIdx")
        }
    }

    /**
     * 切换播放模式时：根据新模式重建 displayData，保持当前播放的视频不变。
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun reorderPlaylist() {
        val currentMediaIdx = currentMediaIndex()
        buildDisplayData(currentMediaIdx)
        val displayIndex = if (playbackMode == PlaybackMode.Sequential) currentMediaIdx else 0
        videoAdapter.notifyDataSetChanged()
        videoManager.resetMediaList(displayData, 0)
        selectAndPlay(displayIndex)
        log.i("reorderPlaylist mode=$playbackMode, currentMediaIdx=$currentMediaIdx")
    }

    override fun onOrientationChanged(orientation: Orientation) {
        super.onOrientationChanged(orientation)
        lastHolder?.resetScaleGesture()
        lastHolder?.updateOrientationState(isFullscreen || orientation == Orientation.LANDSCAPE)
    }

    private fun optCurrentHolder(callback: (VideoPlayHolder) -> Unit) {
        optHolderHolder(currentPosition(), callback)
    }

    private fun optHolderHolder(position: Int, callback: (VideoPlayHolder) -> Unit) {
        optRecyclerView { recyclerVier ->
            if (displayData.size > position && position >= 0) {
                val holder = recyclerVier.findViewHolderForAdapterPosition(position)
                if (holder is VideoPlayHolder) {
                    callback(holder)
                } else {
                    recyclerVier.post {
                        optHolderHolder(position, callback)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mediaFlowStoreView.updateSpanCount(this)
    }

    override fun onDrawerChanged(isOpen: Boolean) {
        // 打开播放列表时不再暂停视频，保持后台播放
    }

    override fun createDrawerPanel(): View {
        return mediaFlowStoreView.getView(this)
    }

    private fun buildContentPanel(viewPager2: ViewPager2) {
        viewPager2.adapter = videoAdapter
        viewPager2.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (displayData.isEmpty()) return
                if (isProgrammaticChange) {
                    // 程序主动跳转（按钮/列表点击/onVideoEnd/advanceRandom 重置），直接更新当前项
                    isProgrammaticChange = false
                    currentUserPosition = position
                    onSelected(position)
                    return
                }
                // 用户手动滑动
                when (playbackMode) {
                    PlaybackMode.Sequential -> {
                        currentUserPosition = position
                        onSelected(position)
                    }
                    PlaybackMode.Shuffle -> {
                        // 随机模式：只允许下滑（position 0 → 1）
                        if (position == 1) {
                            // position 1 仅作为预览，不立即播放，等滑动动画静止后
                            // advanceRandom 切回 position 0 再播放，避免第一帧重复播放。
                            isRandomPreviewing = true
                            currentUserPosition = position
                            onSelected(position)
                            pendingAdvanceRandom = true
                        } else if (position == 0 && currentUserPosition == 1) {
                            // 用户从 position 1 滑回 position 0，需要切回原来的视频
                            isRandomPreviewing = false
                            currentUserPosition = position
                            onSelected(position)
                            pendingAdvanceRandom = false
                        }
                        // position == 0 且 currentUserPosition == 0：初始状态或边界回弹，无需处理
                    }
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE && pendingAdvanceRandom) {
                    pendingAdvanceRandom = false
                    if (currentPosition() == 1) {
                        advanceRandom()
                    }
                }
            }
        })
    }

    private fun onSelected(position: Int) {
        log.i("onSelected: $position")
        // position 是 displayData 索引，转换为 mediaData 索引（与首页列表一致）
        val mediaIdx = when (playbackMode) {
            PlaybackMode.Shuffle -> if (position == 0) randomCurrentIdx else randomNextIdx
            else -> position
        }
        currentUserPosition = position
        // 返回首页时用 mediaData 索引定位（与首页列表顺序一致）
        mediaParams.onSelected(this, mediaIdx)
        // 侧边栏选中用 mediaData 索引
        mediaFlowStoreView.setSelectedPosition(mediaIdx)
        if (position < 0 || position >= displayData.size) {
            updateTitle(titleValue = "", size = "", format = "", duration = "")
        } else {
            val file = displayData[position]
            val job = MetadataLoader.load(this, file) {
                if (displayData.getOrNull(position) === file) {
                    updateTitle(
                        file.name,
                        size = it?.formatFileSize(file.size) ?: "",
                        format = "",
                        duration = it?.durationFormat ?: ""
                    )
                }
            }
            if (job != null) {
                updateTitle(file.name, size = "", format = "", duration = "")
            }
        }

        updatePipParams()
        optHolderHolder(position) { holder ->
            onFocusChanged(holder, position)
            holder.onPipChanged(isInPictureInPictureMode)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        log.i("onSaveInstanceState")
        mediaParams.onSaveInstanceState(this, outState)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        optCurrentHolder { holder ->
            holder.onPipChanged(isInPictureInPictureMode)
        }
    }

    override fun changeDecorationVisibility(isShow: Boolean) {
        changeDecoration(isShow)
    }

    private fun onFocusChanged(holder: VideoPlayHolder, position: Int) {
        log.i("onFocusChanged: $position")
        val isRandomPreview = playbackMode == PlaybackMode.Shuffle && isRandomPreviewing && position == 1

        lastHolder?.let {
            it.onFocusChange(
                controller = null,
                touchDisplay = null,
                decorationCallback = null,
                showArtwork = !suppressArtworkOnFocusClear
            )
        }
        suppressArtworkOnFocusClear = false

        videoManager.changeView(lastHolder?.videoPlayerView, holder.videoPlayerView)

        if (isRandomPreview) {
            // 随机模式 position 1 仅预览：不获取控制器焦点，保持预览图可见，并准备视频但不播放
            holder.onFocusChange(
                controller = null,
                touchDisplay = null,
                decorationCallback = null
            )
            holder.onSelected(isDecorationShown)
            lastHolder = holder
            videoManager.play(position, 0L, autoPlay = false)
        } else {
            holder.onFocusChange(
                controller = videoManager,
                touchDisplay = this,
                decorationCallback = this
            )
            videoManager.eventObserver.setFocus(holder.videoListener)
            holder.onSelected(isDecorationShown)
            lastHolder = holder
            // 需求：所有视频都从头开始播放，不再恢复上次进度缓存
            videoManager.play(position, 0L)
        }
        updatePipParams()
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onWindowInsetsChanged(left, top, right, bottom)
        videoAdapter.setInsets(
            left,
            top,
            right,
            bottom
        )
        mediaFlowStoreView.onInsetsChanged(left, top, right, bottom)
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
        if (displayData.isEmpty()) return
        val current = currentPosition()
        // position 来自 holder 的 bindingAdapterPosition，是 displayData 索引
        val endPosition = if (position < 0) current else position
        // 如果结束的视频不是当前显示的视频（用户已滑走），则不自动切换
        if (endPosition != current) return
        // 视频播放结束，自动播放下一个
        playNext()
    }

    override fun onPlaybackModeChanged(mode: PlaybackMode) {
        videoManager.onPlaybackModeChanged(mode)
        reorderPlaylist()
    }

    override fun onGestureControlChanged(enabled: Boolean) {
        lastHolder?.setGestureControlEnabled(enabled)
    }

    override fun onFullscreenToggleClick() {
        isFullscreen = !isFullscreen
        updateFullscreen()
        lastHolder?.updateOrientationState(isFullscreen || currentOrientation == Orientation.LANDSCAPE)
    }

    override fun onEnterPipMode() {
        if (!PIPHelper.isSupported(this) || PIPHelper.isInPictureInPictureMode(this)) {
            return
        }
        enterPictureInPictureMode()
    }

    private class PlayAdapter(
        private val videoList: List<MediaInfo.File>
    ) : RecyclerView.Adapter<VideoPlayHolder>() {

        companion object {
            /** 随机模式无缝推进：只更新数据，不重新显示全屏预览图 */
            const val PAYLOAD_SEAMLESS = "payload_seamless"
        }

        private var layoutInflater: LayoutInflater? = null

        private var insets = Rect()

        @SuppressLint("NotifyDataSetChanged")
        fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
            if (left != insets.left || top != insets.top || right != insets.right || bottom != insets.bottom) {
                insets.set(left, top, right, bottom)
                notifyDataSetChanged()
            }
        }

        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): VideoPlayHolder {
            return VideoPlayHolder.create(getLayoutInflater(parent), parent)
        }

        override fun onBindViewHolder(
            holder: VideoPlayHolder,
            position: Int
        ) {
            holder.onBind(videoList[position])
            holder.onInsetsChanged(insets.left, insets.top, insets.right, insets.bottom)
        }

        override fun onBindViewHolder(
            holder: VideoPlayHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_SEAMLESS)) {
                holder.onBindSeamless(videoList[position])
            } else {
                holder.onBind(videoList[position])
            }
            holder.onInsetsChanged(insets.left, insets.top, insets.right, insets.bottom)
        }

        override fun getItemCount(): Int {
            // 取消虚拟无限循环，返回真实显示列表大小
            // 顺序模式：完整列表；随机模式：2 项（当前 + 预计算下一个）
            return videoList.size
        }

    }

}
