package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.core.graphics.Insets
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.PlaybackMode
import com.lollipop.mediaflow.databinding.ActivityFlowBinding
import com.lollipop.mediaflow.tools.Preferences

abstract class BasicFlowActivity : CustomOrientationActivity() {

    private val basicBinding by lazy {
        ActivityFlowBinding.inflate(layoutInflater)
    }

    protected var isFullscreen = false

    protected var isDecorationShown = true
        private set

    protected var endGuideSize = 0

    protected open val showPlayModeBtn = true

    protected open val showGestureBtn = true

    private val backBtnVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.backBtn)
    }

    protected val menuBarVisibleFilter by lazy {
        VisibleFilterGroup.Or(basicBinding.menuBar)
    }

    private val menuBtnVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.menuBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val playModeBtnVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.playModeBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val gestureBtnVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.gestureBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    protected var isGestureControlEnabled = Preferences.isGestureControlEnabled.get()
        private set

    protected var playbackMode = Preferences.playbackMode.get()
        private set

    private val titleVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.titleView)
    }

    private val tagVisibleFilter by lazy {
        PreferenceVisibleFilter(basicBinding.tagGroup)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(basicBinding.root)
        initInsetsListener()
        bindDrawerListener()
        basicBinding.drawerPanel.addView(
            createDrawerPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        basicBinding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        basicBinding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        basicBinding.playModeBtn.setOnClickListener {
            changePlaybackMode()
        }
        basicBinding.gestureBtn.setOnClickListener {
            toggleGestureControl()
        }
        updatePlayModeBtn()
        updateGestureBtn()
        basicBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        updateBlur()
    }

    override fun onResume() {
        super.onResume()
        playbackMode = Preferences.playbackMode.get()
        updatePlayModeBtn()
        backBtnVisibleFilter.preference.setVisible(Preferences.isShowBackBtn.get())
        menuBtnVisibleFilter.preference.setVisible(Preferences.isShowDrawerBtn.get())
        playModeBtnVisibleFilter.preference.setVisible(showPlayModeBtn && Preferences.isShowPlayModeBtn.get())
        gestureBtnVisibleFilter.preference.setVisible(showGestureBtn && Preferences.isShowGestureBtn.get())
        if (!showGestureBtn || !Preferences.isShowGestureBtn.get()) {
            isGestureControlEnabled = false
            Preferences.isGestureControlEnabled.set(false)
        } else {
            isGestureControlEnabled = Preferences.isGestureControlEnabled.get()
        }
        updateGestureBtn()
        titleVisibleFilter.preference.setVisible(Preferences.isShowTitle.get())
        tagVisibleFilter.preference.setVisible(Preferences.isShowTag.get())
    }

    override fun filterGuidelineInsets(insets: Insets): Insets {
        endGuideSize = insets.right
        return super.filterGuidelineInsets(insets)
    }

    @CallSuper
    override fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
    }

    protected fun updateFullscreen() {
        if (isFullscreen) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            hideSystemUI()
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            showSystemUI()
        }
    }

    private fun changePlaybackMode() {
        playbackMode = playbackMode.next()
        Preferences.playbackMode.set(playbackMode)
        updatePlayModeBtn()
        onPlaybackModeChanged(playbackMode)
    }

    private fun updatePlayModeBtn() {
        basicBinding.playModeBtn.setImageResource(
            when (playbackMode) {
                PlaybackMode.Sequential -> R.drawable.play_arrow_24
                PlaybackMode.Shuffle -> R.drawable.shuffle_24
            }
        )
    }

    private fun toggleGestureControl() {
        isGestureControlEnabled = !isGestureControlEnabled
        Preferences.isGestureControlEnabled.set(isGestureControlEnabled)
        updateGestureBtn()
        onGestureControlChanged(isGestureControlEnabled)
    }

    private fun updateGestureBtn() {
        basicBinding.gestureBtn.setImageResource(
            if (isGestureControlEnabled) R.drawable.gesture_24 else R.drawable.gesture_off_24
        )
    }

    protected open fun onPlaybackModeChanged(mode: PlaybackMode) {

    }

    protected open fun onGestureControlChanged(enabled: Boolean) {

    }

    protected fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
        basicBinding.decorationPanel.animate().cancel()
        if (isVisibility) {
            basicBinding.decorationPanel.alpha = 0F
            basicBinding.decorationPanel.visibility = View.VISIBLE
            basicBinding.decorationPanel.animate()
                .alpha(1F)
                .setDuration(250L)
                .start()
        } else {
            basicBinding.decorationPanel.animate()
                .alpha(0F)
                .setDuration(200L)
                .withEndAction { basicBinding.decorationPanel.visibility = View.GONE }
                .start()
        }
    }

    private fun bindDrawerListener() {
        basicBinding.menuBtn.setOnClickListener {
            changeDrawerState(true)
        }
        basicBinding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerOpened(drawerView: View) {
                onDrawerChanged(true)
            }

            override fun onDrawerClosed(drawerView: View) {
                onDrawerChanged(false)
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })
    }

    protected fun updateTitle(
        titleValue: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        basicBinding.root.post {
            basicBinding.titleView.text = titleValue
            basicBinding.sizeTagView.text = size
            basicBinding.formatTagView.text = format
            basicBinding.durationTagView.text = duration
            titleVisibleFilter.base.setVisible(titleValue.isNotEmpty())
            basicBinding.sizeTagView.isVisible = size.isNotEmpty()
            basicBinding.formatTagView.isVisible = format.isNotEmpty()
            basicBinding.durationTagView.isVisible = duration.isNotEmpty()
        }
        log.i("updateTitle: $titleValue, $size, $format, $duration")
        if (titleValue.isEmpty()) {
            log.e("updateTitle: isEmpty", RuntimeException())
        }
    }

    private fun initInsetsListener() {
        initInsetsListener(basicBinding.root)
        bindGuidelineInsets(
            leftGuideline = basicBinding.startGuideLine,
            topGuideline = basicBinding.topGuideLine,
            rightGuideline = basicBinding.endGuideLine,
            bottomGuideline = basicBinding.bottomGuideLine,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
        updateFullscreen()
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            basicBinding.blurTarget,
            basicBinding.menuBarBlur,
            basicBinding.backBtnBlur,
        )
    }

    protected fun changeDrawerState(isOpen: Boolean) {
        if (isOpen) {
            basicBinding.drawerLayout.openDrawer(basicBinding.drawerPanel)
        } else {
            basicBinding.drawerLayout.closeDrawer(basicBinding.drawerPanel)
        }
    }

    protected val isDrawerOpen: Boolean
        get() = basicBinding.drawerLayout.isDrawerOpen(basicBinding.drawerPanel)

    protected abstract fun onDrawerChanged(isOpen: Boolean)

    protected abstract fun createDrawerPanel(): View

    protected abstract fun createContentPanel(): View

}
