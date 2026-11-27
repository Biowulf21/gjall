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
package dev.octoshrimpy.quik.common.util.extensions

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dev.octoshrimpy.quik.R
import androidx.viewpager.widget.ViewPager

var ViewGroup.animateLayoutChanges: Boolean
    get() = layoutTransition != null
    set(value) {
        layoutTransition = if (value) LayoutTransition() else null
    }

fun EditText.showKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun EditText.hideKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun ImageView.setTint(color: Int?) {
    imageTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun TextView.setTint(color: Int?) {
    foregroundTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun ProgressBar.setTint(color: Int?) {
    indeterminateTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
    progressTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun View.setBackgroundTint(color: Int?) {

    // API 21 doesn't support this

    backgroundTintList =
        if (color == null) null
        else ColorStateList.valueOf(color)
}

fun View.setPadding(left: Int? = null, top: Int? = null, right: Int? = null, bottom: Int? = null) {
    setPadding(left ?: paddingLeft, top ?: paddingTop, right ?: paddingRight, bottom ?: paddingBottom)
}

fun View.setVisible(visible: Boolean, invisible: Int = View.GONE) {
    visibility = if (visible) View.VISIBLE else invisible
}

/**
 * If a view captures clicks at all, then the parent won't ever receive touch events. This is a
 * problem when we're trying to capture link clicks, but tapping or long pressing other areas of
 * the view no longer work. Also problematic when we try to long press on an image in the message
 * view
 */

class CancelableSimpleOnGestureListener(view: View, parentView: View) : SimpleOnGestureListener() {
    private var lastUpEvent: MotionEvent? = null
    private val parent = parentView
    private val thisView = view
    private var textInitiallySelectable = false

    init {
        if (thisView is TextView)
            textInitiallySelectable = thisView.isTextSelectable
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (lastUpEvent !== null) {
            parent.onTouchEvent(e)
            parent.onTouchEvent(lastUpEvent)
            lastUpEvent?.recycle()
            lastUpEvent = null
        }
        return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        lastUpEvent = MotionEvent.obtain(e)
        thisView.onTouchEvent(e)
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        thisView.onTouchEvent(e)
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        parent.onTouchEvent(e)
        // this is kinda odd but we have to 'bounce' the text selectable value so it doesn't
        // start selecting text on a long press, but will start selecting it on the next double-tap
        if (thisView is TextView) {
            thisView.setTextIsSelectable(false)
            thisView.setTextIsSelectable(textInitiallySelectable)
        }
    }
}

fun View.forwardTouches(parent: View): CancelableSimpleOnGestureListener {
    val gestureListener = CancelableSimpleOnGestureListener(this, parent)

    setOnTouchListener(object : OnTouchListener {
        val gestureDetector = GestureDetector(parent.context, gestureListener)

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            return gestureDetector.onTouchEvent(e)
        }
    })

    return gestureListener
}

fun ViewPager.addOnPageChangeListener(listener: (Int) -> Unit) {
    addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            listener(position)
        }
    })
}

fun RecyclerView.scrapViews() {
    recycledViewPool.clear()
    adapter?.notifyDataSetChanged()
}

/** Heimdall-style floating snackbar: inset, 16dp corners, raised surface, accent action. */
/** [endInsetDp] keeps it clear of a FAB in the bottom-end corner. */
fun Snackbar.heimdallStyle(accent: Int, endInsetDp: Int = 12): Snackbar {
    val context = view.context
    val density = context.resources.displayMetrics.density
    val surface = context.resolveThemeColor(R.attr.surfaceContainerHigh)
    view.background = GradientDrawable().apply {
        cornerRadius = 16 * density
        setColor(surface)
    }
    (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
        val margin = (12 * density).toInt()
        params.setMargins(margin, margin, (endInsetDp * density).toInt(), margin)
        view.layoutParams = params
    }
    // Material 1.0.0 hardcodes white snackbar text; pick ink from the surface we just set
    val ink = context.getColor(if (ColorUtils.calculateLuminance(surface) > 0.5) R.color.textPrimary else R.color.textPrimaryDark)
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.setTextColor(ink)
    setActionTextColor(accent)
    return this
}
