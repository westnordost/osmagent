package de.westnordost.streetcomplete.quests.lanes

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.ANYTHING_PAVED
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType

class AddLanes : OsmFilterQuestType<LanesAnswer>() {

    override val elementFilter = """
        ways with
          (
            highway ~ ${ROADS_WITH_LANES.joinToString("|")}
            or highway = residential and (
              maxspeed > 30
              or (maxspeed ~ ".*mph" and maxspeed !~ "([1-9]|1[0-9]|20) mph")
            )
          )
          and surface ~ ${ANYTHING_PAVED.joinToString("|")}
          and (!lanes or lanes = 0)
          and lane_markings != no
    """
    override val commitMessage = "Add road lanes"
    override val wikiLink = "Key:lanes"
    override val icon = R.drawable.ic_quest_street_lanes
    override val isSplitWayEnabled = true

    override fun getTitle(tags: Map<String, String>) = R.string.quest_lanes_title

    override fun createForm() = AddLanesForm()

    override fun applyAnswerTo(answer: LanesAnswer, changes: StringMapChangesBuilder) {

        val laneCount = answer.total

        laneCount?.let { changes.addOrModify("lanes", it.toString()) }

        val isMarked = answer is MarkedLanes || answer is MarkedLanesSides
        // if there is just one lane, the information whether it is marked or not is irrelevant
        // (if there are no more than one lane, there are no markings to separate them)
        when {
            laneCount == 1 -> changes.deleteIfExists("lane_markings")
            isMarked ->       changes.modifyIfExists("lane_markings", "yes")
            else ->           changes.addOrModify("lane_markings", "no")
        }

        val hasCenterLeftTurnLane = answer is MarkedLanesSides && answer.centerLeftTurnLane
        if (hasCenterLeftTurnLane) {
            changes.addOrModify("lanes:both_ways", "1")
            changes.addOrModify("turn:lanes:both_ways","left")
        } else {
            changes.deleteIfExists("lanes:both_ways")
            changes.deleteIfExists("turn:lanes:both_ways")
        }

        when(answer) {
            is MarkedLanes, is UnmarkedLanesKnowLaneCount -> {
                if (answer.total == 1) {
                    changes.deleteIfExists("lanes:forward")
                    changes.deleteIfExists("lanes:backward")
                } else {
                    changes.modifyIfExists("lanes:forward", (answer.total!! / 2).toString())
                    changes.modifyIfExists("lanes:backward", (answer.total!! / 2).toString())
                }
            }
            is UnmarkedLanes -> {
                changes.deleteIfExists("lanes:forward")
                changes.deleteIfExists("lanes:backward")
            }
            is MarkedLanesSides -> {
                changes.addOrModify("lanes:forward", answer.forward.toString())
                changes.addOrModify("lanes:backward", answer.backward.toString())
            }
        }
    }

    companion object {
        private val ROADS_WITH_LANES = listOf(
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link",
            "unclassified"
        )
    }
}

