package com.github.gbassi.timetracker.service

import com.github.gbassi.timetracker.model.BranchEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Adds to the entry the time elapsed in the [from, to) interval, splitting it
 * by calendar day in the [zone] time zone (handles the midnight rollover).
 * Pure function over the entry's data: testable without the platform.
 */
fun accrue(entry: BranchEntry, from: Instant, to: Instant, zone: ZoneId = ZoneId.systemDefault()) {
    if (!to.isAfter(from)) return
    var cursor = from
    while (cursor.isBefore(to)) {
        val day = cursor.atZone(zone).toLocalDate()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val segmentEnd = if (nextMidnight.isBefore(to)) nextMidnight else to
        val secs = Duration.between(cursor, segmentEnd).seconds
        if (secs > 0) entry.bucket(day.toString()).seconds += secs
        cursor = segmentEnd
    }
}
