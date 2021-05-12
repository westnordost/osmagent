package de.westnordost.streetcomplete.data.visiblequests

import android.content.SharedPreferences
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.quest.DayNightCycle.*
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.util.isNight
import javax.inject.Inject

class DayNightQuestFilter @Inject internal constructor(
    private val prefs: SharedPreferences
) {

    fun isVisible(quest: Quest): Boolean {

        // always true if bypass setting true
        if (prefs.getBoolean(Prefs.ALWAYS_SHOW_TIME_DEPENDENT, false)) return true

        // Check time
        return when (quest.type.dayNightCycle) {
            DAY_AND_NIGHT -> true
            ONLY_DAY -> !isNight(quest.position)
            ONLY_NIGHT -> isNight(quest.position)
        }
    }
}
