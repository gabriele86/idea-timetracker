package com.github.gbassi.timetracker.ui

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.github.gbassi.timetracker.service.TimeTrackerService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class TimeTrackerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        TimeTrackerService.getInstance().ensureStarted()

        val contentFactory = toolWindow.contentManager.factory
        val branch = BranchTablePanel(project, toolWindow.disposable)
        val summary = DailySummaryPanel(toolWindow.disposable)

        toolWindow.contentManager.addContent(
            contentFactory.createContent(branch, message("tab.branches"), false),
        )
        toolWindow.contentManager.addContent(
            contentFactory.createContent(summary, message("tab.summary"), false),
        )
    }
}
