package de.westnordost.streetcomplete.user

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnStart
import androidx.core.net.toUri
import androidx.core.view.isGone
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.tryStartActivity
import kotlinx.android.synthetic.main.fragment_country_info_dialog.*
import java.util.*
import kotlin.math.min
import kotlin.math.pow

/** Shows the details for a certain quest type as a fake-dialog. */
class CountryInfoFragment : AbstractInfoFakeDialogFragment(R.layout.fragment_country_info_dialog) {

    // need to keep the animators here to be able to clear them on cancel
    private var counterAnimation: ValueAnimator? = null
    private var circularRevealAnimator: ObjectAnimator? = null

    /* ---------------------------------------- Lifecycle --------------------------------------- */

    override fun onDestroy() {
        super.onDestroy()
        counterAnimation?.cancel()
        counterAnimation = null
        circularRevealAnimator?.cancel()
        circularRevealAnimator = null
    }

    /* ---------------------------------------- Interface --------------------------------------- */

    fun show(countryCode: String, questCount: Int, rank: Int?, countryFlagBubbleView: View) {
        if(!show(countryFlagBubbleView)) return
        circularRevealAnimator?.cancel()
        val revealAnim = createCircularRevealAnimator()
        revealAnim.start()
        circularRevealAnimator = revealAnim

        val flag = requireContext().getDrawable(getFlagResId(countryCode))!!
        titleImageView.setImageDrawable(flag)

        titleView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val flagAspectRatio = flag.intrinsicWidth.toFloat() / flag.intrinsicHeight.toFloat()
                val aspectRatio = view.width.toFloat() / view.height.toFloat()
                val flagWidth: Int
                val flagHeight: Int
                if (flagAspectRatio > aspectRatio) {
                    flagWidth = view.width
                    flagHeight = (view.width / flagAspectRatio).toInt()
                } else {
                    flagWidth = (view.height * flagAspectRatio).toInt()
                    flagHeight = view.height
                }
                val xDiff = view.width - flagWidth
                val yDiff = view.height - flagHeight
                // oval because the shadow is there during the whole animation, rect would look very odd
                // (an oval less so)
                outline.setOval(xDiff / 2, yDiff / 2, flagWidth + xDiff / 2, flagHeight + yDiff / 2)
            }
        }

        val countryLocale = Locale("", countryCode)

        solvedQuestsText.text = ""
        val scale = (0.4 + min( questCount / 100.0, 1.0)*0.6).toFloat()
        solvedQuestsContainer.visibility = View.INVISIBLE
        solvedQuestsContainer.scaleX = scale
        solvedQuestsContainer.scaleY = scale

        val shouldShowRank = rank != null && rank < 500 && questCount > 50
        countryRankTextView.isGone = !shouldShowRank
        if (shouldShowRank) {
            countryRankTextView.text = resources.getString(
                R.string.user_statistics_country_rank, rank, countryLocale.displayCountry
            )
        }

        wikiLinkButton.text = resources.getString(R.string.user_statistics_country_wiki_link, countryLocale.displayCountry)
        wikiLinkButton.setOnClickListener {
            openUrl("https://wiki.openstreetmap.org/wiki/${countryLocale.getDisplayCountry(Locale.UK)}")
        }

        counterAnimation?.cancel()
        val anim = ValueAnimator.ofInt(0, questCount)

        anim.doOnStart { solvedQuestsContainer.visibility = View.VISIBLE }
        anim.duration = 300 + (questCount * 500.0).pow(0.6).toLong()
        anim.addUpdateListener { solvedQuestsText?.text = it.animatedValue.toString() }
        anim.interpolator = DecelerateInterpolator()
        anim.startDelay = ANIMATION_TIME_IN_MS
        anim.start()
        counterAnimation = anim
    }

    override fun dismiss(): Boolean {
        if (!super.dismiss()) return false

        circularRevealAnimator?.cancel()
        val revealAnim = createCircularHideAnimator()
        revealAnim.start()
        circularRevealAnimator = revealAnim
        return true
    }

    private fun getFlagResId(countryCode: String): Int {
        val lowerCaseCountryCode = countryCode.toLowerCase(Locale.US).replace('-', '_')
        return resources.getIdentifier("ic_flag_$lowerCaseCountryCode", "drawable", requireContext().packageName)
    }

    private fun createCircularRevealAnimator(): ObjectAnimator {
        val anim = ObjectAnimator.ofFloat(titleView, "circularity", 1f, 0f)
        anim.interpolator = AccelerateInterpolator()
        anim.duration = ANIMATION_TIME_IN_MS
        return anim
    }

    private fun createCircularHideAnimator(): ObjectAnimator {
        val anim = ObjectAnimator.ofFloat(titleView, "circularity", 0f, 1f)
        anim.interpolator = DecelerateInterpolator()
        anim.duration = ANIMATION_TIME_OUT_MS
        return anim
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }
}
