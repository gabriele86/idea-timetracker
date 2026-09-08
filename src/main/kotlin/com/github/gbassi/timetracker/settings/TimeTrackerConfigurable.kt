package com.github.gbassi.timetracker.settings

import com.github.gbassi.timetracker.TimeTrackerBundle.message
import com.github.gbassi.timetracker.service.TimeTrackerService
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class TimeTrackerConfigurable : BoundConfigurable(message("configurable.name")) {

    override fun createPanel(): DialogPanel {
        val s = TimeTrackerService.getInstance()
        return panel {
            group(message("settings.group.idle")) {
                row(message("settings.idle.timeout")) {
                    spinner(1..600)
                        .bindIntValue({ s.idleTimeoutMinutes }, { s.idleTimeoutMinutes = it })
                }
                row {
                    checkBox(message("settings.idle.autoResume"))
                        .bindSelected({ s.autoResumeAfterIdle }, { s.autoResumeAfterIdle = it })
                }
            }
            group(message("settings.group.git")) {
                row {
                    checkBox(message("settings.git.autoStart"))
                        .bindSelected({ s.autoStartOnCheckout }, { s.autoStartOnCheckout = it })
                        .comment(message("settings.git.autoStart.comment"))
                }
                row {
                    checkBox(message("settings.git.autoPause"))
                        .bindSelected({ s.autoPauseOnBranchSwitch }, { s.autoPauseOnBranchSwitch = it })
                }
            }
        }
    }
}
