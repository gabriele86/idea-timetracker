package com.github.gbassi.timetracker.listeners

import com.github.gbassi.timetracker.service.TimeTrackerService
import com.intellij.ide.AppLifecycleListener

/** Stops the active timer when the IDE shuts down. */
class AppCloseListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        TimeTrackerService.getInstance().stopActiveOnShutdown()
    }
}
