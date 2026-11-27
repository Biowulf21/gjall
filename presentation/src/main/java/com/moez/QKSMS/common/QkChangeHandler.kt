/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.changehandler.AnimatorChangeHandler
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.common.util.extensions.dpToPx

class QkChangeHandler : AnimatorChangeHandler(250, true) {

    override fun getAnimator(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        toAddedToContainer: Boolean
    ): Animator {
        if ((container.context as? QkThemedActivity)?.expressive == true) return fadeThrough(from, to)

        val animatorSet = AnimatorSet()
        animatorSet.interpolator = DecelerateInterpolator()

        if (isPush) {
            if (from != null) {
                animatorSet.play(ObjectAnimator.ofFloat(from, View.TRANSLATION_X, -from.width.toFloat() / 4))
            }
            if (to != null) {
                to.translationZ = 8.dpToPx(to.context).toFloat()
                animatorSet.play(ObjectAnimator.ofFloat(to, View.TRANSLATION_X, to.width.toFloat(), 0f))
            }
        } else {
            if (from != null) {
                from.translationZ = 8.dpToPx(from.context).toFloat()
                animatorSet.play(ObjectAnimator.ofFloat(from, View.TRANSLATION_X, from.width.toFloat()))
            }
            if (to != null) {
                // Allow this to have a nice transition when coming off an aborted push animation
                val fromLeft = from?.translationX ?: 0f
                animatorSet.play(ObjectAnimator.ofFloat(to, View.TRANSLATION_X, fromLeft - to.width / 4, 0f))
            }
        }

        return animatorSet
    }

    /** Material fade-through: old screen fades out over the first 35%, new one fades in from 92% scale */
    private fun fadeThrough(from: View?, to: View?): Animator {
        val easing = FastOutSlowInInterpolator()
        return ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener {
                val t = it.animatedFraction
                val incoming = easing.getInterpolation(((t - 0.35f) / 0.65f).coerceIn(0f, 1f))
                from?.alpha = 1f - (t / 0.35f).coerceAtMost(1f)
                to?.alpha = incoming
                to?.scaleX = 0.92f + 0.08f * incoming
                to?.scaleY = to?.scaleX ?: 1f
            }
        }
    }

    override fun resetFromView(from: View) {
        from.translationX = 0f
        from.translationZ = 0f
        from.alpha = 1f
        from.scaleX = 1f
        from.scaleY = 1f
    }

    override fun copy(): ControllerChangeHandler {
        return QkChangeHandler()
    }

}
