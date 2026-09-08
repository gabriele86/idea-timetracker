package com.github.gbassi.timetracker.model

import com.intellij.util.xmlb.annotations.XCollection

/** Persisted plugin state (application level). */
class TrackerState {
    @get:XCollection(style = XCollection.Style.v2)
    var entries: MutableList<BranchEntry> = mutableListOf()

    /** Id of the entry currently RUNNING, or `null`. At most one at a time. */
    var activeEntryId: String? = null

    // --- Settings ---
    var idleTimeoutMinutes: Int = 5
    var autoResumeAfterIdle: Boolean = true
    var autoStartOnCheckout: Boolean = false
    var autoPauseOnBranchSwitch: Boolean = true
}
