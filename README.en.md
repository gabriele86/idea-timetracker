# Branch Time Tracker

IntelliJ plugin that tracks the time you spend working on the **Git branches** of your projects,
across every open project in a single IDE-wide view.

## Features

- **Automatic entry on checkout**: the first time you work on a branch (checkout) a tracking entry
  is created. You can also add one by hand with the `+` button.
- **Start / Pause / Stop / Delete** tracking for each branch from the *Time Tracker* tool window.
- **One active timer at a time**: starting an entry pauses the current one.
- **Automatic pause**:
  - on IDE inactivity (configurable timeout, default 5 min; optional auto-resume);
  - on branch switch (the entry of the branch you leave is paused);
  - on IDE shutdown (so overnight hours are not counted).
- **Daily summary** (*Summary* tab): day total, per-project and per-branch breakdown, day-by-day
  navigation and last-7-days totals.
- Data is **IDE-wide** (all projects in one view) and persisted in
  `<config>/options/branchTimeTracker.xml`.

## Languages

The UI is translated into **English** (default) and **Italian**. Strings live in
`src/main/resources/messages/TimeTrackerBundle[_it].properties` and go through
`com.github.gbassi.timetracker.TimeTrackerBundle`.

The language follows the IDE locale (language pack), not the OS locale. There is no official
JetBrains Italian language pack: to see Italian, start the IDE with `-Didea.locale=it`
(*Help > Edit Custom VM Options*) or set the `i18n.locale` registry key.

## Requirements

- IntelliJ IDEA 2024.3+ (build 243+)
- JDK 21 (already used by the Gradle toolchain)

## Development

```bash
./gradlew test          # unit tests (time accrual logic)
./gradlew buildPlugin    # produces build/distributions/intellij-timetracker-<version>.zip
./gradlew runIde         # launches a sandbox IDE with the plugin installed
./gradlew verifyPlugin    # plugin verifier against IC 2024.3
```

If `gradle/wrapper/gradle-wrapper.jar` is missing, regenerate it with *Gradle > Add Gradle Wrapper*
from IntelliJ's Gradle bundle, or run `gradle wrapper --gradle-version 8.10.2`.

To install the plugin in a real IDE: *Settings > Plugins > ⚙ > Install Plugin from Disk…* and pick
the zip in `build/distributions/`.

## Settings

*Settings > Tools > Branch Time Tracker*:

| Option | Default | Description |
|---|---|---|
| Pause after (minutes) | 5 | Inactivity timeout |
| Resume automatically | on | Restart the timer when activity resumes |
| Start tracking on checkout | off | If off, checkout only creates the entry |
| Pause on branch switch | on | Pause the active entry when you switch branch |


## Project layout

```
model/        BranchEntry, DayBucket, TrackerState, EntryState
service/      TimeTrackerService (persistent state + ticker), TimeAccrual (midnight split),
              TimeTrackerListener (message bus)
git/          BranchWatcher (GitRepositoryChangeListener -> detects checkout)
listeners/    TrackerStartupActivity, AppCloseListener
ui/           TimeTrackerToolWindowFactory, BranchTablePanel, DailySummaryPanel, AddEntryDialog
settings/     TimeTrackerConfigurable
```

## License

MIT — see [LICENSE](LICENSE).
