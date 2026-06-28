package com.lollipop.mediaflow.data.duplicate

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.lollipop.mediaflow.data.MediaDatabase
import com.lollipop.mediaflow.data.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 视频重复检测器。
 *
 * 采用“分级筛选 + 内容感知哈希”策略：
 * 1. 快速元信息过滤（时长、宽高比）剔除明显不同的视频；
 * 2. 仅在同一时长桶内进行帧哈希两两比对；
 * 3. 缓存每部视频的内容签名，避免每次全量重算；
 * 4. 通过灰度降采样 dHash 实现清晰度无关的比对。
 */
class VideoDuplicateDetector(
    private val context: Context,
    private val database: MediaDatabase,
    private val maxParallelExtractor: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
    private val onProgress: suspend (current: Int, total: Int, stage: Stage) -> Unit = { _, _, _ -> }
) {

    enum class Stage {
        EXTRACTING_SIGNATURES,
        COMPARING,
        FINISHED
    }

    private val signatureCache = mutableMapOf<String, VideoSignature?>()

    /**
     * 主入口：对 [files] 进行重复分组。
     */
    suspend fun findDuplicates(files: List<MediaInfo.File>): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        if (files.size < 2) return@withContext emptyList<DuplicateGroup>()

        // 0. 预过滤：仅对“至少存在另一个视频与其时长接近”的文件提取签名。
        //    大量唯一时长的视频可直接排除，避免不必要的帧解码，显著提升性能。
        val candidateFiles = filterDurationCandidates(files)
        if (candidateFiles.size < 2) return@withContext emptyList<DuplicateGroup>()

        // 1. 提取/加载签名（并行、受控并发）
        val signatures = extractSignatures(candidateFiles)
        if (!isActive) return@withContext emptyList<DuplicateGroup>()

        // 2. 按时长排序后使用滑动窗口两两比对，仅当两部视频时长接近时才进入精确比对
        val groups = findDuplicateClusters(signatures)

        groups
            .filter { it.size >= 2 }
            .sortedByDescending { it.size }
            .mapIndexed { index, cluster ->
                DuplicateGroup(
                    index = index + 1,
                    files = cluster.map { it.file }.toMutableList()
                )
            }
    }

    /**
     * 基于已加载的 [MediaMetadata.duration] 进行快速预过滤。
     * 返回“存在至少一个时长邻居”的视频列表。
     */
    private fun filterDurationCandidates(files: List<MediaInfo.File>): List<MediaInfo.File> {
        val withDuration = files
            .mapNotNull { file ->
                val duration = file.metadata?.duration ?: return@mapNotNull null
                if (duration <= 0) return@mapNotNull null
                file to duration
            }
            .sortedBy { it.second }

        val n = withDuration.size
        if (n < 2) return emptyList()

        val isCandidate = BooleanArray(n)
        var j = 1
        for (i in 0 until n - 1) {
            val (fileA, durationA) = withDuration[i]
            if (j <= i) j = i + 1
            while (j < n) {
                val (fileB, durationB) = withDuration[j]
                val maxDuration = max(durationA, durationB)
                val tolerance = max(
                    VideoSignature.DURATION_ABS_TOLERANCE_MS,
                    (maxDuration * VideoSignature.DURATION_REL_TOLERANCE).toLong()
                )
                if (durationB - durationA > tolerance) {
                    // 后续视频时长只会更大，无需继续
                    break
                }
                // 找到一个在容差范围内的邻居
                if (durationB - durationA <= tolerance) {
                    isCandidate[i] = true
                    isCandidate[j] = true
                }
                j++
            }
        }

        return withDuration.mapIndexedNotNull { index, (file, _) ->
            if (isCandidate[index]) file else null
        }
    }

    /**
     * 提取所有视频的内容签名。优先读取数据库缓存，缓存失效或不存在时重新解码。
     */
    private suspend fun extractSignatures(files: List<MediaInfo.File>): List<SignatureWrapper> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(maxParallelExtractor)
        val total = files.size

        files.mapIndexed { index, file ->
            async {
                semaphore.withPermit {
                    val signature = loadOrExtractSignature(file)
                    signatureCache[file.docId] = signature
                    onProgress(index + 1, total, Stage.EXTRACTING_SIGNATURES)
                    SignatureWrapper(file, signature)
                }
            }
        }.awaitAll().filter { it.signature != null }
    }

    /**
     * 加载缓存签名；若缓存不存在或文件已修改，则重新提取并回写缓存。
     */
    private fun loadOrExtractSignature(file: MediaInfo.File): VideoSignature? {
        val cached = database.findVideoSignature(file.docId)
        if (cached != null && cached.lastModified == file.lastModified && cached.version == VideoSignature.CURRENT_VERSION) {
            return cached
        }
        return extractSignature(file)?.also {
            database.updateVideoSignature(it)
        }
    }

    /**
     * 使用 [MediaMetadataRetriever] 抽取关键帧并计算感知哈希。
     */
    private fun extractSignature(file: MediaInfo.File): VideoSignature? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return null
            if (duration <= 0) return null

            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: file.metadata?.width ?: 0
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: file.metadata?.height ?: 0

            val hashes = LongArray(VideoSignature.FRAME_COUNT)
            var failureCount = 0

            for (i in 0 until VideoSignature.FRAME_COUNT) {
                val position = VideoSignature.FRAME_POSITIONS[i]
                val timeUs = (duration * position * 1000).toLong()
                val frame = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame == null || frame.isRecycled) {
                    failureCount++
                    continue
                }
                try {
                    hashes[i] = computeDHash(frame)
                } finally {
                    frame.recycle()
                }
            }

            // 如果超过一半帧提取失败，认为该视频无法生成可靠签名
            if (failureCount > VideoSignature.FRAME_COUNT / 2) return null

            return VideoSignature(
                docId = file.docId,
                lastModified = file.lastModified,
                durationMs = duration,
                width = width,
                height = height,
                frameHashes = hashes
            )
        } catch (e: Throwable) {
            return null
        } finally {
            retriever.release()
        }
    }

    /**
     * 计算单帧 dHash（差异哈希）。
     *
     * 步骤：
     * 1. 缩放到 9x8 像素；
     * 2. 转灰度；
     * 3. 每个像素与右侧像素比较，大者为 1，小者为 0；
     * 4. 得到 64 bit 哈希值。
     *
     * dHash 对亮度、轻微压缩、缩放具有良好不变性，因此适合跨清晰度比对。
     */
    private fun computeDHash(source: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(source, 9, 8, false)
        val grayscale = IntArray(72) // 9 * 8
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val pixel = scaled.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                grayscale[y * 9 + x] = ((r * 30 + g * 59 + b * 11) / 100)
            }
        }
        scaled.recycle()

        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = grayscale[y * 9 + x]
                val right = grayscale[y * 9 + x + 1]
                if (left > right) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    /**
     * 在时长滑动窗口内执行两两比对，并通过并查集合并重复视频的连通分量。
     *
     * 先将视频按时长排序，仅对时长差在容差范围内的视频对进行精确比对，
     * 避免跨度过大的视频对进入昂贵的帧哈希比较。
     */
    private suspend fun findDuplicateClusters(
        wrappers: List<SignatureWrapper>
    ): List<List<SignatureWrapper>> = withContext(Dispatchers.Default) {
        val sorted = wrappers.sortedBy { it.signature!!.durationMs }
        val totalPairsEstimate = (sorted.size * (sorted.size - 1L) / 2).toInt()
        var comparedPairs = 0

        val parent = mutableMapOf<String, String>()
        val wrappersById = mutableMapOf<String, SignatureWrapper>()

        fun find(id: String): String {
            var root = id
            while (parent[root] != root) {
                root = parent[root]!!
            }
            var cur = id
            while (parent[cur] != root) {
                val next = parent[cur]!!
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) {
                parent[rb] = ra
            }
        }

        for (wrapper in sorted) {
            parent[wrapper.file.docId] = wrapper.file.docId
            wrappersById[wrapper.file.docId] = wrapper
        }

        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            var j = i + 1
            while (j < sorted.size) {
                val b = sorted[j]
                // 列表已按时长升序排列，因此 b 的时长一定大于等于 a
                val durationDiff = b.signature!!.durationMs - a.signature!!.durationMs
                val maxDuration = b.signature.durationMs
                val durationTolerance = max(
                    VideoSignature.DURATION_ABS_TOLERANCE_MS,
                    (maxDuration * VideoSignature.DURATION_REL_TOLERANCE).toLong()
                )
                if (durationDiff > durationTolerance) {
                    // 后续视频时长只会更大，直接跳出内层循环
                    break
                }

                comparedPairs++
                if (areSignaturesDuplicate(a.signature!!, b.signature)) {
                    union(a.file.docId, b.file.docId)
                }
                if (comparedPairs % 50 == 0) {
                    onProgress(comparedPairs, totalPairsEstimate, Stage.COMPARING)
                }
                j++
            }
        }
        onProgress(totalPairsEstimate, totalPairsEstimate, Stage.COMPARING)

        return@withContext wrappersById.values
            .groupBy { find(it.file.docId) }
            .values
            .toList()
    }

    /**
     * 判断两个视频签名是否代表同一视频。
     */
    private fun areSignaturesDuplicate(a: VideoSignature, b: VideoSignature): Boolean {
        // 1. 时长过滤
        val maxDuration = max(a.durationMs, b.durationMs)
        val durationDiff = abs(a.durationMs - b.durationMs)
        val durationTolerance = max(
            VideoSignature.DURATION_ABS_TOLERANCE_MS,
            (maxDuration * VideoSignature.DURATION_REL_TOLERANCE).toLong()
        )
        if (durationDiff > durationTolerance) return false

        // 2. 宽高比过滤（允许轻微黑边/裁剪差异）
        if (a.aspectRatio > 0 && b.aspectRatio > 0) {
            val ratioDiff = abs(a.aspectRatio - b.aspectRatio) / max(a.aspectRatio, b.aspectRatio)
            if (ratioDiff > VideoSignature.ASPECT_RATIO_TOLERANCE) return false
        }

        // 3. 帧哈希序列比对
        val minFrames = min(a.frameHashes.size, b.frameHashes.size)
        if (minFrames == 0) return false

        var matchCount = 0
        for (i in 0 until minFrames) {
            val distance = hammingDistance(a.frameHashes[i], b.frameHashes[i])
            if (distance <= VideoSignature.FRAME_MATCH_THRESHOLD) {
                matchCount++
            }
        }

        val ratio = matchCount.toFloat() / minFrames
        return if (maxDuration < 5000L) {
            // 短视频要求更严格：所有可用帧必须匹配
            ratio >= 1.0F
        } else {
            ratio >= VideoSignature.MATCH_RATIO_THRESHOLD
        }
    }

    /**
     * 计算两个 64 bit 值的汉明距离。
     */
    private fun hammingDistance(a: Long, b: Long): Int {
        var xor = a xor b
        var count = 0
        while (xor != 0L) {
            count++
            xor = xor and (xor - 1)
        }
        return count
    }

    private data class SignatureWrapper(
        val file: MediaInfo.File,
        val signature: VideoSignature?
    )
}
