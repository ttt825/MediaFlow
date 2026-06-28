package com.lollipop.mediaflow.data.duplicate

import com.lollipop.mediaflow.data.MediaInfo

/**
 * 重复视频分组结果。
 */
data class DuplicateGroup(
    val index: Int,
    val files: MutableList<MediaInfo.File>
)
