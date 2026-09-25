package cc.opencar.assistant.feature.dvr

/**
 * Pure wall-clock / media-offset helpers for DVR play + cut (unit-tested).
 */
object DvrTimelineMath {

    data class Segment(
        val startUtcMs: Long,
        val endUtcMs: Long,
        val durationMs: Long,
    )

    data class PlaySnap(
        val startUtcMs: Long,
        val endUtcMs: Long,
        val durationMs: Long,
        val wallUtcMs: Long,
        val offsetMs: Long,
        val index: Int,
    )

    /** Media [from,to) inside a segment for a wall-clock cut range. */
    fun mediaRangeForCut(
        segStartUtcMs: Long,
        segDurationMs: Long,
        fromUtcMs: Long,
        toUtcMs: Long,
    ): Pair<Long, Long> {
        val mediaFrom = (fromUtcMs - segStartUtcMs).coerceAtLeast(0L)
        val mediaTo = (toUtcMs - segStartUtcMs)
            .coerceAtMost(segDurationMs)
            .coerceAtLeast(mediaFrom + 1L)
        return mediaFrom to mediaTo
    }

    /**
     * Snap [atUtcMs] into [segs] (sorted by start). Returns null if empty.
     */
    fun resolvePlayAt(segs: List<Segment>, atUtcMs: Long): PlaySnap? {
        if (segs.isEmpty()) return null
        val containing = segs.indexOfFirst { atUtcMs >= it.startUtcMs && atUtcMs < it.endUtcMs }
        if (containing >= 0) {
            val s = segs[containing]
            val wall = atUtcMs.coerceIn(s.startUtcMs, (s.endUtcMs - 1).coerceAtLeast(s.startUtcMs))
            val offset = (wall - s.startUtcMs).coerceIn(0L, (s.durationMs - 1).coerceAtLeast(0L))
            return PlaySnap(s.startUtcMs, s.endUtcMs, s.durationMs, wall, offset, containing)
        }
        var bestIdx = 0
        var bestDist = Long.MAX_VALUE
        var bestWall = segs[0].startUtcMs
        for (i in segs.indices) {
            val s = segs[i]
            val dStart = kotlin.math.abs(atUtcMs - s.startUtcMs)
            val dEnd = kotlin.math.abs(atUtcMs - (s.endUtcMs - 1))
            if (dStart < bestDist) {
                bestDist = dStart
                bestIdx = i
                bestWall = s.startUtcMs
            }
            if (dEnd < bestDist) {
                bestDist = dEnd
                bestIdx = i
                bestWall = s.endUtcMs - 1
            }
        }
        val s = segs[bestIdx]
        val offset = (bestWall - s.startUtcMs).coerceIn(0L, (s.durationMs - 1).coerceAtLeast(0L))
        return PlaySnap(s.startUtcMs, s.endUtcMs, s.durationMs, bestWall, offset, bestIdx)
    }
}
