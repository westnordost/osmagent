package de.westnordost.streetcomplete.quests.building_flat_count

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder

class AddBuildingFlatCount : OsmFilterQuestType<Int>() {

    override val elementFilter = """
        nodes, ways with building = apartments
         and (
           !'building:flats'
         )
    """

    override val commitMessage = "Add building flat count"
    override val wikiLink = "Key:building:flats"
    override val icon = R.drawable.ic_quest_bicycle_parking_capacity

    override fun getTitle(tags: Map<String, String>) = R.string.quest_buildingFlatCount_title

    override fun createForm() = AddBuildingFlatCountForm()

    override fun applyAnswerTo(answer: Int, changes: StringMapChangesBuilder) {
        changes.add("building:flats", answer.toString())
    }
}
