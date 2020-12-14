package de.westnordost.streetcomplete.view.insets_animation

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.RequiresApi
import androidx.core.graphics.Insets
import de.westnordost.streetcomplete.ktx.setMargins
import de.westnordost.streetcomplete.ktx.setOnApplyWindowInsetsCompatListener
import de.westnordost.streetcomplete.ktx.toCompatInsets

/** Make the keyboard appear and disappear smoothly. Must be set on both
 *  setOnApplyWindowInsetsListener and setWindowInsetsAnimationCallback */
@RequiresApi(Build.VERSION_CODES.R)
class ImeInsetsAnimationCallback(
    private val view: View,
    private val onNewInsets: View.(insets: Insets) -> Unit
) : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE), View.OnApplyWindowInsetsListener {
    private var isAnimating = false
    private var prevInsets: WindowInsets? = null

    override fun onApplyWindowInsets(v: View, windowInsets: WindowInsets): WindowInsets {
        prevInsets = windowInsets
        if (!isAnimating) applyNewInsets(windowInsets)
        return windowInsets
    }

    override fun onPrepare(animation: WindowInsetsAnimation) {
        if (animation.typeMask and WindowInsets.Type.ime() != 0) {
            isAnimating = true
        }
    }

    override fun onProgress(insets: WindowInsets, runningAnims: List<WindowInsetsAnimation>): WindowInsets {
        applyNewInsets(insets)
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimation) {
        if (isAnimating && (animation.typeMask and WindowInsets.Type.ime()) != 0) {
            isAnimating = false
            prevInsets?.let { view.dispatchApplyWindowInsets(it) }
        }
    }

    private fun applyNewInsets(insets: WindowInsets) {
        view.onNewInsets(insets.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.systemBars())
            .toCompatInsets())
    }
}

fun View.respectSystemInsets(
    onNewInsets: View.(insets: Insets) -> Unit = { setMargins(it.left, it.top, it.right, it.bottom) }
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val imeAnimationCallback = ImeInsetsAnimationCallback(this, onNewInsets)
        setOnApplyWindowInsetsListener(imeAnimationCallback)
        setWindowInsetsAnimationCallback(imeAnimationCallback)
    } else {
        setOnApplyWindowInsetsCompatListener { v, windowInsets ->
            v.onNewInsets(windowInsets.systemWindowInsets)
            windowInsets
        }
    }
}
