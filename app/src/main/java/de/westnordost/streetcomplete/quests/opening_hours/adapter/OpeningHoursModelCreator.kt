package de.westnordost.streetcomplete.quests.opening_hours.adapter

import de.westnordost.streetcomplete.quests.opening_hours.model.OpeningMonths
import de.westnordost.streetcomplete.quests.opening_hours.model.OpeningWeekdays
import de.westnordost.streetcomplete.quests.opening_hours.model.TimeRange


fun List<OpeningMonthsRow>.toOpeningMonthsList() = this.map { it.toOpeningMonths() }

private fun OpeningMonthsRow.toOpeningMonths() =
    OpeningMonths(months, weekdaysList.toOpeningWeekdaysOld().toWeekdaysClustersOldFunction())

//TODO this one should be removable in future
private fun List<OpeningWeekdaysRow>.toOpeningWeekdaysOld(): List<OpeningWeekdays> {
    val result = mutableListOf<OpeningWeekdays>()
    var last: OpeningWeekdays? = null
    for ((weekdays, timeRange) in this) {
        // merging rows that have the same weekdays
        // OpeningWeekdaysRow may have single timeRange, OpeningWeekdays may have list
        if (last != null && last.weekdays == weekdays) {
            last.timeRanges.add(timeRange)
        } else {
            val times = mutableListOf<TimeRange>()
            times.add(timeRange)
            last = OpeningWeekdays(weekdays, times)
            result.add(last)
        }
    }
    return result
}

//TODO this one should be removable in future
private fun List<OpeningWeekdays>.toWeekdaysClustersOldFunction(): List<List<OpeningWeekdays>> {
    val unsorted = toMutableList()

    val clusters = mutableListOf<List<OpeningWeekdays>>()

    while (!unsorted.isEmpty()) {
        val cluster = mutableListOf<OpeningWeekdays>()
        cluster.add(unsorted.removeAt(0))
        val it = unsorted.iterator()
        while (it.hasNext()) {
            val other = it.next()
            var anyWeekdaysOverlap = false
            var anyTimesOverlap = false
            for (inThisCluster in cluster) {
                val weekdaysOverlaps = inThisCluster.intersectsWeekdays(other)
                val anyTimeRangeOverlaps = inThisCluster.intersects(other)
                anyTimesOverlap = anyTimesOverlap || weekdaysOverlaps && anyTimeRangeOverlaps
                anyWeekdaysOverlap = anyWeekdaysOverlap || weekdaysOverlaps
            }
            if (anyWeekdaysOverlap && !anyTimesOverlap) {
                cluster.add(other)
                it.remove()
            }
        }
        clusters.add(cluster)
    }

    return clusters
}
