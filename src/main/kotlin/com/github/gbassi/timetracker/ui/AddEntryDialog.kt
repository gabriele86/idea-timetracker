package com.github.gbassi.timetracker.ui

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import javax.swing.JComponent
import javax.swing.JPanel

/** Manual creation of a tracking entry. */
class AddEntryDialog(private val project: Project) : DialogWrapper(project) {

    data class Result(val projectName: String, val repoPath: String, val branchName: String)

    private val repos: List<GitRepository> = GitRepositoryManager.getInstance(project).repositories
    private val repoCombo = ComboBox(repos.map { it.root.path }.toTypedArray())
    private val branchCombo = ComboBox<String>().apply { isEditable = true }
    private val projectField = JBTextField(project.name)

    var result: Result? = null
        private set

    init {
        title = message("dialog.add.title")
        repoCombo.addActionListener { refreshBranches() }
        refreshBranches()
        init()
    }

    private fun refreshBranches() {
        branchCombo.removeAllItems()
        val repo = repos.getOrNull(repoCombo.selectedIndex) ?: return
        repo.branches.localBranches
            .map { it.name }
            .sorted()
            .forEach { branchCombo.addItem(it) }
        repo.currentBranchName?.let { branchCombo.selectedItem = it }
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        if (repos.isNotEmpty()) {
            builder.addLabeledComponent(message("dialog.add.repository"), repoCombo)
        }
        builder.addLabeledComponent(message("dialog.add.branch"), branchCombo)
        builder.addLabeledComponent(message("dialog.add.project"), projectField)
        return builder.panel.also { (it as JPanel).preferredSize = it.preferredSize.apply { width = 420 } }
    }

    override fun doValidate(): ValidationInfo? {
        if (branchName().isBlank()) return ValidationInfo(message("dialog.add.error.branch"), branchCombo)
        if (repoPath().isBlank()) return ValidationInfo(message("dialog.add.error.repo"), repoCombo)
        return null
    }

    override fun doOKAction() {
        result = Result(projectField.text.trim().ifBlank { project.name }, repoPath(), branchName())
        super.doOKAction()
    }

    private fun branchName(): String = (branchCombo.editor.item?.toString() ?: "").trim()

    private fun repoPath(): String =
        repos.getOrNull(repoCombo.selectedIndex)?.root?.path
            ?: repos.firstOrNull()?.root?.path
            ?: ""
}
