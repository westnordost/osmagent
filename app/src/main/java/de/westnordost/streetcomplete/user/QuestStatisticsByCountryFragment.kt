package de.westnordost.streetcomplete.user

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.CountryStatisticsDao
import de.westnordost.streetcomplete.ktx.toPx
import kotlinx.android.synthetic.main.fragment_quest_statistics_ball_pit.*
import javax.inject.Inject

/** Shows the user's solved quests of each type in some kind of ball pit.  */
class QuestStatisticsByCountryFragment : Fragment(R.layout.fragment_quest_statistics_ball_pit)
{
    @Inject internal lateinit var countryStatisticsDao: CountryStatisticsDao

    interface Listener {
        fun onClickedCountryFlag(countryCode: String, solvedCount: Int, rank: Int?, countryBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycle.addObserver(ballPitView)

        val countriesStatistics = countryStatisticsDao.getAll()

        ballPitView.setViews(countriesStatistics.map {
            createCountryBubbleView(it.countryCode, it.solvedCount, it.rank) to it.solvedCount
        })
    }

    private fun createCountryBubbleView(countryCode: String, solvedCount: Int, rank: Int?): View {
        val ctx = requireContext()
        val countryBubbleView = CircularFlagView(ctx)
        countryBubbleView.id = View.generateViewId()
        countryBubbleView.layoutParams = ViewGroup.LayoutParams(240,240)
        countryBubbleView.countryCode = countryCode
        countryBubbleView.elevation = 6f.toPx(ctx)
        countryBubbleView.setOnClickListener { v ->
            listener?.onClickedCountryFlag(countryCode, solvedCount, rank, v)
        }
        return countryBubbleView
    }
}

