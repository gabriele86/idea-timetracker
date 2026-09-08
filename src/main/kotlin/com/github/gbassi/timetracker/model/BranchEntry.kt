package com.github.gbassi.timetracker.model

import com.intellij.util.xmlb.annotations.XCollection
import java.util.UUID

/** Time accrued on a single calendar day (date in ISO `yyyy-MM-dd` format). */
class DayBucket() {
    var date: String = ""
    var seconds: Long = 0

    constructor(date: String, seconds: Long) : this() {
        this.date = date
        this.seconds = seconds
    }
}

/**
 * A tracking entry: identifies a branch of a repository of a project.
 * The logical key is the [repoPath] + [branchName] pair.
 */
class BranchEntry {
    var id: String = UUID.randomUUID().toString()
    var projectName: String = ""
    var repoPath: String = ""
    var branchName: String = ""
    var createdAtEpoch: Long = System.currentTimeMillis()
    var state: EntryState = EntryState.STOPPED

    @get:XCollection(style = XCollection.Style.v2)
    var days: MutableList<DayBucket> = mutableListOf()

    fun secondsOn(isoDate: String): Long = days.firstOrNull { it.date == isoDate }?.seconds ?: 0

    fun totalSeconds(): Long = days.sumOf { it.seconds }

    fun bucket(isoDate: String): DayBucket =
        days.firstOrNull { it.date == isoDate } ?: DayBucket(isoDate, 0).also { days.add(it) }
}
