package com.github.gbassi.timetracker.service

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.github.gbassi.timetracker.model.BranchEntry
import com.github.gbassi.timetracker.model.EntryState
import com.github.gbassi.timetracker.model.TrackerState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// --- Immutable DTOs for the UI ---

data class EntryView(
    val id: String,
    val projectName: String,
    val repoPath: String,
    val branchName: String,
    val state: EntryState,
    val todaySeconds: Long,
    val totalSeconds: Long,
    val active: Boolean,
)

data class SummaryRow(val projectName: String, val branchName: String, val seconds: Long)

data class DailySummary(
    val date: LocalDate,
    val totalSeconds: Long,
    val rows: List<SummaryRow>,
) {
    /** Per-project totals, sorted by descending time. */
    fun byProject(): List<Pair<String, Long>> =
        rows.groupBy { it.projectName }
            .map { (p, r) -> p to r.sumOf { it.seconds } }
            .sortedByDescending { it.second }
}

@Service(Service.Level.APP)
@State(name = "BranchTimeTrackerState", storages = [Storage("branchTimeTracker.xml")])
class TimeTrackerService : PersistentStateComponent<TrackerState>, Disposable {

    private val log = thisLogger()
    private val lock = Any()

    private var state = TrackerState()

    /** Instant of the active entry's last accrual; `null` if nothing is RUNNING. */
    private var runningSince: Instant? = null

    /** Entry paused by inactivity, candidate for auto-resume. */
    private var pausedByIdleId: String? = null

    @Volatile
    private var lastActivityMs: Long = System.currentTimeMillis()

    private val started = AtomicBoolean(false)
    private var ticker: ScheduledFuture<*>? = null
    private var awtListener: AWTEventListener? = null

    // ---------------------------------------------------------------- lifecycle

    /** Idempotent: starts the ticker and user-activity tracking. */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        val listener = AWTEventListener { lastActivityMs = System.currentTimeMillis() }
        awtListener = listener
        Toolkit.getDefaultToolkit().addAWTEventListener(
            listener,
            AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
        )

