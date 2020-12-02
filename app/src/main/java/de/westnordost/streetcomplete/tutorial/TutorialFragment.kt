package de.westnordost.streetcomplete.tutorial

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import androidx.fragment.app.Fragment
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.toDp
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.location.LocationState
import kotlinx.android.synthetic.main.fragment_tutorial.*

/** Shows a short tutorial for first-time users */
class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentPage: Int = 0

    interface Listener {
        fun onFinishedTutorial()
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFittingToSystemWindowInsets()
        updateIndicatorDots()

        nextButton.setOnClickListener {
            when(currentPage) {
                0 -> {
                    currentPage = 1
                    step1Transition()
                }
                1 -> {
                    currentPage = 2
                    step2Transition()
                }
                MAX_PAGE_INDEX -> {
                    nextButton.isEnabled = false
                    listener?.onFinishedTutorial()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun setupFittingToSystemWindowInsets() {
        view?.setOnApplyWindowInsetsListener { v: View?, insets: WindowInsets ->
            view?.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets
        }
    }

    private fun step1Transition() {
        val ctx = requireContext()

        updateIndicatorDots()

        // magnifier flies towards viewer and fades out
        magnifierImageView.animate()
            .setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .scaleX(6f).scaleY(6f)
            .alpha(0f)
            .start()


        // map zooms in and tilts
        val mapTranslate = (-50f).toPx(ctx)
        val mapRotate = 50f
        val mapScale = 1.5f

        mapImageView.animate()
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .rotationX(mapRotate)
            .scaleY(mapScale).scaleX(mapScale)
            .translationY(mapTranslate)
            .start()

        mapLightingImageView.animate()
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .rotationX(mapRotate)
            .alpha(0f)
            .scaleY(mapScale).scaleX(mapScale)
            .translationY(mapTranslate)
            .start()

        // 1st text fade out
        tutorialStepIntro.animate()
            .setDuration(300)
            .alpha(0f)
            .translationY(100f.toDp(ctx))
            .withEndAction { tutorialStepIntro.visibility = View.GONE }
            .start()

        // 2nd text fade in
        tutorialStepSolvingQuests.translationY = (-100f).toDp(ctx)
        tutorialStepSolvingQuests.animate()
            .withStartAction { tutorialStepSolvingQuests.visibility = View.VISIBLE }
            .setStartDelay(400)
            .setDuration(300)
            .alpha(1f)
            .translationY(0f)
            .start()

        // flashing GPS button appears
        tutorialGpsButton.state = LocationState.SEARCHING
        tutorialGpsButton.animate()
            .setStartDelay(200)
            .alpha(1f)
            .setDuration(200)
            .start()

        mainHandler.postDelayed({
            // ...and after a few seconds, stops flashing
            tutorialGpsButton?.state = LocationState.UPDATING

            // quest pins fall into place
            listOf(questPin1, questPin2, questPin3).forEachIndexed { index, pin ->
                pin.translationY = (-200f).toDp(ctx)
                pin.animate()
                    .setStartDelay(1200L + index * 400L)
                    .setInterpolator(BounceInterpolator())
                    .setDuration(400)
                    .translationY(0f)
                    .alpha(1f)
                    .start()
            }

        }, 3000L)
    }

    private fun step2Transition() {
        val ctx = requireContext()

        updateIndicatorDots()
        nextButton.setText(R.string.letsgo)

        // 2nd text fade out
        tutorialStepSolvingQuests.animate()
            .setStartDelay(0)
            .setDuration(300)
            .alpha(0f)
            .translationY(100f.toDp(ctx))
            .withEndAction { tutorialStepSolvingQuests.visibility = View.GONE }
            .start()

        // 3rd text fade in
        tutorialStepStaySafe.translationY = (-100f).toDp(ctx)
        tutorialStepStaySafe.animate()
            .withStartAction { tutorialStepStaySafe.visibility = View.VISIBLE }
            .setStartDelay(400)
            .setDuration(300)
            .alpha(1f)
            .translationY(0f)
            .start()

        // quest pins fade out
        listOf(questPin1, questPin2, questPin3).forEach { pin ->
            pin.animate()
                .setStartDelay(0)
                .setInterpolator(AccelerateInterpolator())
                .setDuration(300)
                .alpha(0f)
                .start()
        }

        mainHandler.postDelayed({
            // checkmark fades in and animates

            checkmarkView.animate()
                .setDuration(600)
                .alpha(1f)
                .start()

            (checkmarkView.drawable as? AnimatedVectorDrawable)?.start()
        }, 1400L)
    }

    private fun updateIndicatorDots() {
        listOf(dot1,dot2,dot3).forEachIndexed { index, dot ->
            dot.setImageResource(
                if(currentPage == index) R.drawable.indicator_dot_selected
                else R.drawable.indicator_dot_default
            )
        }
    }

    companion object {
        private const val MAX_PAGE_INDEX = 2
    }
}
