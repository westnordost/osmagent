package de.westnordost.streetcomplete.user

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.OsmNotesModule
import de.westnordost.streetcomplete.data.quest.UnsyncedChangesCountListener
import de.westnordost.streetcomplete.data.quest.UnsyncedChangesCountSource
import de.westnordost.streetcomplete.data.user.*
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsDao
import de.westnordost.streetcomplete.ktx.createBitmap
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import javax.inject.Inject

/** Shows the user profile: username, avatar, star count and a hint regarding unpublished changes */
class ProfileFragment : Fragment(R.layout.fragment_profile),
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject internal lateinit var userController: UserController
    @Inject internal lateinit var userStore: UserStore
    @Inject internal lateinit var questStatisticsDao: QuestStatisticsDao
    @Inject internal lateinit var countryStatisticsDao: CountryStatisticsDao
    @Inject internal lateinit var userAchievementsDao: UserAchievementsDao
    @Inject internal lateinit var unsyncedChangesCountSource: UnsyncedChangesCountSource

    private lateinit var anonAvatar: Bitmap

    private val unsyncedChangesCountListener = object : UnsyncedChangesCountListener {
        override fun onUnsyncedChangesCountIncreased() { launch(Dispatchers.Main) { updateUnpublishedQuestsText() } }
        override fun onUnsyncedChangesCountDecreased() { launch(Dispatchers.Main) { updateUnpublishedQuestsText() } }
    }
    private val questStatisticsDaoListener = object : QuestStatisticsDao.Listener {
        override fun onAddedOne(questType: String) { launch(Dispatchers.Main) { updateSolvedQuestsText() }}
        override fun onSubtractedOne(questType: String) { launch(Dispatchers.Main) { updateSolvedQuestsText() } }
        override fun onReplacedAll() { launch(Dispatchers.Main) { updateSolvedQuestsText() } }
    }
    private val userStoreUpdateListener = object : UserStore.UpdateListener {
        override fun onUserDataUpdated() { launch(Dispatchers.Main) { updateUserName() } }
    }
    private val userAvatarListener = object : UserAvatarListener {
        override fun onUserAvatarUpdated() { launch(Dispatchers.Main) { updateAvatar() } }
    }

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        anonAvatar = context.getDrawable(R.drawable.ic_osm_anon_avatar)!!.createBitmap()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logoutButton.setOnClickListener {
            userController.logOut()
        }
        profileButton.setOnClickListener {
            openUrl("https://www.openstreetmap.org/user/" + userStore.userName)
        }
    }

    override fun onStart() {
        super.onStart()

        launch {
            userStore.addListener(userStoreUpdateListener)
            userController.addUserAvatarListener(userAvatarListener)
            questStatisticsDao.addListener(questStatisticsDaoListener)
            unsyncedChangesCountSource.addListener(unsyncedChangesCountListener)

            updateUserName()
            updateAvatar()
            updateSolvedQuestsText()
            updateUnpublishedQuestsText()
            updateDaysActiveText()
            updateGlobalRankText()
            updateLocalRankText()
            updateAchievementLevelsText()
        }
    }

    override fun onStop() {
        super.onStop()
        unsyncedChangesCountSource.removeListener(unsyncedChangesCountListener)
        questStatisticsDao.removeListener(questStatisticsDaoListener)
        userStore.removeListener(userStoreUpdateListener)
        userController.removeUserAvatarListener(userAvatarListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    private fun updateUserName() {
        userNameTextView.text = userStore.userName
    }

    private fun updateAvatar() {
        val cacheDir = OsmNotesModule.getAvatarsCacheDirectory(requireContext())
        val avatarFile = File(cacheDir.toString() + File.separator + userStore.userId)
        val avatar = if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.path) else anonAvatar
        userAvatarImageView.setImageBitmap(avatar)
    }

    private suspend fun updateSolvedQuestsText() {
        solvedQuestsText.text = withContext(Dispatchers.IO) { questStatisticsDao.getTotalAmount().toString() }
    }

    private suspend fun updateUnpublishedQuestsText() {
        val unsyncedChanges = withContext(Dispatchers.IO) { unsyncedChangesCountSource.count }
        unpublishedQuestsText.text = getString(R.string.unsynced_quests_description, unsyncedChanges)
        unpublishedQuestsText.isGone = unsyncedChanges <= 0
    }

    private fun updateDaysActiveText() {
        val daysActive = userStore.daysActive
        daysActiveContainer.isGone = daysActive <= 0
        daysActiveText.text = daysActive.toString()
    }

    private fun updateGlobalRankText() {
        val rank = userStore.rank
        globalRankContainer.isGone = rank <= 0 || questStatisticsDao.getTotalAmount() <= 100
        globalRankText.text = "#$rank"
    }

    private suspend fun updateLocalRankText() {
        val statistics = withContext(Dispatchers.IO) {
            countryStatisticsDao.getCountryWithBiggestSolvedCount()
        }
        if (statistics == null) localRankContainer.isGone = true
        else {
            val shouldShow = statistics.rank != null && statistics.rank > 0 && statistics.solvedCount > 50
            val countryLocale = Locale("", statistics.countryCode)
            localRankContainer.isGone = !shouldShow
            localRankText.text = "#${statistics.rank}"
            localRankLabel.text = getString(R.string.user_profile_local_rank, countryLocale.displayCountry)
        }
    }

    private suspend fun updateAchievementLevelsText() {
        val levels = withContext(Dispatchers.IO) { userAchievementsDao.getAll().values.sum() }
        achievementLevelsContainer.isGone = levels <= 0
        achievementLevelsText.text = "$levels"
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
