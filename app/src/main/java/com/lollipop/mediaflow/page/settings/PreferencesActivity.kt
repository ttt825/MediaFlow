package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.R
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

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val activity = this
        var playbackSpeed by remember { mutableFloatStateOf(Preferences.playbackSpeed.get()) }
        var defaultVideoSpeed by remember { mutableFloatStateOf(Preferences.defaultVideoSpeed.get()) }
        var videoTouchSeekBaseWeight by remember { mutableFloatStateOf(Preferences.videoTouchSeekBaseWeight.get()) }
        var gestureSideRegionRatio by remember { mutableFloatStateOf(Preferences.gestureSideRegionRatio.get()) }
        val isBlurVideoBackground by remember { Preferences.isBlurVideoBackground.state }
        val isShowDrawerBtn by remember { Preferences.isShowDrawerBtn.state }
        val isShowPlayModeBtn by remember { Preferences.isShowPlayModeBtn.state }
        val isShowGestureBtn by remember { Preferences.isShowGestureBtn.state }
        val isShowPipBtn by remember { Preferences.isShowPipBtn.state }
        val isShowBackBtn by remember { Preferences.isShowBackBtn.state }
        val isShowTitle by remember { Preferences.isShowTitle.state }
        val isShowTag by remember { Preferences.isShowTag.state }
        val isPictureInPictureEnable by remember { Preferences.isPictureInPictureEnable.state }
        val isPipPrevEnable by remember { Preferences.isPipPrevEnable.state }
        val isPipPlayEnable by remember { Preferences.isPipPlayEnable.state }
        val isPipNextEnable by remember { Preferences.isPipNextEnable.state }

        ContentColumn(
            innerPadding = innerPadding,
            showBack = true,
            title = "偏好设置"
        ) {

            PreferencesGroup {
                PreferencesSlide(
                    valueRange = Preferences.playbackSpeedRange,
                    value = defaultVideoSpeed,
                    steps = getSteps(Preferences.playbackSpeedRange, 0.01F),
                    nameProvider = {
                        stringResource(id = R.string.label_default_video_speed, percentage(it))
                    },
                    onValueChangeFinished = {
                        defaultVideoSpeed = it
                        Preferences.defaultVideoSpeed.set(it)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    valueRange = Preferences.playbackSpeedRange,
                    value = playbackSpeed,
                    steps = getSteps(Preferences.playbackSpeedRange, 0.01F),
                    nameProvider = {
                        stringResource(id = R.string.label_long_press_playback_speed, percentage(it))
                    },
                    onValueChangeFinished = {
                        playbackSpeed = it
                        Preferences.playbackSpeed.set(it)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    valueRange = Preferences.videoTouchSeekBaseWeightRange,
                    value = videoTouchSeekBaseWeight,
                    steps = getSteps(Preferences.videoTouchSeekBaseWeightRange, 0.01F),
                    nameProvider = {
                        stringResource(id = R.string.label_video_touch_seek_base_weight, percentage(it))
                    },
                    onValueChangeFinished = {
                        videoTouchSeekBaseWeight = it
                        Preferences.videoTouchSeekBaseWeight.set(it)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    valueRange = Preferences.gestureSideRegionRatioRange,
                    value = gestureSideRegionRatio,
                    steps = getSteps(Preferences.gestureSideRegionRatioRange, 0.01F),
                    nameProvider = {
                        stringResource(id = R.string.label_gesture_side_region_ratio, percentage(it))
                    },
                    onValueChangeFinished = {
                        gestureSideRegionRatio = it
                        Preferences.gestureSideRegionRatio.set(it)
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
                    name = stringResource(id = R.string.label_picture_in_picture_enable),
                    summary = stringResource(id = R.string.summary_picture_in_picture_enable),
                    isChecked = isPictureInPictureEnable
                ) {
                    Preferences.isPictureInPictureEnable.set(it)
                }

                AnimatedVisibility(visible = isPictureInPictureEnable) {
                    Column {
                        PreferencesDivider()

                        PreferencesSwitch(
                            name = stringResource(id = R.string.label_play_is_show_pip_button),
                            summary = stringResource(id = R.string.summary_play_is_show_pip_button),
                            isChecked = isShowPipBtn
                        ) {
                            Preferences.isShowPipBtn.set(it)
                        }

                        PreferencesDivider()

                        PreferencesSwitch(
                            name = stringResource(id = R.string.label_pip_button_skip_previous_enable),
                            summary = stringResource(id = R.string.summary_pip_button_skip_previous_enable),
                            isChecked = isPipPrevEnable
                        ) {
                            Preferences.isPipPrevEnable.set(it)
                        }

                        PreferencesDivider()

                        PreferencesSwitch(
                            name = stringResource(id = R.string.label_pip_button_play_enable),
                            summary = stringResource(id = R.string.summary_pip_button_play_enable),
                            isChecked = isPipPlayEnable
                        ) {
                            Preferences.isPipPlayEnable.set(it)
                        }

                        PreferencesDivider()

                        PreferencesSwitch(
                            name = stringResource(id = R.string.label_pip_button_skip_next_enable),
                            summary = stringResource(id = R.string.summary_pip_button_skip_next_enable),
                            isChecked = isPipNextEnable
                        ) {
                            Preferences.isPipNextEnable.set(it)
                        }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .toggleable(
                    value = isChecked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1F)
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    contentDescription = name
                }
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
                .minimumInteractiveComponentSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1F)
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun ColumnScope.PreferencesSlide(
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        value: Float,
        nameProvider: @Composable (value: Float) -> String,
        onValueChangeFinished: (value: Float) -> Unit
    ) {
        var localValue by remember { mutableFloatStateOf(value) }
        LaunchedEffect(value) { localValue = value }
        val name = nameProvider(localValue)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = name
                    },
                value = localValue,
                onValueChange = { localValue = it },
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = {
                    onValueChangeFinished(localValue)
                },
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
        return ((range.endInclusive - range.start) / stepLength).toInt() - 1
    }

}