package com.lollipop.mediaflow.data

enum class PlaybackMode(val key: String) {

    Sequential(key = "sequential"),
    Shuffle(key = "shuffle");

    fun next(): PlaybackMode {
        return when (this) {
            Sequential -> Shuffle
            Shuffle -> Sequential
        }
    }

    companion object {
        fun findByKey(key: String): PlaybackMode {
            return entries.find { it.key == key } ?: Sequential
        }
    }

}
