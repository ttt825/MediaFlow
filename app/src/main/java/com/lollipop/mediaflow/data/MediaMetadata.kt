package com.lollipop.mediaflow.data

class MediaMetadata(
    val docId: String,
    val width: Int,
    val height: Int,
    val duration: Long,
    val rotation: Int,
    val lastModified: Long,
) {

    companion object {
        fun formatDuration(duration: Long): String {
            if (duration <= 0) {
                return ""
            }
            var seconds = duration / 1000
            val minutes = seconds / 60
            seconds %= 60

            val builder = StringBuilder()

            if (minutes < 10) {
                builder.append("0")
            }
            builder.append(minutes)
            builder.append(":")
            if (seconds < 10) {
                builder.append("0")
            }
            builder.append(seconds)
            return builder.toString()
        }

        fun fromVideo(
            docId: String,
            width: Int,
            height: Int,
            duration: Long,
            lastModified: Long,
        ): MediaMetadata {
            return MediaMetadata(
                docId = docId,
                width = width,
                height = height,
                duration = duration,
                lastModified = lastModified,
                rotation = 0,
            )
        }

        fun fromImage(
            docId: String,
            width: Int,
            height: Int,
            rotation: Int,
            lastModified: Long,
        ): MediaMetadata {
            return MediaMetadata(
                docId = docId,
                width = width,
                height = height,
                duration = 0,
                rotation = rotation,
                lastModified = lastModified,
            )
        }

    }

    val durationFormat: String by lazy {
        formatDuration(duration)
    }

    val sizeFormat: String by lazy {
        "$width × $height"
    }

    fun formatFileSize(fileSize: Long): String {
        if (fileSize <= 0) return ""
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            fileSize >= gb -> String.format("%.1f GB", fileSize / gb)
            fileSize >= mb -> String.format("%.1f MB", fileSize / mb)
            fileSize >= kb -> String.format("%.1f KB", fileSize / kb)
            else -> "$fileSize B"
        }
    }

    val needRotate: Boolean = rotation == 90 || rotation == 270

}