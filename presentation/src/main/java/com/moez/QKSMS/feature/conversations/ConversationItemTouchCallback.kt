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
package dev.octoshrimpy.quik.feature.conversations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class ConversationItemTouchCallback @Inject constructor(
    colors: Colors,
    disposables: CompositeDisposable,
    prefs: Preferences,
    private val context: Context
) : ItemTouchHelper.SimpleCallback(0, 0) {

    val swipes: Subject<Pair<Long, Int>> = PublishSubject.create()

    /**
     * Setting the adapter allows us to animate back to the original position
     */
    var adapter: RecyclerView.Adapter<*>? = null

    private val backgroundPaint = Paint()
    private var rightAction = 0
    private var swipeRightIcon: Bitmap? = null
    private var leftAction = 0
    private var swipeLeftIcon: Bitmap? = null

    private val iconLength = 24.dpToPx(context)
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val deleteColor = context.getColor(R.color.swipeDelete)
    private val rect = RectF()
    private var themeColor = 0
    private var armed = false
    private var armedAt = 0L

    init {
        disposables += colors.themeObservable()
                .doOnNext { theme -> themeColor = theme.theme }
                .subscribeOn(Schedulers.io())
                .subscribe()

        disposables += Observables
                .combineLatest(prefs.swipeRight.asObservable(), prefs.swipeLeft.asObservable(), colors.themeObservable()
                ) { right, left, theme ->
                    rightAction = right
                    swipeRightIcon = iconForAction(right, Color.WHITE)
                    leftAction = left
                    swipeLeftIcon = iconForAction(left, Color.WHITE)
                    setDefaultSwipeDirs((if (right == Preferences.SWIPE_ACTION_NONE) 0 else ItemTouchHelper.RIGHT)
                            or (if (left == Preferences.SWIPE_ACTION_NONE) 0 else ItemTouchHelper.LEFT))
                }
                .subscribeOn(Schedulers.io())
                .subscribe()
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    /**
     * Google Messages-style swipe: a flat, full-width tonal colour fills the space the row leaves
     * behind, with the action icon pinned near the edge. The icon fades in with the drag and pops
     * (0.85 to 1.0) with a haptic tick when the release would commit.
     */
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val threshold = getSwipeThreshold(viewHolder) * itemView.width
            val action = if (dX > 0) rightAction else leftAction

            val armed = abs(dX) >= threshold
            if (isCurrentlyActive && armed != this.armed && dX != 0f) {
                this.armed = armed
                armedAt = SystemClock.uptimeMillis()
                itemView.performHapticFeedback(when {
                    Build.VERSION.SDK_INT >= 34 && armed -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                    Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                    armed -> HapticFeedbackConstants.CONTEXT_CLICK
                    else -> HapticFeedbackConstants.CLOCK_TICK
                })
            }
            if (dX == 0f) this.armed = false

            if (dX != 0f) {
                val context = itemView.context
                val isDelete = action == Preferences.SWIPE_ACTION_DELETE
                // Colour tracks the finger 1:1 (no easing on a tracked value). p² keeps it muted early
                // and saturates right at the commit point; past the threshold it stays fully saturated.
                val progress = min(1f, abs(dX) / threshold)
                val ramp = progress * progress
                val muted = context.resolveThemeColor(R.attr.surfaceContainerHigh)
                val full = if (isDelete) deleteColor else themeColor
                backgroundPaint.color = ColorUtils.blendARGB(muted, full, ramp)
                val mutedIcon = context.resolveThemeColor(android.R.attr.textColorSecondary)
                // Icon goes white twice as fast so it never sits grey-on-red mid-drag
                val iconColor = ColorUtils.blendARGB(mutedIcon, Color.WHITE, min(1f, ramp * 2f))

                // Edge to edge, ignoring the row's own inset, like Google Messages
                if (dX > 0) {
                    rect.set(0f, itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                } else {
                    rect.set(itemView.right + dX, itemView.top.toFloat(), recyclerView.width.toFloat(), itemView.bottom.toFloat())
                }
                c.drawRect(rect, backgroundPaint)

                val icon = if (dX > 0) swipeRightIcon else swipeLeftIcon
                if (icon != null) {
                    // Icon eases between 0.85 and 1.0 over 120ms whenever the armed state flips
                    val t = min(1f, (SystemClock.uptimeMillis() - armedAt) / 120f)
                    val eased = 1f - (1f - t) * (1f - t)
                    val scale = if (this.armed) 0.85f + 0.15f * eased else 1f - 0.15f * eased
                    if (t < 1f) recyclerView.postInvalidateOnAnimation()

                    val edge = iconLength.toFloat() // 24dp from the screen edge to the icon
                    val cx = if (dX > 0) edge + iconLength / 2f else recyclerView.width - edge - iconLength / 2f
                    val cy = (itemView.top + itemView.bottom) / 2f
                    iconPaint.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                    iconPaint.alpha = (min(1f, abs(dX) / (edge * 2 + iconLength)) * 255).toInt()
                    c.save()
                    c.clipRect(rect)
                    c.scale(scale, scale, cx, cy)
                    c.drawBitmap(icon, cx - icon.width / 2f, cy - icon.height / 2f, iconPaint)
                    c.restore()
                }
            }

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        swipes.onNext(Pair(viewHolder.itemId, direction))

        // This will trigger the animation back to neutral state
        val action = if (direction == ItemTouchHelper.RIGHT) rightAction else leftAction
        if (action != Preferences.SWIPE_ACTION_ARCHIVE && action != Preferences.SWIPE_ACTION_DELETE) {
            adapter?.notifyItemChanged(viewHolder.adapterPosition)
        }
    }

    private fun iconForAction(action: Int, tint: Int): Bitmap? {
        val res = when (action) {
            Preferences.SWIPE_ACTION_ARCHIVE -> R.drawable.ic_archive_white_24dp
            Preferences.SWIPE_ACTION_DELETE -> R.drawable.ic_delete_white_24dp
            Preferences.SWIPE_ACTION_BLOCK -> R.drawable.ic_block_white_24dp
            Preferences.SWIPE_ACTION_CALL -> R.drawable.ic_call_white_24dp
            Preferences.SWIPE_ACTION_READ -> R.drawable.ic_check_white_24dp
            Preferences.SWIPE_ACTION_UNREAD -> R.drawable.ic_markunread_black_24dp
            Preferences.SWIPE_ACTION_SPEAK -> R.drawable.ic_speaker_black_24dp
            else -> null
        }

        return res?.let(context.resources::getDrawable)
                ?.apply { setTint(tint) }
                ?.toBitmap(iconLength, iconLength)
    }

}