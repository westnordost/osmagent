package de.westnordost.streetcomplete.user


import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.Fragment
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.user.QuestStatisticsDao
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.view.CircularOutlineProvider
import kotlinx.android.synthetic.main.fragment_quest_statistics_ball_pit.*
import javax.inject.Inject

/** Shows the user's solved quests of each type in some kind of ball pit. Clicking on each opens
 *  a QuestTypeInfoFragment that shows the quest's details. */
class QuestStatisticsByQuestTypeFragment : Fragment(R.layout.fragment_quest_statistics_ball_pit)
{
    @Inject internal lateinit var questStatisticsDao: QuestStatisticsDao
    @Inject internal lateinit var questTypeRegistry: QuestTypeRegistry

    interface Listener {
        fun onClickedQuestType(questType: QuestType<*>, solvedCount: Int, questBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        Injector.applicationComponent.inject(this)
    }

    /* --------------------------------------- Lifecycle ---------------------------------------- */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycle.addObserver(ballPitView)

        val solvedQuestsByQuestType = questStatisticsDao.getAll()
                    .filterKeys { questTypeRegistry.getByName(it) != null }
                    .mapKeys { questTypeRegistry.getByName(it.key)!! }

        ballPitView.setViews(solvedQuestsByQuestType.map { (questType, amount) ->
            createQuestTypeBubbleView(questType, amount) to amount
        })
    }

    private fun createQuestTypeBubbleView(questType: QuestType<*>, solvedCount: Int): View {
        val ctx = requireContext()
        val questView = ImageView(ctx)
        questView.id = View.generateViewId()
        questView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        questView.scaleType = ImageView.ScaleType.FIT_XY
        questView.setImageResource(questType.icon)

        val clickableContainer = FrameLayout(ctx)
        clickableContainer.layoutParams = ViewGroup.LayoutParams(256,256)
        clickableContainer.foreground = requireContext().getDrawable(R.drawable.round_pressed)
        clickableContainer.elevation = 6f.toPx(ctx)
        clickableContainer.outlineProvider = CircularOutlineProvider
        clickableContainer.addView(questView)
        clickableContainer.setOnClickListener { v ->
            listener?.onClickedQuestType(questType, solvedCount, v)
        }

        return clickableContainer
    }
}

