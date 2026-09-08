package com.github.gbassi.timetracker

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.TimeTrackerBundle"

/**
 * Message bundle for all user-facing strings. The default bundle is English
 * ([BUNDLE]); `TimeTrackerBundle_it.properties` provides the Italian override and
 * is picked up automatically when the IDE runs with an Italian locale.
 */
object TimeTrackerBundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
