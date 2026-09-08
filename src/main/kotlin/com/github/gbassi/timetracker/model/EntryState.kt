package com.github.gbassi.timetracker.model

enum class EntryState {
    /** Never started, or explicitly stopped. */
    STOPPED,

    /** Timer running: accruing time. */
    RUNNING,

    /** Started in the past but currently suspended. */
    PAUSED,
}
