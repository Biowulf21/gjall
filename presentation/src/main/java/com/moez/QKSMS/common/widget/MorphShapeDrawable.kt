package dev.octoshrimpy.quik.common.widget

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor

/**
 * Expressive icon-button background: a 9-lobe "cookie" that morphs to a circle while the view
 * is pressed (150ms, FastOutSlowIn). Release reverses from the current value, so it's interruptible.
 * Duration follows the system animator scale, so "Remove animations" disables it.
 */
class MorphShapeDrawable(color: Int, private val maxSize: Int = Int.MAX_VALUE) : Drawable() {

    /** Square the cookie is drawn in: centred, at most [maxSize], so wide views don't stretch it */
    private val square = android.graphics.Rect()

    private val path = Path()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private var tint: ColorStateList? = null
    private var pressed = false

    private var progress = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    private val animator = ValueAnimator().apply {
        duration = 150
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { progress = it.animatedValue as Float }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(buildPath(), paint)
    }

    private fun buildPath(): Path {
        path.rewind()
        MORPH.toPath(progress, path)
        val side = minOf(bounds.width(), bounds.height(), maxSize)
        square.set(bounds.centerX() - side / 2, bounds.centerY() - side / 2,
            bounds.centerX() - side / 2 + side, bounds.centerY() - side / 2 + side)
        matrix.setScale(side.toFloat(), side.toFloat())
        matrix.postTranslate(square.left.toFloat(), square.top.toFloat())
        path.transform(matrix)
        return path
    }

    override fun getOutline(outline: Outline) {
        // Round shadow, not the cookie path: a path-outline shadow of the 9-lobe cookie hung the
        // RenderThread at 100% on Android 17 (OnePlus 15), so the app never drew a frame
        buildPath()
        outline.setOval(square)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        tint?.let { paint.color = it.getColorForState(state, it.defaultColor) }
        val nowPressed = android.R.attr.state_pressed in state
        if (nowPressed != pressed) {
            pressed = nowPressed
            animator.cancel()
            animator.setFloatValues(progress, if (pressed) 1f else 0f)
            animator.start()
        }
        return true
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        tint?.let { paint.color = it.getColorForState(state, it.defaultColor) }
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val MORPH = Morph(
            // Same cookie as Heimdall's MorphShape.kt: 9 lobes, inner radius 0.8, rounding 0.5
            RoundedPolygon.star(9, innerRadius = 0.8f, rounding = CornerRounding(0.5f)).normalized(),
            RoundedPolygon.circle(9).normalized())

        /** Gives an icon button the tonal cookie background used by the Expressive style */
        fun apply(view: View) {
            view.background = MorphShapeDrawable(view.context.resolveThemeColor(R.attr.colorPrimaryContainer))
            (view as? ImageView)?.imageTintList =
                ColorStateList.valueOf(view.context.resolveThemeColor(R.attr.colorOnPrimaryContainer))
        }
    }

}
