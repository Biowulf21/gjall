package dev.octoshrimpy.quik.common.widget

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor

/**
 * Expressive (Heimdall-style) restyle applied in place to any screen, so every page gets the same
 * treatment without forking its layout:
 * - toolbars: 72dp tall, large title, standard (containerless) icon buttons
 * - vertical lists of rows (PreferenceViews, or any clickable rows in a container tagged
 *   [R.id.expressive_rows]): grouped cards (28dp outer / 4dp inner corners, 3dp gap), round tonal
 *   icon badges, section headers as small pills, divider lines hidden
 * Idempotent: it runs on every global layout and only touches what changed.
 */
object ExpressiveStyler {

    /** Top-level row lists that become grouped cards (the inbox attaches its own) */
    private val cardLists = setOf(R.id.messages, R.id.numbers, R.id.conversations, R.id.filters)

    fun style(view: View, insideList: Boolean = false) {
        when (view) {
            is Toolbar -> styleToolbar(view)
            is LinearLayout -> if (view.orientation == LinearLayout.VERTICAL) styleRows(view)
            is RecyclerView -> if (!insideList && view.id in cardLists && view.getTag(R.id.expressive_styled) == null) {
                view.setTag(R.id.expressive_styled, true)
                ConversationGroupDecoration(view.context, sideInset = 12.dpToPx(view.context)).attachTo(view)
            }
        }
        if (view is ViewGroup) view.children.forEach { style(it, insideList || view is RecyclerView) }
    }

    private fun styleToolbar(toolbar: Toolbar) {
        val context = toolbar.context
        if (toolbar.getTag(R.id.expressive_styled) == null) {
            toolbar.setTag(R.id.expressive_styled, true)
            toolbar.updateLayoutParams { if (height > 0) height = 72.dpToPx(context) }
            if (toolbar.getTag(R.id.expressive_keep_title) == null) {
                toolbar.findViewById<TextView>(R.id.toolbarTitle)?.let { title ->
                    title.textSize = title.textSize / context.resources.displayMetrics.scaledDensity * 1.4f
                }
            }
        }
        // Toolbar actions stay standard M3 icon buttons (no container); the cookie shape is
        // reserved for the inbox compose FAB so it keeps its emphasis
    }

    private fun isRow(parent: ViewGroup, child: View) = child is PreferenceView ||
            (parent.getTag(R.id.expressive_rows) != null && child is ViewGroup && child.isClickable)

    private fun styleRows(list: LinearLayout) {
        val all = list.children.toList()
        val visible = all.filter { it.visibility != View.GONE }
        if (visible.count { isRow(list, it) } < 1) return
        val context = list.context
        val dp = { value: Int -> value.dpToPx(context) }

        // Split into groups of consecutive rows; headers, divider lines (hidden, but they still
        // mark a section break) and other views break a group
        val groups = mutableListOf<MutableList<View>>()
        var newGroup = true
        all.forEach { child ->
            when {
                child.javaClass == View::class.java -> {
                    child.visibility = View.GONE // divider line
                    newGroup = true
                }
                child.visibility == View.GONE -> Unit
                isRow(list, child) -> {
                    if (newGroup) groups += mutableListOf<View>()
                    groups.last() += child
                    newGroup = false
                }
                else -> {
                    if (child is TextView) header(child)
                    newGroup = true
                }
            }
        }

        groups.forEach { group ->
            group.forEachIndexed { index, row ->
                val before = visible.getOrNull(visible.indexOf(row) - 1)
                val top = when {
                    index > 0 -> dp(3)
                    before is TextView -> 0
                    before == null -> 0
                    else -> dp(12)
                }
                card(row, top, if (index == 0) 28f else 4f, if (index == group.lastIndex) 28f else 4f)
                badge(row)
            }
        }
    }

    private fun card(row: View, top: Int, topRadius: Float, bottomRadius: Float) {
        val context = row.context
        val density = context.resources.displayMetrics.density
        var shape = row.background as? MaterialShapeDrawable
        if (shape == null || row.getTag(R.id.expressive_styled) == null) {
            row.setTag(R.id.expressive_styled, true)
            shape = MaterialShapeDrawable().apply {
                fillColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                    intArrayOf(context.resolveThemeColor(R.attr.colorSecondaryContainer),
                        context.resolveThemeColor(R.attr.colorSurfaceContainer)))
            }
            row.background = shape
            row.foreground = RippleDrawable(
                ColorStateList.valueOf(context.resolveThemeColor(R.attr.colorControlHighlight)),
                null, ColorDrawable(Color.WHITE))
            row.clipToOutline = true
        }
        val model = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(topRadius * density).setTopRightCornerSize(topRadius * density)
            .setBottomLeftCornerSize(bottomRadius * density).setBottomRightCornerSize(bottomRadius * density)
            .build()
        if (shape.shapeAppearanceModel.topLeftCornerSize != model.topLeftCornerSize ||
            shape.shapeAppearanceModel.bottomLeftCornerSize != model.bottomLeftCornerSize) {
            shape.shapeAppearanceModel = model
            row.invalidateOutline()
        }
        val side = 12.dpToPx(context)
        (row.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.topMargin != top || lp.marginStart != side || lp.marginEnd != side) {
                row.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = top
                    bottomMargin = 0
                    marginStart = side
                    marginEnd = side
                }
            }
        }
    }

    /** Round tonal badge behind the row's leading icon */
    private fun badge(row: View) {
        val icon = (row as? ViewGroup)?.children?.firstOrNull { it is ImageView } as? ImageView ?: return
        if (icon.getTag(R.id.expressive_styled) != null || icon.visibility != View.VISIBLE) return
        icon.setTag(R.id.expressive_styled, true)
        val context = icon.context
        val pad = 8.dpToPx(context)
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.resolveThemeColor(R.attr.colorSecondaryContainer))
        }
        icon.setPadding(pad, pad, pad, pad)
        icon.imageTintList = ColorStateList.valueOf(context.resolveThemeColor(R.attr.colorOnSecondaryContainer))
        icon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = 40.dpToPx(context)
            height = width
            marginStart = 16.dpToPx(context)
        }
    }

    /** Section header as a small pill label, like Heimdall's HeaderListItem */
    private fun header(text: TextView) {
        if (text.getTag(R.id.expressive_styled) != null) return
        text.setTag(R.id.expressive_styled, true)
        val context = text.context
        text.background = GradientDrawable().apply {
            cornerRadius = 12.dpToPx(context).toFloat()
            setColor(context.resolveThemeColor(R.attr.colorSurfaceContainerHigh))
        }
        text.setTextColor(context.resolveThemeColor(R.attr.colorOnSurfaceVariant))
        text.setTypeface(text.typeface, Typeface.BOLD)
        text.textSize = 12f
        text.updatePadding(left = 8.dpToPx(context), right = 8.dpToPx(context),
            top = 4.dpToPx(context), bottom = 4.dpToPx(context))
        text.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            marginStart = 20.dpToPx(context)
            topMargin = 20.dpToPx(context)
            bottomMargin = 8.dpToPx(context)
        }
    }

}
