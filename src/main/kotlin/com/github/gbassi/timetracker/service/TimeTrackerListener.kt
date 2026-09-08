package com.github.gbassi.timetracker.service

import com.intellij.util.messages.Topic

/** Notifies the UI that the tracker state changed (timer tick included). */
fun interface TimeTrackerListener {
    fun stateChanged()

    companion object {
        @JvmField
        val TOPIC: Topic<TimeTrackerListener> =
            Topic.create("Branch Time Tracker state", TimeTrackerListener::class.java)
    }
}
