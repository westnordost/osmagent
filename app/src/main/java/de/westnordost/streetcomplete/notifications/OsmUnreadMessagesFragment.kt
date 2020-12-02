package de.westnordost.streetcomplete.notifications

import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.util.SoundFx
import kotlinx.android.synthetic.main.fragment_unread_osm_message.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fragment that shows a notification that the user has X unread messages in his OSM inbox */
class OsmUnreadMessagesFragment : DialogFragment(),
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject lateinit var soundFx: SoundFx

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_CustomDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_unread_osm_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // hide first, to avoid flashing
        mailContainer.alpha = 0.0f
        dialogContainer.setOnClickListener { dismiss() }
        readMailButton.setOnClickListener {
            openUrl("https://www.openstreetmap.org/messages/inbox")
            dismiss()
        }
        val unreadMessagesCount = arguments?.getInt(ARG_UNREAD_MESSAGE_COUNT, 0) ?: 0
        unreadMessagesTextView.text = getString(R.string.unread_messages_message, unreadMessagesCount)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.setOnShowListener { startAnimation() }
        // we want to show a highly custom dialog here with no frame. Without this, the dialog's
        // content is restricted to wrap content, but we want to use whole screen here (for animation)
        dialog?.window?.setLayout(MATCH_PARENT, MATCH_PARENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    private fun startAnimation() {
        val ctx = requireContext()

        launch { soundFx.play(R.raw.sliding_envelope) }

        mailFrontImageView.alpha = 0f

        speechbubbleContentContainer.alpha = 0.0f
        speechbubbleContentContainer.visibility = View.VISIBLE
        speechbubbleContentContainer.scaleX = 0.8f
        speechbubbleContentContainer.scaleY = 0.8f
        speechbubbleContentContainer.translationY = 140f.toPx(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (mailOpenImageView.drawable as? AnimatedVectorDrawable)?.reset()
        }

        mailContainer.rotation = -40f
        mailContainer.rotationY = -45f
        mailContainer.alpha = 0.2f
        mailContainer.translationX = (-400f).toPx(ctx)
        mailContainer.translationY = (60f).toPx(ctx)
        mailContainer.animate()
            .setDuration(400)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .rotation(0f).rotationY(0f)
            .alpha(1f)
            .translationX(0f).translationY(0f)
            .withEndAction {
                (mailOpenImageView.drawable as? AnimatedVectorDrawable)?.start()

                mailFrontImageView.animate()
                    .setDuration(100)
                    .setStartDelay(100)
                    .alpha(1f)
                    .start()


                speechbubbleContentContainer.animate()
                    .withStartAction {
                        speechbubbleContentContainer.alpha = 0.4f
                    }
                    .setStartDelay(200)
                    .setDuration(300)
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .translationY(0f)
                    .start()
            }
            .start()
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }

    companion object {
        private const val ARG_UNREAD_MESSAGE_COUNT = "unread_message_count"

        fun create(unreadMessagesCount: Int): OsmUnreadMessagesFragment {
            val args = bundleOf(ARG_UNREAD_MESSAGE_COUNT to unreadMessagesCount)
            val f = OsmUnreadMessagesFragment()
            f.arguments = args
            return f
        }
    }
}
