package com.github.gbassi.timetracker.ui

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.github.gbassi.timetracker.service.DailySummary
import com.github.gbassi.timetracker.service.TimeTrackerListener
import com.github.gbassi.timetracker.service.TimeTrackerService
import com.github.gbassi.timetracker.util.formatClock
import com.github.gbassi.timetracker.util.formatHuman
import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class DailySummaryPanel(parent: Disposable) : JPanel() {

    private val service get() = TimeTrackerService.getInstance()
    private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", DynamicBundle.getLocale())

    private var date: LocalDate = LocalDate.now()

    private val dateLabel = JBLabel().apply { font = font.deriveFont(font.size + 1f) }
    private val totalLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, font.size + 3f) }
    private val detailModel = DetailModel()
    private val recentModel = RecentModel()

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(8)

        val prev = JButton(AllIcons.Actions.Back).apply { addActionListener { shift(-1) } }
        val next = JButton(AllIcons.Actions.Forward).apply { addActionListener { shift(1) } }
        val today = JButton(message("summary.today")).apply { addActionListener { date = LocalDate.now(); refresh() } }
        val nav = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(prev); add(Box.createHorizontalStrut(4)); add(next)
            add(Box.createHorizontalStrut(8)); add(dateLabel)
            add(Box.createHorizontalGlue()); add(today)
        }

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(nav)
            add(Box.createVerticalStrut(8))
            add(leftAligned(totalLabel))
            add(Box.createVerticalStrut(8))
        }

        val detailTable = JBTable(detailModel).apply {
            setShowGrid(false)
            emptyText.text = message("summary.detail.empty")
            columnModel.getColumn(2).maxWidth = 90
        }
        val recentTable = JBTable(recentModel).apply {
            setShowGrid(false)
            columnModel.getColumn(1).maxWidth = 90
        }

        val center = JPanel(BorderLayout()).apply {
            add(ScrollPaneFactory.createScrollPane(detailTable), BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(12, 0, 0, 0)
                add(sectionLabel(message("summary.recent")), BorderLayout.NORTH)
                add(ScrollPaneFactory.createScrollPane(recentTable).apply {
                    preferredSize = Dimension(0, 150)
                }, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

        add(header, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)

        ApplicationManager.getApplication().messageBus.connect(parent)
            .subscribe(TimeTrackerListener.TOPIC, TimeTrackerListener {
                ApplicationManager.getApplication().invokeLater(::refresh, ModalityState.any())
            })

        refresh()
    }

    private fun shift(days: Int) { date = date.plusDays(days.toLong()); refresh() }

    private fun refresh() {
        val summary: DailySummary = service.summaryFor(date)
        dateLabel.text = date.format(dateFmt).replaceFirstChar { it.uppercase() }
        totalLabel.text = message("summary.total", formatHuman(summary.totalSeconds))
        detailModel.load(summary)
        recentModel.load(service.recentTotals(7))
    }

    private fun leftAligned(c: JBLabel): JPanel =
        JPanel(BorderLayout()).apply { add(c, BorderLayout.WEST); alignmentX = LEFT_ALIGNMENT }

    private fun sectionLabel(text: String) =
        JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

    // --- table models ---

    private class DetailModel : AbstractTableModel() {
        private data class Line(val project: String, val branch: String, val seconds: Long, val group: Boolean)
        private var lines: List<Line> = emptyList()
        private val names = arrayOf(
            message("summary.column.project"),
            message("summary.column.branch"),
            message("summary.column.time"),
        )

        fun load(summary: DailySummary) {
            val out = mutableListOf<Line>()
            for ((project, total) in summary.byProject()) {
                out += Line(project, "", total, group = true)
                summary.rows.filter { it.projectName == project }
                    .sortedByDescending { it.seconds }
                    .forEach { out += Line(project, it.branchName, it.seconds, group = false) }
            }
            lines = out
            fireTableDataChanged()
        }

        override fun getRowCount() = lines.size
        override fun getColumnCount() = names.size
        override fun getColumnName(column: Int) = names[column]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val l = lines[rowIndex]
            return when (columnIndex) {
                0 -> if (l.group) l.project else ""
                1 -> if (l.group) "" else "  ${l.branch}"
                2 -> formatClock(l.seconds)
                else -> ""
            }
        }
    }

    private class RecentModel : AbstractTableModel() {
        private var rows: List<Pair<LocalDate, Long>> = emptyList()
        private val fmt = DateTimeFormatter.ofPattern("EEE d MMM", DynamicBundle.getLocale())
        private val names = arrayOf(
            message("summary.column.day"),
            message("summary.column.time"),
        )

        fun load(data: List<Pair<LocalDate, Long>>) { rows = data; fireTableDataChanged() }

        override fun getRowCount() = rows.size
        override fun getColumnCount() = names.size
        override fun getColumnName(column: Int) = names[column]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val (d, secs) = rows[rowIndex]
            return if (columnIndex == 0) d.format(fmt).replaceFirstChar { it.uppercase() } else formatClock(secs)
        }
    }
}
