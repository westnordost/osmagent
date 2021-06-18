package de.westnordost.streetcomplete.quests.way_lit

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.MAXSPEED_TYPE_KEYS
import de.westnordost.streetcomplete.data.meta.updateWithCheckDate
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.quest.DayNightCycle.ONLY_NIGHT

class AddWayLit : OsmFilterQuestType<WayLit>() {

    /* Using sidewalk, source:maxspeed=*urban etc and a urban-like maxspeed as tell-tale tags for
       (urban) streets which reached a certain level of development. I.e. non-urban streets will
       usually not even be lit in industrialized countries.

       Also, only include paths only for those which are equal to footway/cycleway to exclude
       most hike paths and trails.

        See #427 for discussion. */
    override val elementFilter = """
        ways with
        (
          highway ~ ${LIT_RESIDENTIAL_ROADS.joinToString("|")}
          or highway ~ ${LIT_NON_RESIDENTIAL_ROADS.joinToString("|")} and
          (
            sidewalk ~ both|left|right|yes|separate
            or ~${(MAXSPEED_TYPE_KEYS + "maxspeed").joinToString("|")} ~ .*urban|.*zone.*
            or maxspeed <= 60
            or maxspeed ~ "(5|10|15|20|25|30|35) mph"
          )
          or highway ~ ${LIT_WAYS.joinToString("|")}
          or highway = path and (foot = designated or bicycle = designated)
        )
        and
        (
          !lit
          or lit = no and lit older today -8 years
          or lit older today -16 years
        )
        and (access !~ private|no or (foot and foot !~ private|no))
        and indoor != yes
    """

    override val commitMessage = "Add whether way is lit"
    override val wikiLink = "Key:lit"
    override val icon = R.drawable.ic_quest_lantern
    override val isSplitWayEnabled = true
    override val dayNightCycle = ONLY_NIGHT

    override fun getTitle(tags: Map<String, String>): Int {
        val type = tags["highway"]
        val hasName = tags.containsKey("name")
        val isRoad = LIT_NON_RESIDENTIAL_ROADS.contains(type) || LIT_RESIDENTIAL_ROADS.contains(type)

        return when {
            hasName -> R.string.quest_way_lit_named_title
            isRoad  -> R.string.quest_way_lit_road_title
            else    -> R.string.quest_way_lit_title
        }
    }

    override fun createForm() = WayLitForm()

    override fun applyAnswerTo(answer: WayLit, changes: StringMapChangesBuilder) {
        changes.updateWithCheckDate("lit", answer.osmValue)
    }

    companion object {
        private val LIT_RESIDENTIAL_ROADS = arrayOf("residential", "living_street", "pedestrian")

        private val LIT_NON_RESIDENTIAL_ROADS =
            arrayOf("primary", "primary_link", "secondary", "secondary_link",
                    "tertiary", "tertiary_link", "unclassified", "service")

        private val LIT_WAYS = arrayOf("footway", "cycleway", "steps")
    }
}
