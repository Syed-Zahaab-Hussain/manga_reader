package com.example.mangareader.core.format

object TimeAgo {

    fun format(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val elapsedMinutes = (nowMs - timestampMs).coerceAtLeast(0) / MILLIS_PER_MINUTE
        return when {
            elapsedMinutes < 1 -> "just now"
            elapsedMinutes < MINUTES_PER_HOUR -> "${elapsedMinutes}m ago"
            elapsedMinutes < MINUTES_PER_DAY -> "${elapsedMinutes / MINUTES_PER_HOUR}h ago"
            elapsedMinutes < MINUTES_PER_WEEK -> "${elapsedMinutes / MINUTES_PER_DAY}d ago"
            else -> "${elapsedMinutes / MINUTES_PER_WEEK}w ago"
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
    private const val MINUTES_PER_WEEK = 7L * MINUTES_PER_DAY
}
