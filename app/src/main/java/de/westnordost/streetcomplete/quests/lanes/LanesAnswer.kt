package de.westnordost.streetcomplete.quests.lanes

sealed class LanesAnswer

data class MarkedLanes(val count: Int) : LanesAnswer()
object UnmarkedLanes : LanesAnswer()
data class UnmarkedLanesKnowLaneCount(val count: Int) : LanesAnswer()
data class MarkedLanesSides(val forward: Int, val backward: Int, val centerLeftTurnLane: Boolean) : LanesAnswer()

val LanesAnswer.total: Int? get() = when(this) {
    is MarkedLanes -> count
    is UnmarkedLanesKnowLaneCount -> count
    is UnmarkedLanes -> null
    is MarkedLanesSides -> forward + backward + (if (centerLeftTurnLane) 1 else 0)
}
