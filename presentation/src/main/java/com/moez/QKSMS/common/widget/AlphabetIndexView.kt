package dev.octoshrimpy.quik.common.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import java.text.Normalizer
import kotlin.math.abs

// Ported from Heimdall's chooloolib ui/contacts/AlphabetIndex.kt and ContactsList.kt.

internal fun scrollTarget(fraction: Float, lastIndex: Int): Int =
    (fraction.coerceIn(0f, 1f) * (lastIndex + 1))
        .toInt().coerceIn(0, lastIndex.coerceAtLeast(0))

internal fun alphabetBend(distanceDp: Float): Float {
    val relative = distanceDp / 144f
    val strength = (1f - relative * relative).coerceAtLeast(0f)
    return strength * strength
}

fun contactSection(name: String?): String {
    val first = name?.trimStart()?.firstOrNull()?.takeIf(Char::isLetter) ?: return "#"
    return Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercase()
}

/**
 * Alphabet rail on the right edge of a RecyclerView: drag to jump between sections, with a tick
 * haptic per section, a highlight pill, a curve toward the finger and a letter preview bubble.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val strip = dp(32f)
    private val highlightWidth = dp(24f)
    private val maxInset = dp(34f)
    private val bubbleSize = dp(44f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(24f)
    }
    private val rect = RectF()

    private var accent = 0
    private var ink = 0
    private var muted = 0

    private var recyclerView: RecyclerView? = null
    private var sections: List<Pair<String, Int>> = emptyList()
    private val actionIds = mutableListOf<Int>()

    private var dragIndex: Int? = null
    private var draggingRail = false
    private var previous = -1
    private var downY = 0f
    private var active = 0

    // Animated values
    private var center = 0f
    private var bend = 0f
    private var bubbleLetter: String? = null
    private var bubbleAlpha = 0f
    private var bubbleScale = 1f
    private var bubbleSlide = 0f
    private var outgoingLetter: String? = null
    private var outgoingAlpha = 0f

    private var centerAnimator: ValueAnimator? = null
    private var bendAnimator: ValueAnimator? = null
    private var enterAnimators: List<ValueAnimator> = emptyList()
    private var exitAnimator: ValueAnimator? = null

    private val hideBubble = Runnable {
        if (!isInteracting()) showLetter(null)
    }

    init {
        contentDescription = context.getString(R.string.contact_alphabet_index)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
    }

    fun setColors(accent: Int, ink: Int, muted: Int) {
        this.accent = accent
        this.ink = ink
        this.muted = muted
        highlightPaint.color = accent
        bubblePaint.color = accent
        bubbleTextPaint.color = ink
        invalidate()
    }

    fun attach(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) = onInteractionChanged()
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateActive()
        })
    }

    fun setSections(sections: List<Pair<String, Int>>) {
        if (sections == this.sections) return
        this.sections = sections
        visibility = if (sections.isEmpty()) GONE else VISIBLE
        recyclerView?.let { list ->
            list.setPaddingRelative(list.paddingStart, list.paddingTop,
                    if (sections.isEmpty()) 0 else strip.toInt(), list.paddingBottom)
        }

        actionIds.forEach { ViewCompat.removeAccessibilityAction(this, it) }
        actionIds.clear()
        sections.forEach { (label, position) ->
            actionIds += ViewCompat.addAccessibilityAction(this,
                    context.getString(R.string.contact_jump_section, label),
                    AccessibilityViewCommand { _, _ ->
                        jumpTo(position)
                        showLetter(label)
                        scheduleHide()
                        true
                    })
        }
        updateActive(animate = false)
    }

    private fun rowHeight() = if (sections.isEmpty()) 0f else height.toFloat() / sections.size

    private fun isInteracting() = dragIndex != null ||
            recyclerView?.scrollState?.let { it != RecyclerView.SCROLL_STATE_IDLE } == true

    private fun onInteractionChanged() {
        removeCallbacks(hideBubble)
        if (isInteracting()) showLetter(sections.getOrNull(active)?.first) else scheduleHide()
    }

    private fun scheduleHide() {
        removeCallbacks(hideBubble)
        postDelayed(hideBubble, 850)
    }

    private fun updateActive(animate: Boolean = true) {
        if (sections.isEmpty()) return
        val list = recyclerView ?: return
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        val atEnd = !list.canScrollVertically(1) && list.canScrollVertically(-1)
        val index = dragIndex?.coerceIn(sections.indices) ?: if (atEnd) {
            sections.lastIndex
        } else {
            sections.indexOfLast { it.second <= first }.coerceAtLeast(0)
        }
        val changed = index != active
        active = index
        ViewCompat.setStateDescription(this, sections[active].first)
        val target = rowHeight() * (active + 0.5f)
        if (!animate || height == 0) {
            centerAnimator?.cancel()
            center = target
        } else if (changed || centerAnimator?.isRunning != true && center != target) {
            centerAnimator?.cancel()
            centerAnimator = animate(center, target, 220) { center = it }
        }
        if (changed && bubbleLetter != null) showLetter(sections[active].first)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        center = rowHeight() * (active + 0.5f)
    }

    /** Mirrors Heimdall's AnimatedContent: each new letter scales/fades/slides in, the old one fades out. */
    private fun showLetter(letter: String?) {
        if (letter == bubbleLetter) return
        if (bubbleLetter != null) {
            outgoingLetter = bubbleLetter
            outgoingAlpha = bubbleAlpha
            exitAnimator?.cancel()
            exitAnimator = animate(outgoingAlpha, 0f, 90) { outgoingAlpha = it }
        }
        enterAnimators.forEach { it.cancel() }
        bubbleLetter = letter
        if (letter == null) {
            bubbleAlpha = 0f
        } else {
            bubbleAlpha = 0f
            bubbleScale = 0.77f
            bubbleSlide = 1f
            enterAnimators = listOf(
                    animate(0f, 1f, 120) { bubbleAlpha = it },
                    animate(0.77f, 1f, 260, OvershootInterpolator(2f)) { bubbleScale = it },
                    animate(1f, 0f, 150) { bubbleSlide = it })
        }
        invalidate()
    }

    private fun animate(from: Float, to: Float, duration: Long,
                        interpolator: android.animation.TimeInterpolator = DecelerateInterpolator(),
                        update: (Float) -> Unit): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { update(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    private fun setDragging(dragging: Boolean) {
        if (draggingRail == dragging) return
        draggingRail = dragging
        bendAnimator?.cancel()
        bendAnimator = animate(bend, if (dragging) 1f else 0f, 250) { bend = it }
    }

    private fun jumpTo(position: Int) {
        (recyclerView?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    private fun move(y: Float) {
        if (height == 0 || sections.isEmpty()) return
        val target = scrollTarget(y / height, sections.lastIndex)
        dragIndex = target
        if (target != previous) {
            previous = target
            performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            })
            jumpTo(sections[target].second)
        }
        updateActive()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sections.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only the 32dp strip on the edge is touchable; the bubble area passes through
                if (event.x < width - strip) return false
                parent.requestDisallowInterceptTouchEvent(true)
                previous = -1
                downY = event.y
                move(event.y)
                onInteractionChanged()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - downY) > touchSlop) setDragging(true)
                move(event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setDragging(false)
                dragIndex = null
                updateActive()
                onInteractionChanged()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideBubble)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (sections.isEmpty()) return
        val rowHeight = rowHeight()
        val highlightHeight = rowHeight.coerceAtMost(dp(24f))
        val inset = maxInset * bend
        val railCenter = width - strip / 2

        // Highlight pill
        val highlightX = railCenter - inset
        rect.set(highlightX - highlightWidth / 2, center - highlightHeight / 2,
                highlightX + highlightWidth / 2, center + highlightHeight / 2)
        canvas.drawRoundRect(rect, highlightWidth / 2, highlightWidth / 2, highlightPaint)

        // Letters, curving toward the finger
        val baseline = (letterPaint.descent() + letterPaint.ascent()) / 2
        sections.forEachIndexed { index, (label, _) ->
            val rowCenter = rowHeight * (index + 0.5f)
            val distance = abs(center - rowCenter)
            letterPaint.color = if (distance < highlightHeight / 2) ink else muted
            val x = railCenter - inset * alphabetBend(distance / density)
            canvas.drawText(label, x, rowCenter - baseline, letterPaint)
        }

        // Preview bubble
        val bubbleRight = width - strip - inset
        val bubbleTop = (center - bubbleSize / 2).coerceIn(0f, (height - bubbleSize).coerceAtLeast(0f))
        drawBubble(canvas, outgoingLetter, outgoingAlpha, 1f, 0f, bubbleRight, bubbleTop)
        drawBubble(canvas, bubbleLetter, bubbleAlpha, bubbleScale, bubbleSlide * bubbleSize / 4, bubbleRight, bubbleTop)
    }

    private fun drawBubble(canvas: Canvas, letter: String?, alpha: Float, scale: Float, slide: Float,
                           right: Float, top: Float) {
        if (letter == null || alpha <= 0f) return
        val cx = right - bubbleSize / 2 + slide
        val cy = top + bubbleSize / 2
        val radius = bubbleSize / 2 * scale
        bubblePaint.alpha = (alpha * 255).toInt()
        bubbleTextPaint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(cx, cy, radius, bubblePaint)
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawText(letter, cx, cy - (bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2, bubbleTextPaint)
        canvas.restore()
    }

}
