package com.lollipop.mediaflow.tools

import android.graphics.Matrix
import com.lollipop.mediaflow.ui.view.FlowPlayerGestureHost
import kotlin.math.absoluteValue
import kotlin.math.min

class VideoTouchHelper(
    private val baseWeight: Float,
    private val xThreshold: Float,
    private val yMaxRangeRatio: Float = 0.5F,
    private val minWeight: Float = 0.05F,
    private val sideRegionRatio: Float = 0.3F,
    private val videoController: VideoController
) : FlowPlayerGestureHost.OnFlowTouchListener {

    companion object {
        private const val SPEED_MODE_DELAY = 100L
        private const val GESTURE_THRESHOLD = 30F
    }

    private var lastX = 0F
    private var lastY = 0F
    private var isSeeking = false
    private var accumulatedTimeWeight = 0F
    private var yRangeSize = 100F

    private var isSpeedModePending = false
    private var isSpeedModeStarted = false

    private var isGestureVerticalMode = false
    private var gestureVerticalType = GestureVerticalType.None

    private val speedModeDelayTask = task {
        onSpeedModeDelayTimeout()
    }

    var gestureControlEnabled = false

    fun shouldInterceptVerticalSwipe(touchDownX: Float, viewWidth: Int): Boolean {
        if (!gestureControlEnabled) return false
        val ratio = touchDownX / viewWidth.toFloat()
        return ratio < sideRegionRatio || ratio > (1F - sideRegionRatio)
    }

    private fun getGestureVerticalType(touchDownX: Float, viewWidth: Int): GestureVerticalType {
        val ratio = touchDownX / viewWidth.toFloat()
        return if (ratio < sideRegionRatio) {
            GestureVerticalType.Brightness
        } else if (ratio > (1F - sideRegionRatio)) {
            GestureVerticalType.Volume
        } else {
            GestureVerticalType.None
        }
    }

    override fun onSingleCapture(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        lastX = currentX
        lastY = currentY
        isSeeking = false
        isSpeedModePending = true
        isSpeedModeStarted = false
        isGestureVerticalMode = false
        gestureVerticalType = GestureVerticalType.None
        accumulatedTimeWeight = 0F
        yRangeSize = min(viewWidth, viewHeight) * yMaxRangeRatio
        speedModeDelayTask.delayOnUI(SPEED_MODE_DELAY)
    }

    private fun onSpeedModeDelayTimeout() {
        if (isSpeedModePending && !isSeeking && !isGestureVerticalMode) {
            isSpeedModePending = false
            isSpeedModeStarted = true
            videoController.startPlaybackSpeed()
        }
    }

    private fun onSwitchToSeekMode() {
        if (isSpeedModeStarted) {
            videoController.stopPlaybackSpeed()
        }
        videoController.startSeekMode()
    }

    override fun onSingleMove(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        val absDx = (currentX - touchDownX).absoluteValue
        val absDy = (currentY - touchDownY).absoluteValue

        if (!isSeeking && !isGestureVerticalMode) {
            if (absDx > xThreshold) {
                isSeeking = true
                speedModeDelayTask.cancel()
                isSpeedModePending = false
                onSwitchToSeekMode()
            } else if (gestureControlEnabled && absDy > GESTURE_THRESHOLD && absDy > absDx) {
                val type = getGestureVerticalType(touchDownX, viewWidth)
                if (type != GestureVerticalType.None) {
                    isGestureVerticalMode = true
                    gestureVerticalType = type
                    speedModeDelayTask.cancel()
                    isSpeedModePending = false
                    videoController.onGestureVerticalBegin(gestureVerticalType)
                }
            }
        }

        if (isSeeking) {
            val deltaX = currentX - lastX
            lastX = currentX

            val dy = (currentY - touchDownY).absoluteValue
            val ratioY = (dy / yRangeSize).coerceIn(0f, 1.0f)
            val precision = 1.0f - (1.0f - minWeight) * ratioY

            val frameOffset = (deltaX / viewWidth.toFloat()) * baseWeight * precision
            accumulatedTimeWeight += frameOffset

            videoController.onSeek(accumulatedTimeWeight, precision)
        } else if (isGestureVerticalMode) {
            val deltaY = lastY - currentY
            lastY = currentY
            val deltaRatio = deltaY / viewHeight.toFloat()
            videoController.onGestureVerticalMove(gestureVerticalType, deltaRatio)
        }

        if (!isSeeking && !isGestureVerticalMode) {
            lastX = currentX
        }
        if (!isGestureVerticalMode) {
            lastY = currentY
        }
    }

    override fun onDoubleScale(matrix: Matrix) {
        videoController.onScaleGestureChanged(matrix)
    }

    override fun onTouchRelease() {
        speedModeDelayTask.cancel()
        if (isSeeking) {
            videoController.stopSeekMode(accumulatedTimeWeight)
        } else if (isSpeedModeStarted) {
            videoController.stopPlaybackSpeed()
        }
        if (isGestureVerticalMode) {
            videoController.onGestureVerticalEnd(gestureVerticalType)
        }
        isSeeking = false
        isSpeedModePending = false
        isSpeedModeStarted = false
        isGestureVerticalMode = false
        gestureVerticalType = GestureVerticalType.None
    }

    enum class GestureVerticalType {
        None,
        Brightness,
        Volume
    }

    interface VideoController {

        fun startPlaybackSpeed()

        fun stopPlaybackSpeed()

        fun startSeekMode()

        fun onSeek(weight: Float, precision: Float)

        fun stopSeekMode(weight: Float)

        fun onScaleGestureChanged(matrix: Matrix)

        fun onGestureVerticalBegin(type: GestureVerticalType)

        fun onGestureVerticalMove(type: GestureVerticalType, deltaRatio: Float)

        fun onGestureVerticalEnd(type: GestureVerticalType)
    }

}
