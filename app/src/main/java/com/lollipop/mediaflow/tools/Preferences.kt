package com.lollipop.mediaflow.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.data.PlaybackMode
import org.json.JSONArray

object Preferences {

    fun init(context: Context) {
        PreferencesDelegate.init(context)
    }

    /**
     * 播放速度的范围
     */
    val playbackSpeedRange = 0.3F..4F

    /**
     * 手势变化进度时的基础倍率范围
     */
    val videoTouchSeekBaseWeightRange = 0.3F..1.2F

    /**
     * 纵向手势范围权重范围
     */
    val videoTouchMaxRangeRatioYRange = 0.1F..1F

    /**
     * 手势操作区域（亮度/音量）屏幕占比范围
     */
    val gestureSideRegionRatioRange = 0.05F..0.35F

    /**
     * 手指手势变化进度时的基础倍率
     */
    val videoTouchSeekBaseWeight by lazy {
        FloatItem(name = "videoTouchSeekBaseWeight", 0.3F)
    }

    /**
     * 倍速播放时候的速度（长按屏幕时的倍速）
     */
    val playbackSpeed by lazy {
        FloatItem(name = "playbackSpeed", 2F)
    }

    /**
     * 默认视频播放速度
     */
    val defaultVideoSpeed by lazy {
        FloatItem(name = "defaultVideoSpeed", 1.25F)
    }

    /**
     * 纵向手势范围权重
     */
    val videoTouchMaxRangeRatioY by lazy {
        FloatItem(name = "videoTouchMaxRangeRatioY", 0.5F)
    }

    /**
     * 手势操作区域（亮度/音量）屏幕占比
     */
    val gestureSideRegionRatio by lazy {
        FloatItem(name = "gestureSideRegionRatio", 0.2F)
    }

    /**
     * 将视频背景渲染为同色的高斯模糊版本
     */
    val isBlurVideoBackground by lazy {
        BooleanItem(name = "isBlurVideoBackground", true)
    }

    /**
     * 是否显示播放列表按钮
     */
    val isShowDrawerBtn by lazy {
        BooleanItem(name = "isShowDrawerBtn", true)
    }

    val isShowPlayModeBtn by lazy {
        BooleanItem(name = "isShowPlayModeBtn", true)
    }

    val isShowGestureBtn by lazy {
        BooleanItem(name = "isShowGestureBtn", true)
    }

    val isShowPipBtn by lazy {
        BooleanItem(name = "isShowPipBtn", true)
    }

    /**
     * 是否开启画中画模式
     */
    val isPictureInPictureEnable by lazy {
        BooleanItem(name = "isPictureInPictureEnable", true)
    }

    /**
     * 画中画模式下是否开启上一曲按钮
     */
    val isPipPrevEnable by lazy {
        BooleanItem(name = "isPipPrevEnable", true)
    }

    /**
     * 画中画模式下是否开启下一曲按钮
     */
    val isPipNextEnable by lazy {
        BooleanItem(name = "isPipNextEnable", true)
    }

    /**
     * 画中画模式下是否开启播放按钮
     */
    val isPipPlayEnable by lazy {
        BooleanItem(name = "isPipPlayEnable", true)
    }

    val isGestureControlEnabled by lazy {
        BooleanItem(name = "isGestureControlEnabled", true)
    }

    val playbackMode by lazy {
        PlaybackModeItem(name = "playbackMode", PlaybackMode.Sequential)
    }

    /**
     * 是否显示返回按钮
     */
    val isShowBackBtn by lazy {
        BooleanItem(name = "isShowBackBtn", true)
    }

    /**
     * 是否显示标题栏
     */
    val isShowTitle by lazy {
        BooleanItem(name = "isShowTitle", true)
    }

    /**
     * 是否显示标签栏
     */
    val isShowTag by lazy {
        BooleanItem(name = "isShowTag", true)
    }

    val customVideoSuffixes by lazy {
        StringSetItem(name = "customVideoSuffixes")
    }

    val shuffleSeed by lazy {
        LongItem(name = "shuffleSeed", System.currentTimeMillis())
    }

    /**
     * 公开视频排序
     */
    val publicVideoSort by lazy {
        MediaSortItem(name = "publicVideoSort", MediaSort.DateDesc)
    }

    /**
     * 公开图片排序
     */
    val publicPhotoSort by lazy {
        MediaSortItem(name = "publicPhotoSort", MediaSort.DateDesc)
    }

    /**
     * 私有视频排序
     */
    val privateVideoSort by lazy {
        MediaSortItem(name = "privateVideoSort", MediaSort.DateDesc)
    }

