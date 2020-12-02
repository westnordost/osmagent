package de.westnordost.streetcomplete.quests.note_discussion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.osmapi.notes.NoteComment
import de.westnordost.osmapi.user.User
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osmnotes.OsmNotesModule
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestController
import de.westnordost.streetcomplete.ktx.createBitmap
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher
import de.westnordost.streetcomplete.view.CircularOutlineProvider
import de.westnordost.streetcomplete.view.ListAdapter
import de.westnordost.streetcomplete.view.RoundRectOutlineProvider
import kotlinx.android.synthetic.main.fragment_quest_answer.*
import kotlinx.android.synthetic.main.quest_buttonpanel_note_discussion.*
import kotlinx.android.synthetic.main.quest_note_discussion_content.*
import kotlinx.android.synthetic.main.quest_note_discussion_item.view.*
import java.io.File
import java.util.*
import javax.inject.Inject

class NoteDiscussionForm : AbstractQuestAnswerFragment<NoteAnswer>() {

    override val contentLayoutResId = R.layout.quest_note_discussion_content
    override val buttonsResId = R.layout.quest_buttonpanel_note_discussion
    override val defaultExpanded = false

    private lateinit var anonAvatar: Bitmap

    @Inject internal lateinit var osmNoteQuestController: OsmNoteQuestController

    private val attachPhotoFragment get() =
        childFragmentManager.findFragmentById(R.id.attachPhotoFragment) as? AttachPhotoFragment

    private val noteText: String get() = noteInput?.text?.toString().orEmpty().trim()

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        doneButton.setOnClickListener { onClickOk() }
        noButton.setOnClickListener { skipQuest() }

        noteInput.addTextChangedListener(TextChangedWatcher { updateDoneButtonEnablement() })

        otherAnswersButton.visibility = View.GONE

        updateDoneButtonEnablement()

        anonAvatar = requireContext().getDrawable(R.drawable.ic_osm_anon_avatar)!!.createBitmap()

        inflateNoteDiscussion(osmNoteQuestController.get(questId)!!.note.comments)

        if (savedInstanceState == null) {
            childFragmentManager.commit { add<AttachPhotoFragment>(R.id.attachPhotoFragment) }
        }
    }

    private fun inflateNoteDiscussion(comments: List<NoteComment>) {
        val discussionView = layoutInflater.inflate(R.layout.quest_note_discussion_items, scrollViewChild, false) as RecyclerView

        discussionView.isNestedScrollingEnabled = false
        discussionView.layoutManager = LinearLayoutManager(
            context,
            RecyclerView.VERTICAL,
            false
        )
        discussionView.adapter = NoteCommentListAdapter(comments)

        scrollViewChild.addView(discussionView, 0)
    }

    private fun onClickOk() {
        applyAnswer(NoteAnswer(noteText, attachPhotoFragment?.imagePaths))
    }

    override fun onDiscard() {
        attachPhotoFragment?.deleteImages()
    }

    override fun isRejectingClose(): Boolean {
        val f = attachPhotoFragment
        val hasPhotos = f != null && !f.imagePaths.isEmpty()
        return hasPhotos || noteText.isNotEmpty()
    }

    private fun updateDoneButtonEnablement() {
        doneButton.isEnabled = noteText.isNotEmpty()
    }


    private inner class NoteCommentListAdapter(list: List<NoteComment>) :
        ListAdapter<NoteComment>(list) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<NoteComment> {
            return NoteCommentViewHolder(
                layoutInflater.inflate(R.layout.quest_note_discussion_item, parent, false)
            )
        }
    }

    private inner class NoteCommentViewHolder(itemView: View) :
        ListAdapter.ViewHolder<NoteComment>(itemView) {

        init {
            val cornerRadius = resources.getDimension(R.dimen.speech_bubble_rounded_corner_radius)
            val margin = resources.getDimensionPixelSize(R.dimen.horizontal_speech_bubble_margin)
            val marginStart = -resources.getDimensionPixelSize(R.dimen.quest_form_speech_bubble_top_margin)
            itemView.commentStatusText.outlineProvider = RoundRectOutlineProvider(cornerRadius)

            val isRTL = itemView.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val marginLeft = if (isRTL) 0 else marginStart
            val marginRight = if (isRTL) marginStart else 0
            itemView.commentBubble.outlineProvider = RoundRectOutlineProvider(
                cornerRadius, marginLeft, margin, marginRight, margin
            )
            itemView.commentAvatarImageContainer.outlineProvider = CircularOutlineProvider
        }

        override fun onBind(comment: NoteComment) {
            val dateDescription = DateUtils.getRelativeTimeSpanString(comment.date.time, Date().time, MINUTE_IN_MILLIS)
            val userName = if (comment.user != null) comment.user.displayName else getString(R.string.quest_noteDiscussion_anonymous)

            val commentActionResourceId = comment.action.actionResourceId
            val hasNoteAction = commentActionResourceId != 0
            itemView.commentStatusText.isGone = !hasNoteAction
            if (hasNoteAction) {
                itemView.commentStatusText.text = getString(commentActionResourceId, userName, dateDescription)
            }

            val hasComment = !comment.text.isNullOrEmpty()
            itemView.commentView.isGone = !hasComment
            if (hasComment) {
                itemView.commentText.text = comment.text
                itemView.commentInfoText.text = getString(R.string.quest_noteDiscussion_comment2, userName, dateDescription)

                val bitmap = comment.user?.avatar ?: anonAvatar
                itemView.commentAvatarImage.setImageBitmap(bitmap)
            }
        }

        private val User.avatar: Bitmap? get() {
            val cacheDir = OsmNotesModule.getAvatarsCacheDirectory(requireContext())
            val file = File(cacheDir.toString() + File.separator + id)
            return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        }

        private val NoteComment.Action.actionResourceId get() = when (this) {
            NoteComment.Action.CLOSED -> R.string.quest_noteDiscussion_closed2
            NoteComment.Action.REOPENED -> R.string.quest_noteDiscussion_reopen2
            NoteComment.Action.HIDDEN -> R.string.quest_noteDiscussion_hide2
            else -> 0
        }
    }
}
