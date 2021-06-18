package de.westnordost.streetcomplete.data.quest

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestSource
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestSource
import de.westnordost.streetcomplete.data.visiblequests.DayNightQuestFilter
import de.westnordost.streetcomplete.data.visiblequests.TeamModeQuestFilter
import de.westnordost.streetcomplete.data.visiblequests.VisibleQuestTypeSource
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** Access and listen to quests visible on the map */
@Singleton class VisibleQuestsSource @Inject constructor(
    private val questTypeRegistry: QuestTypeRegistry,
    private val osmQuestSource: OsmQuestSource,
    private val osmNoteQuestSource: OsmNoteQuestSource,
    private val visibleQuestTypeSource: VisibleQuestTypeSource,
    private val teamModeQuestFilter: TeamModeQuestFilter,
    private val dayNightQuestFilter: DayNightQuestFilter,
) {
    interface Listener {
        /** Called when given quests in the given group have been added/removed */
        fun onUpdatedVisibleQuests(added: Collection<Quest>, removed: Collection<QuestKey>)
        /** Called when something has changed which should trigger any listeners to update all */
        fun onVisibleQuestsInvalidated()
    }

    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    private val osmQuestSourceListener = object : OsmQuestSource.Listener {
        override fun onUpdated(addedQuests: Collection<OsmQuest>, deletedQuestKeys: Collection<OsmQuestKey>) {
            updateVisibleQuests(addedQuests.filter(::isVisible), deletedQuestKeys)
        }
        override fun onInvalidated() {
            // apparently the visibility of many different quests have changed
            invalidate()
        }
    }

    private val osmNoteQuestSourceListener = object : OsmNoteQuestSource.Listener {
        override fun onUpdated(addedQuests: Collection<OsmNoteQuest>, deletedQuestIds: Collection<Long>) {
            updateVisibleQuests(addedQuests.filter(::isVisible), deletedQuestIds.map { OsmNoteQuestKey(it) })
        }
        override fun onInvalidated() {
            // apparently the visibility of many different notes have changed
            invalidate()
        }
    }

    private val visibleQuestTypeSourceListener = object : VisibleQuestTypeSource.Listener {
        override fun onQuestTypeVisibilitiesChanged() {
            // many different quests could become visible/invisible when this is changed
            invalidate()
        }
    }

    private val teamModeQuestFilterListener = object : TeamModeQuestFilter.TeamModeChangeListener {
        override fun onTeamModeChanged(enabled: Boolean) {
            invalidate()
        }
    }

    init {
        osmQuestSource.addListener(osmQuestSourceListener)
        osmNoteQuestSource.addListener(osmNoteQuestSourceListener)
        visibleQuestTypeSource.addListener(visibleQuestTypeSourceListener)
        teamModeQuestFilter.addListener(teamModeQuestFilterListener)
    }

    /** Retrieve all visible quests in the given bounding box from local database */
    fun getAllVisible(bbox: BoundingBox): List<Quest> {
        val visibleQuestTypeNames = questTypeRegistry
            .getVisible(visibleQuestTypeSource)
            .map { it::class.simpleName!! }
        if (visibleQuestTypeNames.isEmpty()) return listOf()

        val osmQuests = osmQuestSource.getAllVisibleInBBox(bbox, visibleQuestTypeNames)
        val osmNoteQuests = osmNoteQuestSource.getAllVisibleInBBox(bbox)

        return osmQuests.filter(::isVisible) + osmNoteQuests.filter(::isVisible)
    }

    private fun isVisible(quest: Quest): Boolean =
        visibleQuestTypeSource.isVisible(quest.type) && teamModeQuestFilter.isVisible(quest) && dayNightQuestFilter.isVisible(quest)

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun updateVisibleQuests(addedQuests: Collection<Quest>, deletedQuestKeys: Collection<QuestKey>) {
        if (addedQuests.isEmpty() && deletedQuestKeys.isEmpty()) return
        listeners.forEach { it.onUpdatedVisibleQuests(addedQuests, deletedQuestKeys) }
    }

    private fun invalidate() {
        listeners.forEach { it.onVisibleQuestsInvalidated() }
    }
}


