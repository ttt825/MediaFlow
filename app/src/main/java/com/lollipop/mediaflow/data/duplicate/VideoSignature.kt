package com.lollipop.mediaflow.data.duplicate

/**
 * 视频内容签名，用于重复性判定。
 *
 * 该签名基于“关键帧感知哈希”，不依赖文件名、分辨率、码率等元信息，
 * 因此能够识别不同清晰度/编码的同一视频。
 */
data class VideoSignature(
    val docId: String,
    val lastModified: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameHashes: LongArray,
    val version: Int = CURRENT_VERSION
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * 采样帧数量。数量越多准确率越高，但提取耗时线性增加。
         * 5 帧在 500 视频规模下能较好平衡准确性与性能。
         */
        const val FRAME_COUNT = 5

        /**
         * 在视频时长上的归一化采样位置（0.0 ~ 1.0）。
         * 均匀分布可避开片头片尾黑屏/Logo 的干扰。
         */
        val FRAME_POSITIONS = floatArrayOf(0.10F, 0.30F, 0.50F, 0.70F, 0.90F)

        /**
         * 单帧 dHash 的位长。9x8 图像产生 8x8 = 64 bit。
         */
        const val HASH_BITS = 64

        /**
         * 判断两帧“视觉上相同”的汉明距离阈值。
         * 64 bit 中允许 4 bit 差异，可容忍压缩/缩放带来的轻微失真。
         */
        const val FRAME_MATCH_THRESHOLD = 4

        /**
         * 判定两个视频为重复所需的最少匹配帧比例。
         */
        const val MATCH_RATIO_THRESHOLD = 0.80F

        /**
         * 时长容差：绝对值容差与相对值容差取较大者。
         * 用于过滤明显不同内容的视频，减少无效比对。
         */
        const val DURATION_ABS_TOLERANCE_MS = 2000L
        const val DURATION_REL_TOLERANCE = 0.02F

        /**
         * 宽高比容差，允许裁剪、黑边、不同分辨率带来的轻微比例差异。
         */
        const val ASPECT_RATIO_TOLERANCE = 0.05F
    }

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 0F

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoSignature) return false
        return docId == other.docId
                && lastModified == other.lastModified
                && durationMs == other.durationMs
                && width == other.width
                && height == other.height
                && version == other.version
                && frameHashes.contentEquals(other.frameHashes)
    }

    override fun hashCode(): Int {
        var result = docId.hashCode()
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + version
        result = 31 * result + frameHashes.contentHashCode()
        return result
    }
}