    /**
     * 私有图片排序
     */
    val privatePhotoSort by lazy {
        MediaSortItem(name = "privatePhotoSort", MediaSort.DateDesc)
    }

    abstract class TypedItem<T> {

        protected val stateImpl by lazy {
            mutableStateOf(getPreferencesValue())
        }

        val state: State<T>
            get() {
                return stateImpl
            }

        fun get(): T {
            return state.value
        }

        fun set(value: T) {
            this.stateImpl.value = value
            setPreferencesValue(value)
        }

        protected abstract fun getPreferencesValue(): T

        protected abstract fun setPreferencesValue(value: T)

    }

    class MediaSortItem(
        val name: String,
        val def: MediaSort
    ) : TypedItem<MediaSort>() {
        override fun getPreferencesValue(): MediaSort {
            val key = PreferencesDelegate.get(name = name, def = def.key)
            return MediaSort.findByKey(key) ?: def
        }

        override fun setPreferencesValue(value: MediaSort) {
            PreferencesDelegate.set(name = name, value = value.key)
        }

    }

    class PlaybackModeItem(
        val name: String,
        val def: PlaybackMode
    ) : TypedItem<PlaybackMode>() {
        override fun getPreferencesValue(): PlaybackMode {
            val key = PreferencesDelegate.get(name = name, def = def.key)
            return PlaybackMode.findByKey(key)
        }

        override fun setPreferencesValue(value: PlaybackMode) {
            PreferencesDelegate.set(name = name, value = value.key)
        }

    }

    class StringItem(
        val name: String,
        val def: String
    ) : TypedItem<String>() {
        override fun getPreferencesValue(): String {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: String) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class BooleanItem(
        val name: String,
        val def: Boolean
    ) : TypedItem<Boolean>() {
        override fun getPreferencesValue(): Boolean {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Boolean) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class IntItem(
        val name: String,
        val def: Int
    ) : TypedItem<Int>() {
        override fun getPreferencesValue(): Int {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Int) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class LongItem(
        val name: String,
        val def: Long
    ) : TypedItem<Long>() {
        override fun getPreferencesValue(): Long {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Long) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class FloatItem(
        val name: String,
        val def: Float
    ) : TypedItem<Float>() {
        override fun getPreferencesValue(): Float {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Float) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class StringSetItem(
        val name: String
    ) : TypedItem<Set<String>>() {

        override fun getPreferencesValue(): Set<String> {
            val json = PreferencesDelegate.get(name = name, def = "")
            if (json.isEmpty()) return emptySet()
            return try {
                val array = JSONArray(json)
                val set = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    set.add(array.getString(i))
                }
                set
            } catch (e: Exception) {
                emptySet()
            }
        }

        override fun setPreferencesValue(value: Set<String>) {
            val array = JSONArray()
            value.forEach { array.put(it) }
            PreferencesDelegate.set(name = name, value = array.toString())
        }

        fun add(suffix: String) {
            val current = get().toMutableSet()
            val normalized = suffix.trim().lowercase().removePrefix(".")
            if (normalized.isNotEmpty()) {
                current.add(normalized)
                set(current)
            }
        }

        fun remove(suffix: String) {
            val current = get().toMutableSet()
            current.remove(suffix)
            set(current)
        }

    }

    private object PreferencesDelegate {
        private const val PREFERENCES_NAME = "MediaFlow"

        private var preferences: SharedPreferences? = null

        fun init(context: Context) {
            preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        fun get(name: String, def: Int = 0): Int {
            return preferences?.getInt(name, def) ?: return def
        }

        fun set(name: String, value: Int) {
            preferences?.edit {
                putInt(name, value)
            }
        }

        fun get(name: String, def: Boolean = false): Boolean {
            return preferences?.getBoolean(name, def) ?: return def
        }

        fun set(name: String, value: Boolean) {
            preferences?.edit {
                putBoolean(name, value)
            }
        }

        fun get(name: String, def: Long = 0): Long {
            return preferences?.getLong(name, def) ?: return def
        }

        fun set(name: String, value: Long) {
            preferences?.edit {
                putLong(name, value)
            }
        }

        fun get(name: String, def: String = ""): String {
            return preferences?.getString(name, def) ?: return def
        }

        fun set(name: String, value: String) {
            preferences?.edit {
                putString(name, value)
            }
        }

        fun get(name: String, def: Float = 0F): Float {
            return preferences?.getFloat(name, def) ?: return def
        }

        fun set(name: String, value: Float) {
            preferences?.edit {
                putFloat(name, value)
            }
        }
    }

}