package com.github.gbassi.timetracker.listeners

import com.github.gbassi.timetracker.service.TimeTrackerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Starts the ticker and activity tracking as soon as a project opens. */
class TrackerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        TimeTrackerService.getInstance().ensureStarted()
    }
}
