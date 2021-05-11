package de.westnordost.streetcomplete.data.quest

sealed class DayNightCycle

object DayAndNight : DayNightCycle()
object OnlyDay : DayNightCycle()
object OnlyNight : DayNightCycle()
