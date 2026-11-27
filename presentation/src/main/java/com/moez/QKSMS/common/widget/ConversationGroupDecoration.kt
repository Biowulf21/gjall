package dev.octoshrimpy.quik.common.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import java.util.WeakHashMap
import kotlin.math.hypot

/**
 * Expressive list rows: the rows render as one group of stacked cards (28dp outer corners, 4dp
 * inner corners, 3dp gap). While a row is held its corners round up to the outer radius over the
 * long-press timeout; selected (activated) rows keep that shape.
 */
class ConversationGroupDecoration(
    context: Context,
    private val sideInset: Int = 0 // for rows whose layout has no side margins of their own
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private class Row(val shape: MaterialShapeDrawable) {
        var pill = 0f
        var animator: ValueAnimator? = null
        var top = -1f
        var bottom = -1f
    }

    private val gap = 3.dpToPx(context)
    private val outer = 28.dpToPx(context).toFloat()
    private val inner = 4.dpToPx(context).toFloat()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val fill = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
        intArrayOf(context.resolveThemeColor(R.attr.colorSecondaryContainer),
            context.resolveThemeColor(R.attr.colorSurfaceContainer)))
    private val highlight = ColorStateList.valueOf(context.resolveThemeColor(R.attr.colorControlHighlight))
    private val rows = WeakHashMap<View, Row>()

    private var held: View? = null
    private var downX = 0f
    private var downY = 0f

    fun attachTo(recyclerView: RecyclerView) {
        recyclerView.addItemDecoration(this)
        recyclerView.addOnItemTouchListener(this)
    }

    private fun row(view: View): Row = rows.getOrPut(view) {
        val shape = MaterialShapeDrawable().apply { fillColor = fill }
        view.background = shape
        // Rectangular ripple, clipped to the card by the outline
        view.foreground = RippleDrawable(highlight, null, ColorDrawable(Color.WHITE))
        view.clipToOutline = true
        Row(shape)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) > 0) outRect.top = gap
        outRect.left = sideInset
        outRect.right = sideInset
    }

    override fun onDraw(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val row = row(child)
            val pill = if (child.isActivated) 1f else row.pill
            // Held/selected rows round up to the outer radius, not a full pill: tall rows read as capsules
            val half = minOf(child.height / 2f, outer)
            val top = lerp(if (position == 0) outer else inner, half, pill)
            val bottom = lerp(if (position == count - 1) outer else inner, half, pill)
            if (top == row.top && bottom == row.bottom) continue

            row.top = top
            row.bottom = bottom
            row.shape.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(top).setTopRightCornerSize(top)
                .setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom)
                .build()
            child.invalidateOutline()
        }
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                held = rv.findChildViewUnder(e.x, e.y)?.also { view ->
                    animate(rv, view, 1f, ViewConfiguration.getLongPressTimeout().toLong(), true)
                }
            }
            MotionEvent.ACTION_MOVE -> if (hypot(e.x - downX, e.y - downY) > touchSlop) release(rv)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(rv)
        }
        return false // observe only; clicks, long-clicks and swipes still go to the rows
    }

    private fun release(rv: RecyclerView) {
        held?.let { view -> animate(rv, view, 0f, 150, false) }
        held = null
    }

    private fun animate(rv: RecyclerView, view: View, to: Float, duration: Long, linear: Boolean) {
        val row = row(view)
        row.animator?.cancel()
        row.animator = ValueAnimator.ofFloat(row.pill, to).apply {
            this.duration = duration
            interpolator = if (linear) LinearInterpolator() else FastOutSlowInInterpolator()
            addUpdateListener {
                row.pill = it.animatedValue as Float
                rv.invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

}
