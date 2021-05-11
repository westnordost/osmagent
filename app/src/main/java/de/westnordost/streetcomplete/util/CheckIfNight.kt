package de.westnordost.streetcomplete.util

import com.luckycatlabs.sunrisesunset.Zenith
import com.luckycatlabs.sunrisesunset.calculator.SolarEventCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

fun localDateToCalendar(localDate: LocalDate): Calendar {
    val calendar = Calendar.getInstance()
    calendar.set(localDate.year, localDate.month.value, localDate.dayOfMonth)
    return calendar
}

fun isNight(pos: LatLon): Boolean {
    /* This functions job is to check if it's currently dark out.
    It will use the location of the node and check the civil sunset time.

    Currently it checks the phone time and returns true if between 7pm and 7am.
     */

    val timezone = TimeZone.getDefault().id
    val location = Location(pos.latitude, pos.longitude)
    val calculator = SolarEventCalculator(location, timezone)
    val now = ZonedDateTime.now(ZoneId.of(timezone))
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)

    val time1 = ZonedDateTime.of(today, LocalTime.parse(calculator.computeSunsetTime(Zenith.CIVIL, localDateToCalendar(today))), ZoneId.of(timezone))
    val time2 = ZonedDateTime.of(tomorrow, LocalTime.parse(calculator.computeSunriseTime(Zenith.CIVIL, localDateToCalendar(tomorrow))), ZoneId.of(timezone))
    return now in time1..time2
}
