package de.westnordost.streetcomplete.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import de.westnordost.streetcomplete.BuildConfig
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesDao
import de.westnordost.streetcomplete.data.osm.osmquest.OsmQuestController
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestController
import de.westnordost.streetcomplete.ktx.recreateCompat
import de.westnordost.streetcomplete.ktx.toast
import kotlinx.coroutines.*
import javax.inject.Inject

/** Shows the settings screen */
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject internal lateinit var prefs: SharedPreferences
    @Inject internal lateinit var downloadedTilesDao: DownloadedTilesDao
    @Inject internal lateinit var osmQuestController: OsmQuestController
    @Inject internal lateinit var osmNoteQuestController: OsmNoteQuestController
    @Inject internal lateinit var resurveyIntervalsUpdater: ResurveyIntervalsUpdater

    interface Listener {
        fun onClickedQuestSelection()
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, false)
        addPreferencesFromResource(R.xml.preferences)

        findPreference<Preference>("quests")?.setOnPreferenceClickListener {
            listener?.onClickedQuestSelection()
            true
        }

        findPreference<Preference>("quests.invalidation")?.setOnPreferenceClickListener {
            context?.let {
                AlertDialog.Builder(it)
                    .setMessage(R.string.invalidation_dialog_message)
                    .setPositiveButton(R.string.invalidate_confirmation) { _, _ ->
                        downloadedTilesDao.removeAll()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            true
        }

        findPreference<Preference>("quests.restore.hidden")?.setOnPreferenceClickListener {
            val hidden = osmQuestController.unhideAll()
            context?.toast(getString(R.string.restore_hidden_success, hidden), Toast.LENGTH_LONG)
            true
        }

        findPreference<Preference>("debug")?.isVisible = BuildConfig.DEBUG

        findPreference<Preference>("debug.quests")?.setOnPreferenceClickListener {
            startActivity(Intent(context, ShowQuestFormsActivity::class.java))
            true
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.action_settings)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when(key) {
            Prefs.SHOW_NOTES_NOT_PHRASED_AS_QUESTIONS -> {
                val preference = preferenceScreen.findPreference<Preference>(Prefs.SHOW_NOTES_NOT_PHRASED_AS_QUESTIONS) ?: return
                launch {
                    preference.isEnabled = false
                    applyNoteVisibility()
                    preference.isEnabled = true
                }
            }
            Prefs.AUTOSYNC -> {
                if (Prefs.Autosync.valueOf(prefs.getString(Prefs.AUTOSYNC, "ON")!!) != Prefs.Autosync.ON) {
                    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_tutorial_upload, null)
                    AlertDialog.Builder(requireContext())
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            Prefs.THEME_SELECT -> {
                val theme = Prefs.Theme.valueOf(prefs.getString(Prefs.THEME_SELECT, "AUTO")!!)
                AppCompatDelegate.setDefaultNightMode(theme.appCompatNightMode)
                activity?.recreateCompat()
            }
            Prefs.RESURVEY_INTERVALS -> {
                resurveyIntervalsUpdater.update()
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is DialogPreferenceCompat) {
            val fragment = preference.createDialog()
            fragment.arguments = bundleOf("key" to preference.key)
            fragment.setTargetFragment(this, 0)
            fragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private suspend fun applyNoteVisibility() = withContext(Dispatchers.IO) {
        val showNonQuestionNotes = prefs.getBoolean(Prefs.SHOW_NOTES_NOT_PHRASED_AS_QUESTIONS, false)
        if (showNonQuestionNotes) {
            osmNoteQuestController.makeAllInvisibleVisible()
        } else {
            val hideQuests = mutableListOf<OsmNoteQuest>()
            for (quest in osmNoteQuestController.getAllVisible()) {
                if (!quest.probablyContainsQuestion()) {
                    hideQuests.add(quest)
                }
            }
            osmNoteQuestController.makeAllInvisible(hideQuests)
        }
    }
}
