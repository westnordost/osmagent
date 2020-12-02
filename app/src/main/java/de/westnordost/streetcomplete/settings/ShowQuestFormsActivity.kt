package de.westnordost.streetcomplete.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.map.data.OsmNode
import de.westnordost.osmapi.map.data.OsmWay
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.changes.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.osmquest.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.*
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.view.ListAdapter
import kotlinx.android.synthetic.main.fragment_show_quest_forms.*
import kotlinx.android.synthetic.main.row_quest_display.view.*
import kotlinx.android.synthetic.main.toolbar.*
import java.util.*
import javax.inject.Inject

/** activity only used in debug, to show all the different forms for the different quests. */
class ShowQuestFormsActivity : AppCompatActivity(), AbstractQuestAnswerFragment.Listener {

    @Inject internal lateinit var questTypeRegistry: QuestTypeRegistry
    @Inject internal lateinit var prefs: SharedPreferences

    private val showQuestFormAdapter: ShowQuestFormAdapter = ShowQuestFormAdapter()

    private var currentQuestType: QuestType<*>? = null

    init {
        Injector.applicationComponent.inject(this)
        showQuestFormAdapter.list = questTypeRegistry.all.toMutableList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_show_quest_forms)
        toolbar.navigationIcon = getDrawable(R.drawable.ic_close_white_24dp)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        toolbar.title = "Show Quest Forms"

        questFormContainer.setOnClickListener { onBackPressed() }

        showQuestFormsList.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            layoutManager = LinearLayoutManager(context)
            adapter = showQuestFormAdapter
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            popQuestForm()
        } else {
            super.onBackPressed()
        }
    }

    private fun popQuestForm() {
        questFormContainer.visibility = View.GONE
        supportFragmentManager.popBackStack()
        currentQuestType = null
    }

    inner class ShowQuestFormAdapter: ListAdapter<QuestType<*>>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListAdapter.ViewHolder<QuestType<*>> =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.row_quest_display, parent, false))

        private inner class ViewHolder(itemView: View) : ListAdapter.ViewHolder<QuestType<*>>(itemView) {
            override fun onBind(with: QuestType<*>) {
                itemView.questIcon.setImageResource(with.icon)
                itemView.questTitle.text = genericQuestTitle(itemView, with)
                itemView.setOnClickListener { onClickQuestType(with) }
            }
        }
    }

    private fun onClickQuestType(questType: QuestType<*>) {
        val latititudeDelta = 0
        val longitudeDelta = 0
        val firstLat = Double.fromBits(prefs.getLong(Prefs.MAP_LATITUDE, 0.0.toBits()))
        val firstLng = Double.fromBits(prefs.getLong(Prefs.MAP_LONGITUDE, 0.0.toBits()))
        val firstPos = OsmLatLon(firstLat, firstLng)
        val secondLat = Double.fromBits(prefs.getLong(Prefs.MAP_LATITUDE, (0.0 + latititudeDelta).toBits()))
        val secondLng = Double.fromBits(prefs.getLong(Prefs.MAP_LONGITUDE, (0.0 + longitudeDelta).toBits()))
        val secondPos = OsmLatLon(secondLat, secondLng)
        val centerLat = Double.fromBits(prefs.getLong(Prefs.MAP_LATITUDE, (0.0 + latititudeDelta/2).toBits()))
        val centerLng = Double.fromBits(prefs.getLong(Prefs.MAP_LONGITUDE, (0.0 + longitudeDelta/2).toBits()))
        val centerPos = OsmLatLon(centerLat, centerLng)
        val tags =  mapOf("highway" to "cycleway", "building" to "residential", "name" to "<object name>", "opening_hours" to "Mo-Fr 08:00-12:00,13:00-17:30; Sa 08:00-12:00")
        val firstNode = OsmNode(1, 1, firstPos, tags)
        val secondNode = OsmNode(2, 1, secondPos, tags)
        val element = OsmWay(1, 1, mutableListOf(1, 2), tags)
        val elementGeometry = ElementPolylinesGeometry(listOf(listOf(firstPos, secondPos)), centerPos)

        val quest = object : Quest {
            override var id: Long? = 1L
            override val center = firstPos
            override val markerLocations = listOf<LatLon>(firstPos)
            override val geometry = elementGeometry
            override val type = questType
            override var status = QuestStatus.NEW
            override val lastUpdate = Date()
        }

        val f = questType.createForm()
        val args = AbstractQuestAnswerFragment.createArguments(quest, QuestGroup.OSM, element, 0f, 0f)
        f.arguments = args

        currentQuestType = questType

        questFormContainer.visibility = View.VISIBLE
        supportFragmentManager.commit {
            replace(R.id.questForm, f)
            addToBackStack(null)
        }
    }

    override fun onAnsweredQuest(questId: Long, group: QuestGroup, answer: Any) {
        val builder = StringMapChangesBuilder(mapOf())
        (currentQuestType as? OsmElementQuestType<Any>)?.applyAnswerTo(answer, builder)
        val tagging = builder.create().changes.joinToString("\n")
        AlertDialog.Builder(this)
            .setMessage("Tagging\n$tagging")
            .show()
        popQuestForm()
    }
    override fun onComposeNote(questId: Long, group: QuestGroup, questTitle: String) {
        popQuestForm()
        AlertDialog.Builder(this)
            .setMessage("Composing note")
            .show()
    }
    override fun onSplitWay(osmQuestId: Long) {
        popQuestForm()
        AlertDialog.Builder(this)
            .setMessage("Splitting way")
            .show()
    }
    override fun onSkippedQuest(questId: Long, group: QuestGroup) {
        popQuestForm()
        AlertDialog.Builder(this)
            .setMessage("Skipping quest")
            .show()
    }
}
