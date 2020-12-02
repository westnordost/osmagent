package de.westnordost.streetcomplete.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.UserStore
import de.westnordost.streetcomplete.data.user.achievements.Achievement
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsSource
import de.westnordost.streetcomplete.ktx.awaitPreDraw
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.view.GridLayoutSpacingItemDecoration
import de.westnordost.streetcomplete.view.ListAdapter
import kotlinx.android.synthetic.main.cell_achievement.view.*
import kotlinx.android.synthetic.main.fragment_achievements.*
import kotlinx.coroutines.*
import javax.inject.Inject

/** Shows the icons for all achieved achievements and opens a AchievementInfoFragment to show the
 *  details on click. */
class AchievementsFragment : Fragment(R.layout.fragment_achievements),
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject internal lateinit var userAchievementsSource: UserAchievementsSource
    @Inject internal lateinit var userStore: UserStore

    private var actualCellWidth: Int = 0

    init {
        Injector.applicationComponent.inject(this)
    }

    interface Listener {
        fun onClickedAchievement(achievement: Achievement, level: Int, achievementBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    /* --------------------------------------- Lifecycle ---------------------------------------- */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val minCellWidth = 144f.toPx(ctx)
        val itemSpacing = ctx.resources.getDimensionPixelSize(R.dimen.achievements_item_margin)

        launch {
            view.awaitPreDraw()

            emptyText.visibility = View.GONE

            val spanCount = (view.width / (minCellWidth + itemSpacing)).toInt()
            actualCellWidth = (view.width.toFloat() / spanCount - itemSpacing).toInt()

            val layoutManager = GridLayoutManager(ctx, spanCount, RecyclerView.VERTICAL, false)
            achievementsList.layoutManager = layoutManager
            achievementsList.addItemDecoration(GridLayoutSpacingItemDecoration(itemSpacing))
            achievementsList.clipToPadding = false

            val achievements = withContext(Dispatchers.IO) {
                userAchievementsSource.getAchievements()
            }
            achievementsList.adapter = AchievementsAdapter(achievements)

            emptyText.isGone = achievements.isNotEmpty()
        }
    }

    override fun onStart() {
        super.onStart()

        if (userStore.isSynchronizingStatistics) {
            emptyText.setText(R.string.stats_are_syncing)
        } else {
            emptyText.setText(R.string.achievements_empty)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    /* -------------------------------------- Interaction --------------------------------------- */

    private inner class AchievementsAdapter(achievements: List<Pair<Achievement, Int>>
    ) : ListAdapter<Pair<Achievement, Int>>(achievements) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.cell_achievement, parent, false)
            view.updateLayoutParams {
                width = actualCellWidth
                height = actualCellWidth
            }
            return ViewHolder(view)
        }

        inner class ViewHolder(itemView: View) : ListAdapter.ViewHolder<Pair<Achievement, Int>>(itemView) {
            override fun onBind(with: Pair<Achievement, Int>) {
                val achievement = with.first
                val level = with.second
                itemView.achievementIconView.icon = requireContext().getDrawable(achievement.icon)
                itemView.achievementIconView.level = level
                itemView.achievementIconView.setOnClickListener {
                    listener?.onClickedAchievement(achievement, level, itemView.achievementIconView)
                }
            }
        }
    }
}
