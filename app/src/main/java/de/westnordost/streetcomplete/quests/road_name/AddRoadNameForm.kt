package de.westnordost.streetcomplete.quests.road_name

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog

import java.util.LinkedList
import java.util.Locale

import javax.inject.Inject

import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.AbbreviationsByLocale
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.AAddLocalizedNameForm
import de.westnordost.streetcomplete.quests.AddLocalizedNameAdapter
import de.westnordost.streetcomplete.quests.LocalizedName
import de.westnordost.streetcomplete.quests.road_name.data.RoadNameSuggestionsDao
import java.lang.IllegalStateException


class AddRoadNameForm : AAddLocalizedNameForm<RoadNameAnswer>() {

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_name_answer_noName) { selectNoStreetNameReason() },
        OtherAnswer(R.string.quest_streetName_answer_cantType) { showKeyboardInfo() }
    )

    @Inject internal lateinit var abbreviationsByLocale: AbbreviationsByLocale
    @Inject internal lateinit var roadNameSuggestionsDao: RoadNameSuggestionsDao

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun createLocalizedNameAdapter(data: List<LocalizedName>, addLanguageButton: View): AddLocalizedNameAdapter {
        return AddLocalizedNameAdapter(
            data, requireContext(), getPossibleStreetsignLanguageTags(),
            abbreviationsByLocale, getRoadNameSuggestions(), addLanguageButton
        )
    }

    private fun getRoadNameSuggestions(): List<MutableMap<String, String>> {
        val polyline = when(val geom = elementGeometry) {
            is ElementPolylinesGeometry -> geom.polylines.first()
            is ElementPolygonsGeometry -> geom.polygons.first()
            is ElementPointGeometry -> listOf(geom.center)
        }
        return roadNameSuggestionsDao.getNames(
            listOf(polyline.first(), polyline.last()),
            MAX_DIST_FOR_ROAD_NAME_SUGGESTION
        )
    }

    override fun onClickOk(names: List<LocalizedName>) {
        val possibleAbbreviations = LinkedList<String>()
        for ((languageTag, name) in adapter.localizedNames) {
            val locale = if (languageTag.isEmpty()) countryInfo.locale else Locale.forLanguageTag(languageTag)
            val abbr = abbreviationsByLocale.get(locale)
            val containsLocalizedAbbreviations = abbr?.containsAbbreviations(name) == true

            if (name.contains(".") || containsLocalizedAbbreviations) {
                possibleAbbreviations.add(name)
            }
        }

        confirmPossibleAbbreviationsIfAny(possibleAbbreviations) {
            val points = when(val g = elementGeometry) {
                is ElementPolylinesGeometry -> g.polylines.first()
                is ElementPolygonsGeometry -> g.polygons.first()
                is ElementPointGeometry -> listOf(g.center)
            }
            applyAnswer(RoadName(names, osmElement!!.id, points))
        }
    }

    private fun selectNoStreetNameReason() {
        val linkRoad = resources.getString(R.string.quest_streetName_answer_noProperStreet_link)
        val serviceRoad = resources.getString(R.string.quest_streetName_answer_noProperStreet_service2)
        val trackRoad = resources.getString(R.string.quest_streetName_answer_noProperStreet_track2)
        val noName = resources.getString(R.string.quest_streetName_answer_noName_noname)
        val leaveNote = resources.getString(R.string.quest_streetName_answer_noProperStreet_leaveNote)

        val highwayValue = osmElement!!.tags["highway"]
        val mayBeLink = highwayValue?.matches("primary|secondary|tertiary".toRegex()) == true

        val answers = mutableListOf<String>()
        if (mayBeLink) answers.add(linkRoad)
        answers.add(serviceRoad)
        answers.add(trackRoad)
        answers.add(leaveNote)
        answers.add(noName)

        val onSelect = object : DialogInterface.OnClickListener {
            var selection: Int? = null

            override fun onClick(dialog: DialogInterface, which: Int) {
                if (which >= 0) {
                    selection = which
                    (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                } else if (which == DialogInterface.BUTTON_POSITIVE) {
                    selection?.let {
                        if(it >= 0 && it < answers.size) onAnswer(it)
                    }
                }
            }

            private fun onAnswer(selection: Int) {
                val answer = answers[selection]
                when (answer) {
                    leaveNote -> composeNote()
                    noName    -> confirmNoStreetName()
                    else      -> {
                        applyAnswer(when(answer) {
                            linkRoad    -> RoadIsLinkRoad
                            serviceRoad -> RoadIsServiceRoad
                            trackRoad   -> RoadIsTrack
                            else        -> throw IllegalStateException()
                        })
                    }
                }
            }
        }

        val dlg = AlertDialog.Builder(requireContext())
            .setSingleChoiceItems(answers.toTypedArray(), -1, onSelect)
            .setTitle(R.string.quest_streetName_answer_noName_question)
            .setPositiveButton(android.R.string.ok, onSelect)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        dlg.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
    }

    private fun confirmNoStreetName() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quest_name_answer_noName_confirmation_title)
            .setMessage(R.string.quest_streetName_answer_noName_confirmation_description)
            .setPositiveButton(R.string.quest_name_noName_confirmation_positive) { _, _ -> applyAnswer(NoRoadName) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
    }

    companion object {
        const val MAX_DIST_FOR_ROAD_NAME_SUGGESTION = 30.0 //m
    }
}
