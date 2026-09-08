package com.github.gbassi.timetracker.git

import com.github.gbassi.timetracker.service.TimeTrackerService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches the project's Git repositories. Git emits no "branch created" event,
 * so we detect the *checkout*: the first time a branch is worked on its entry is
 * created; on a branch switch the previous entry is paused.
 */
class BranchWatcher(private val project: Project) : GitRepositoryChangeListener {

    private val log = thisLogger()
    private val lastBranchByRepo = ConcurrentHashMap<String, String>()

    override fun repositoryChanged(repository: GitRepository) {
        val currentBranch = repository.currentBranchName ?: return // detached HEAD: ignore
        val repoPath = repository.root.path

        val previous = lastBranchByRepo.put(repoPath, currentBranch)
        if (previous == currentBranch) return

        val service = TimeTrackerService.getInstance()
        service.ensureStarted()

        if (previous != null && service.autoPauseOnBranchSwitch) {
            service.pauseIfActive(repoPath, previous)
        }

        val entryId = service.findOrCreate(project.name, repoPath, currentBranch)
        log.debug("Active branch for $repoPath: $currentBranch (previous: $previous)")

        if (previous != null && service.autoStartOnCheckout) {
            service.start(entryId)
        }
    }
}
