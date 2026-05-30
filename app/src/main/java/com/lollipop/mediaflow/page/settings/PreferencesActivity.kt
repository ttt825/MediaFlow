package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor


class PreferencesActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PreferencesActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    private fun percentage(float: Float): String {
        return "${(float * 100).toInt()}%"
    }

    private val log by lazy {
        registerLog()
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val activity = this
        var playbackSpeed by remember { mutableFloatStateOf(Preferences.playbackSpeed.get()) }
        var defaultVideoSpeed by remember { mutableFloatStateOf(Preferences.defaultVideoSpeed.get()) }
        var videoTouchSeekBaseWeight by remember { mutableFloatStateOf(Preferences.videoTouchSeekBaseWeight.get()) }
        var gestureSideRegionRatio by remember { mutableFloatStateOf(Preferences.gestureSideRegionRatio.get()) }
        val isBlurVideoBackground by remember { Preferences.isBlurVideoBackground.state }
        var playbackSpeedValue by remember { mutableStateOf(percentage(playbackSpeed)) }
        var defaultVideoSpeedValue by remember { mutableStateOf(percentage(defaultVideoSpeed)) }
        var videoTouchSeekBaseWeightValue by remember {
            mutableStateOf(
                percentage(
                    videoTouchSeekBaseWeight
                )
            )
        }
        var gestureSideRegionRatioValue by remember {
            mutableStateOf(percentage(gestureSideRegionRatio))
        }
        val isShowDrawerBtn by remember { Preferences.isShowDrawerBtn.state }
        val isShowPlayModeBtn by remember { Preferences.isShowPlayModeBtn.state }
        val isShowGestureBtn by remember { Preferences.isShowGestureBtn.state }
        val isShowBackBtn by remember { Preferences.isShowBackBtn.state }
        val isShowTitle by remember { Preferences.isShowTitle.state }
        val isShowTag by remember { Preferences.isShowTag.state }

        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {

            PreferencesGroup {
                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_default_video_speed,
                        defaultVideoSpeedValue
                    ),
                    valueRange = Preferences.playbackSpeedRange,
                    value = defaultVideoSpeed,
                    steps = getSteps(Preferences.playbackSpeedRange, 0.01F),
                    onValueChange = {
                        defaultVideoSpeed = it
                        defaultVideoSpeedValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.defaultVideoSpeed.set(defaultVideoSpeed)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_long_press_playback_speed,
                        playbackSpeedValue
                    ),
                    valueRange = Preferences.playbackSpeedRange,
                    value = playbackSpeed,
                    // (4.0 - 0.5) / 0.1 - 1 = 34
                    steps = getSteps(Preferences.playbackSpeedRange, 0.01F),
                    onValueChange = {
                        playbackSpeed = it
                        playbackSpeedValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.playbackSpeed.set(playbackSpeed)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_video_touch_seek_base_weight,
                        videoTouchSeekBaseWeightValue
                    ),
                    valueRange = Preferences.videoTouchSeekBaseWeightRange,
                    value = videoTouchSeekBaseWeight,
                    // (1.2 - 0.3) / 0.1 - 1 = 8
                    steps = getSteps(Preferences.videoTouchSeekBaseWeightRange, 0.01F),
                    onValueChange = {
                        videoTouchSeekBaseWeight = it
                        videoTouchSeekBaseWeightValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.videoTouchSeekBaseWeight.set(videoTouchSeekBaseWeight)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_gesture_side_region_ratio,
                        gestureSideRegionRatioValue
                    ),
                    valueRange = Preferences.gestureSideRegionRatioRange,
                    value = gestureSideRegionRatio,
                    steps = getSteps(Preferences.gestureSideRegionRatioRange, 0.01F),
                    onValueChange = {
                        gestureSideRegionRatio = it
                        gestureSideRegionRatioValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.gestureSideRegionRatio.set(gestureSideRegionRatio)
                    }
                )
            }

            PreferencesGroup {
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_back_button),
                    summary = stringResource(id = R.string.summary_play_is_show_back_button),
                    isChecked = isShowBackBtn
                ) {
                    Preferences.isShowBackBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_title),
                    summary = stringResource(id = R.string.summary_play_is_show_title),
                    isChecked = isShowTitle
                ) {
                    Preferences.isShowTitle.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_tag),
                    summary = stringResource(id = R.string.summary_play_is_show_tag),
                    isChecked = isShowTag
                ) {
                    Preferences.isShowTag.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_drawer_button),
                    summary = stringResource(id = R.string.summary_play_is_show_drawer_button),
                    isChecked = isShowDrawerBtn
                ) {
                    Preferences.isShowDrawerBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_play_mode_button),
                    summary = stringResource(id = R.string.summary_play_is_show_play_mode_button),
                    isChecked = isShowPlayModeBtn
                ) {
                    Preferences.isShowPlayModeBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_gesture_button),
                    summary = stringResource(id = R.string.summary_play_is_show_gesture_button),
                    isChecked = isShowGestureBtn
                ) {
                    Preferences.isShowGestureBtn.set(it)
                    if (!it) {
                        Preferences.isGestureControlEnabled.set(false)
                    }
                }
            }

            PreferencesGroup {

                PreferencesSwitch(
                    name = stringResource(id = R.string.label_video_blur),
                    summary = stringResource(id = R.string.summary_video_blur),
                    isChecked = isBlurVideoBackground
                ) {
                    Preferences.isBlurVideoBackground.set(it)
                }

                PreferencesDivider()

                PreferencesIntent(
                    name = stringResource(id = R.string.label_custom_video_suffix),
                    summary = stringResource(id = R.string.summary_custom_video_suffix)
                ) {
                    CustomSuffixActivity.start(activity)
                }
            }

            item {
                Text(
                    text = BuildConfig.VERSION_NAME,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }

    private fun LazyListScope.PreferencesGroup(
        content: @Composable ColumnScope.() -> Unit
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(color = currentThemeColor().preferencesGroup),
                content = content,
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }

    @Composable
    private fun PreferencesDivider() {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
    }

    @Composable
    private fun ColumnScope.PreferencesSwitch(
        name: String,
        summary: String,
        isChecked: Boolean,
        onCheckedChange: (isCheck: Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1F)
            ) {
                Text(
                    text = name,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp
                )
                Text(
                    text = summary,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
            )
        }
    }

    @Composable
    private fun ColumnScope.PreferencesIntent(
        name: String,
        summary: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1F)
            ) {
                Text(
                    text = name,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp
                )
                Text(
                    text = summary,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null
            )
        }
    }

    @Composable
    private fun ColumnScope.PreferencesSlide(
        name: String,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        value: Float,
        onValueChange: (value: Float) -> Unit,
        onValueChangeFinished: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = name,
                color = currentThemeColor().buttonText,
                fontSize = 16.sp
            )
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    }

    private fun getSteps(range: ClosedFloatingPointRange<Float>, stepLength: Float): Int {
        // (1.0 - 0.1) / 0.1 - 1 = 8
        return ((range.endInclusive - range.start) / stepLength).toInt() - 1
    }

}