        ticker = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::tick, 1, 1, TimeUnit.SECONDS)
    }

    override fun dispose() {
        ticker?.cancel(false)
        awtListener?.let { Toolkit.getDefaultToolkit().removeAWTEventListener(it) }
        synchronized(lock) { flushRunning() }
    }

    // ---------------------------------------------------------------- persistence

    override fun getState(): TrackerState {
        synchronized(lock) { flushRunning() }
        return state
    }

    override fun loadState(loaded: TrackerState) {
        synchronized(lock) {
            state = loaded
            // After an IDE restart nothing is actually running.
            runningSince = null
            pausedByIdleId = null
            state.activeEntryId = null
            state.entries.forEach { if (it.state == EntryState.RUNNING) it.state = EntryState.PAUSED }
        }
    }

    // ---------------------------------------------------------------- settings

    var idleTimeoutMinutes: Int
        get() = state.idleTimeoutMinutes
        set(v) { synchronized(lock) { state.idleTimeoutMinutes = v.coerceIn(1, 600) } }

    var autoResumeAfterIdle: Boolean
        get() = state.autoResumeAfterIdle
        set(v) { synchronized(lock) { state.autoResumeAfterIdle = v } }

    var autoStartOnCheckout: Boolean
        get() = state.autoStartOnCheckout
        set(v) { synchronized(lock) { state.autoStartOnCheckout = v } }

    var autoPauseOnBranchSwitch: Boolean
        get() = state.autoPauseOnBranchSwitch
        set(v) { synchronized(lock) { state.autoPauseOnBranchSwitch = v } }

    // ---------------------------------------------------------------- commands

    fun findOrCreate(projectName: String, repoPath: String, branchName: String): String {
        val id = synchronized(lock) {
            val existing = state.entries.firstOrNull { it.repoPath == repoPath && it.branchName == branchName }
            if (existing != null) {
                if (existing.projectName != projectName && projectName.isNotBlank()) {
                    existing.projectName = projectName
                }
                existing.id
            } else {
                val entry = BranchEntry().apply {
                    this.projectName = projectName
                    this.repoPath = repoPath
                    this.branchName = branchName
                }
                state.entries.add(entry)
                entry.id
            }
        }
        publish()
        return id
    }

    fun start(entryId: String) {
        synchronized(lock) {
            pausedByIdleId = null
            startInternal(entryId)
        }
        publish()
    }

    fun pause(entryId: String) {
        synchronized(lock) {
            pausedByIdleId = null
            val entry = byId(entryId) ?: return@synchronized
            if (state.activeEntryId == entryId) {
                flushRunning()
                runningSince = null
                state.activeEntryId = null
            }
            entry.state = EntryState.PAUSED
        }
        publish()
    }

    fun stop(entryId: String) {
        synchronized(lock) {
            pausedByIdleId = null
            val entry = byId(entryId) ?: return@synchronized
            if (state.activeEntryId == entryId) {
                flushRunning()
                runningSince = null
                state.activeEntryId = null
            }
            entry.state = EntryState.STOPPED
        }
        publish()
    }

    fun toggle(entryId: String) {
        val running = synchronized(lock) { byId(entryId)?.state == EntryState.RUNNING }
        if (running) pause(entryId) else start(entryId)
    }

    fun delete(entryId: String) {
        synchronized(lock) {
            if (state.activeEntryId == entryId) {
                flushRunning()
                runningSince = null
                state.activeEntryId = null
            }
            if (pausedByIdleId == entryId) pausedByIdleId = null
            state.entries.removeIf { it.id == entryId }
        }
        publish()
    }

    /** Pauses the entry for the given repo/branch if it is the active one (branch switch). */
    fun pauseIfActive(repoPath: String, branchName: String) {
        val id = synchronized(lock) {
            state.entries.firstOrNull { it.repoPath == repoPath && it.branchName == branchName }?.id
        } ?: return
        pause(id)
    }

    /** On IDE shutdown: stops the active entry so overnight hours are not counted. */
    fun stopActiveOnShutdown() {
        val id = synchronized(lock) { state.activeEntryId } ?: return
        stop(id)
    }

    // ---------------------------------------------------------------- query

    fun config(): TrackerState = state

    fun entriesView(): List<EntryView> = synchronized(lock) {
        flushRunning()
        val today = LocalDate.now().toString()
        state.entries
            .sortedWith(compareBy({ it.projectName.lowercase() }, { it.branchName.lowercase() }))
            .map {
                EntryView(
                    id = it.id,
                    projectName = it.projectName,
                    repoPath = it.repoPath,
                    branchName = it.branchName,
                    state = it.state,
                    todaySeconds = it.secondsOn(today),
                    totalSeconds = it.totalSeconds(),
                    active = it.id == state.activeEntryId,
                )
            }
    }

    fun summaryFor(date: LocalDate): DailySummary = synchronized(lock) {
        flushRunning()
        val key = date.toString()
        val rows = state.entries.mapNotNull { e ->
            val s = e.secondsOn(key)
            if (s > 0) SummaryRow(e.projectName.ifBlank { message("summary.noProject") }, e.branchName, s) else null
        }.sortedWith(compareByDescending { it.seconds })
        DailySummary(date, rows.sumOf { it.seconds }, rows)
    }

    /** Total for each of the last [days] days (most recent first). */
    fun recentTotals(days: Int = 7): List<Pair<LocalDate, Long>> = synchronized(lock) {
        flushRunning()
        val today = LocalDate.now()
        (0 until days).map { offset ->
            val d = today.minusDays(offset.toLong())
            val key = d.toString()
            d to state.entries.sumOf { it.secondsOn(key) }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun startInternal(entryId: String) {
        val entry = byId(entryId) ?: return
        val currentId = state.activeEntryId
        if (currentId != null && currentId != entryId) {
            flushRunning()
            byId(currentId)?.state = EntryState.PAUSED
        }
        runningSince = Instant.now()
        state.activeEntryId = entryId
        entry.state = EntryState.RUNNING
        ensureStarted()
    }

    private fun byId(id: String): BranchEntry? = state.entries.firstOrNull { it.id == id }

    /** Accrues the time elapsed since the last tick on the active entry. Call while holding the lock. */
    private fun flushRunning() {
        val since = runningSince ?: return
        val entry = state.activeEntryId?.let { byId(it) } ?: run {
            runningSince = null
            return
        }
        val now = Instant.now()
        accrue(entry, since, now, ZoneId.systemDefault())
        runningSince = now
    }

    private fun tick() {
        var changed = false
        try {
            synchronized(lock) {
                flushRunning()
                changed = checkIdleTransitions()
            }
        } catch (t: Throwable) {
            log.warn("Time tracker tick failed", t)
        }
        // Publish anyway: the UI refreshes its counters every second while something is RUNNING.
        if (changed || state.activeEntryId != null) publish()
    }

    /** @return true if an idle→pause or activity→resume transition happened. */
    private fun checkIdleTransitions(): Boolean {
        val idleMs = System.currentTimeMillis() - lastActivityMs
        val timeoutMs = state.idleTimeoutMinutes * 60_000L
        val activeId = state.activeEntryId

        if (activeId != null && idleMs >= timeoutMs) {
            val entry = byId(activeId) ?: return false
            flushRunning()
            runningSince = null
            state.activeEntryId = null
            entry.state = EntryState.PAUSED
            pausedByIdleId = activeId
            log.info("Time tracker paused due to inactivity: ${entry.branchName}")
            return true
        }

        val resumeId = pausedByIdleId
        if (resumeId != null && idleMs < 2_000) {
            pausedByIdleId = null
            val entry = byId(resumeId) ?: return false
            if (state.autoResumeAfterIdle && entry.state == EntryState.PAUSED && state.activeEntryId == null) {
                startInternal(resumeId)
                log.info("Time tracker resumed after inactivity: ${entry.branchName}")
                return true
            }
        }
        return false
    }

    private fun publish() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(TimeTrackerListener.TOPIC)
            .stateChanged()
    }

    companion object {
        fun getInstance(): TimeTrackerService = service()
    }
}
