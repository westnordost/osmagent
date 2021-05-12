package de.westnordost.streetcomplete.data.visiblequests

import de.westnordost.streetcomplete.data.quest.DayNightCycle.*
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.util.isNight
import javax.inject.Inject

class DayNightQuestFilter @Inject internal constructor() {
    /*
    Might be an idea to add a listener so this is reevaluated occasionally, or something like that.
    However, I think it's reevaluated everytime the displayed quests are updated?
     */
    fun isVisible(quest: Quest): Boolean {
        return when (quest.type.dayNightCycle) {
            DAY_AND_NIGHT -> true
            ONLY_DAY -> !isNight(quest.position)
            ONLY_NIGHT -> isNight(quest.position)
        }
    }
}
