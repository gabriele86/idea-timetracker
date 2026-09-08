package com.github.gbassi.timetracker.ui

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.github.gbassi.timetracker.model.EntryState
import com.github.gbassi.timetracker.service.EntryView
import com.github.gbassi.timetracker.service.TimeTrackerListener
import com.github.gbassi.timetracker.service.TimeTrackerService
import com.github.gbassi.timetracker.util.formatClock
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

private const val COL_PROJECT = 0
private const val COL_BRANCH = 1
private const val COL_TODAY = 2
private const val COL_TOTAL = 3
private const val COL_STATE = 4

class BranchTablePanel(
    private val project: Project,
    parent: Disposable,
) : SimpleToolWindowPanel(true, true) {

    private val service get() = TimeTrackerService.getInstance()
    private val model = EntriesTableModel()
    private val table = JBTable(model).apply {
        setShowGrid(false)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.text = message("table.empty")
    }

    init {
        table.columnModel.getColumn(COL_TODAY).cellRenderer = ClockRenderer { it.todaySeconds }
        table.columnModel.getColumn(COL_TOTAL).cellRenderer = ClockRenderer { it.totalSeconds }
        table.columnModel.getColumn(COL_STATE).cellRenderer = StateRenderer()
        table.columnModel.getColumn(COL_TODAY).maxWidth = 90
        table.columnModel.getColumn(COL_TOTAL).maxWidth = 90
        table.columnModel.getColumn(COL_STATE).maxWidth = 90

        toolbar = buildToolbar()
        setContent(ScrollPaneFactory.createScrollPane(table))

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                selectedId()?.let { service.toggle(it) }
                return true
            }
        }.installOn(table)

        ApplicationManager.getApplication().messageBus.connect(parent)
            .subscribe(TimeTrackerListener.TOPIC, TimeTrackerListener {
                ApplicationManager.getApplication().invokeLater(::reload, ModalityState.any())
            })

        reload()
    }

    private fun selectedRow(): EntryView? =
        table.selectedRow.takeIf { it >= 0 }?.let { model.rows[table.convertRowIndexToModel(it)] }

    private fun selectedId(): String? = selectedRow()?.id

    private fun reload() {
        val keepId = selectedId()
        model.rows = service.entriesView()
        model.fireTableDataChanged()
        if (keepId != null) {
            val idx = model.rows.indexOfFirst { it.id == keepId }
            if (idx >= 0) table.selectionModel.setSelectionInterval(idx, idx)
        }
    }

    private fun buildToolbar(): javax.swing.JComponent {
        val group = DefaultActionGroup()

        group.add(object : AnAction(
            message("action.toggle.text"),
            message("action.toggle.description"),
            AllIcons.Actions.Execute,
        ) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                val row = selectedRow()
                e.presentation.isEnabled = row != null
                val running = row?.state == EntryState.RUNNING
                e.presentation.icon = if (running) AllIcons.Actions.Pause else AllIcons.Actions.Execute
                e.presentation.text =
                    if (running) message("action.toggle.pause") else message("action.toggle.start")
            }
            override fun actionPerformed(e: AnActionEvent) { selectedId()?.let { service.toggle(it) } }
        })

        group.add(object : AnAction(
            message("action.stop.text"),
            message("action.stop.description"),
            AllIcons.Actions.Suspend,
        ) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = selectedRow()?.state != null && selectedRow()?.state != EntryState.STOPPED
            }
            override fun actionPerformed(e: AnActionEvent) { selectedId()?.let { service.stop(it) } }
        })

        group.addSeparator()

        group.add(object : AnAction(
            message("action.add.text"),
            message("action.add.description"),
            AllIcons.General.Add,
        ) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) {
                val dialog = AddEntryDialog(project)
                if (dialog.showAndGet()) {
                    val r = dialog.result ?: return
                    service.findOrCreate(r.projectName, r.repoPath, r.branchName)
                }
            }
        })

        group.add(object : AnAction(
            message("action.delete.text"),
            message("action.delete.description"),
            AllIcons.General.Remove,
        ) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedId() != null }
            override fun actionPerformed(e: AnActionEvent) {
                val id = selectedId() ?: return
                val row = model.rows.firstOrNull { it.id == id } ?: return
                val ok = Messages.showYesNoDialog(
                    project,
                    message("delete.confirm.message", row.projectName, row.branchName),
                    message("delete.confirm.title"),
                    Messages.getWarningIcon(),
                )
                if (ok == Messages.YES) service.delete(id)
            }
        })

        group.addSeparator()

        group.add(object : AnAction(
            message("action.refresh.text"),
            message("action.refresh.description"),
            AllIcons.Actions.Refresh,
        ) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) = reload()
        })

        val toolbar = ActionManager.getInstance().createActionToolbar("BranchTimeTracker", group, true)
        toolbar.targetComponent = table
        return toolbar.component
    }

    // ------------------------------------------------------------------- model

    private class EntriesTableModel : AbstractTableModel() {
        var rows: List<EntryView> = emptyList()

        private val names = arrayOf(
            message("table.column.project"),
            message("table.column.branch"),
            message("table.column.today"),
            message("table.column.total"),
            message("table.column.state"),
        )

        override fun getRowCount() = rows.size
        override fun getColumnCount() = names.size
        override fun getColumnName(column: Int) = names[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val r = rows[rowIndex]
            return when (columnIndex) {
                COL_PROJECT -> r.projectName
                COL_BRANCH -> r.branchName
                COL_TODAY -> r.todaySeconds
                COL_TOTAL -> r.totalSeconds
                COL_STATE -> r.state
                else -> ""
            }
        }
    }

    private inner class ClockRenderer(private val pick: (EntryView) -> Long) : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(t: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            val entry = model.rows.getOrNull(table.convertRowIndexToModel(row)) ?: return
            val attrs = if (entry.state == EntryState.RUNNING) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            else SimpleTextAttributes.REGULAR_ATTRIBUTES
            append(formatClock(pick(entry)), attrs)
        }
    }

    private inner class StateRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(t: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            when (value as? EntryState) {
                EntryState.RUNNING -> { icon = AllIcons.Actions.Execute; append(message("state.running")) }
                EntryState.PAUSED -> { icon = AllIcons.Actions.Pause; append(message("state.paused")) }
                EntryState.STOPPED -> { icon = AllIcons.Actions.Suspend; append(message("state.stopped")) }
                null -> {}
            }
        }
    }
}
