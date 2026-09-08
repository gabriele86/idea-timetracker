package com.github.gbassi.timetracker.util

/** `H:MM:SS`, e.g. `0:07:03` or `12:30:00`. */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return "%d:%02d:%02d".format(h, m, sec)
}

/** Compact, human-readable form, e.g. `2h 05m`, `45m`, `0m`. */
fun formatHuman(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
}
