package de.westnordost.streetcomplete.data.quest

import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment

interface QuestType<T> {

    /** the icon resource id used to display this quest type on the map */
    val icon: Int

    /** the title resource id used to display the quest's question */
    val title: Int

    /** returns the string resource id that explains why this quest is disabled by default or zero
     * if it is not disabled by default */
    val defaultDisabledMessage: Int get() = 0

    /** returns the dialog in which the user can add the data */
    fun createForm(): AbstractQuestAnswerFragment<T>

    /** The quest type can clean it's metadata that is older than the given timestamp here, if any  */
    fun deleteMetadataOlderThan(timestamp: Long) {}

    /** if the quest should only be shown during day-light os night-time hours */
    val dayNightCycle: DayNightCycle get() = DayNightCycle.DAY_AND_NIGHT
}
