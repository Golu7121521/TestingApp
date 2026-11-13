package com.gitactionbuild

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.abs

// Counts raw bytes read from the network so we can show clone progress.
class CountIn(inp: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(inp) {
    private var n = 0L
    override fun read(): Int {
        val r = super.read()
        if (r >= 0) { n++; onRead(n) }
        return r
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) { n += r; onRead(n) }
        return r
    }
}

// Faint falling green characters shown behind the terminal while a build runs.
class MatrixView(c: Context) : View(c) {
    private val dd = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 13 * dd
    }
    private val glyphs = "01<>{}[]/=+*#:;ABCDEFXZ$%&".toCharArray()
    private val rnd = java.util.Random()
    private val stepX = 14 * dd
    private val gap = 14 * dd
    private val trail = 14
    private var cols = 0
    private var heads = FloatArray(0)
    private var speeds = FloatArray(0)
    private val loop = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 45L)
        }
    }

    init { alpha = 0.32f }

    fun stop() { removeCallbacks(loop) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(loop)
        post(loop)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cols = (w / stepX).toInt() + 1
        heads = FloatArray(cols) { -rnd.nextFloat() * h }
        speeds = FloatArray(cols) { (1.2f + rnd.nextFloat() * 3f) * dd }
    }

    override fun onDraw(cv: Canvas) {
        val h = height.toFloat()
        for (i in 0 until cols) {
            heads[i] += speeds[i]
            if (heads[i] - trail * gap > h) heads[i] = -rnd.nextFloat() * h * 0.6f
            val x = i * stepX
            for (k in 0 until trail) {
                val y = heads[i] - k * gap
                if (y < 0f || y > h) continue
                p.color = if (k == 0) 0xFFCCFFDD.toInt() else 0xFF33FF66.toInt()
                p.alpha = ((1f - k.toFloat() / trail) * 255).toInt()
                cv.drawText(glyphs[rnd.nextInt(glyphs.size)].toString(), x, y, p)
            }
        }
    }
}

// A row that slides left to reveal action buttons behind it.
// fg = the card, bgv = the actions layer, reveal = how far the card can move.
class SwipeRow(c: Context, private val fg: View, bgv: View, private val reveal: Float) : FrameLayout(c) {
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var sx = 0f
    private var sy = 0f
    private var base = 0f
    private var dragging = false
    var open = false
        private set

    init {
        addView(bgv, FrameLayout.LayoutParams(-1, -1))
        addView(fg, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x; sy = e.y; base = fg.translationX; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - sx
                val dy = e.y - sy
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                    // opening needs a leftward drag; closing needs a rightward drag on an open row
                    if (dx < 0f || open) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                fg.translationX = (base + e.x - sx).coerceIn(-reveal, 0f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                settle(fg.translationX < -reveal / 2f)
            }
        }
        return true
    }

    fun settle(o: Boolean) {
        open = o
        fg.animate().translationX(if (o) -reveal else 0f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    fun close() = settle(false)
}

// Small arc spinner used by the pull-to-refresh header.
class SpinnerView(c: Context) : View(c) {
    private val dd = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * dd
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3B82F6")
    }
    private var angle = 0f
    private var spinner: ValueAnimator? = null

    // used while the user pulls (ignored while the spinner is running)
    fun setAngle(a: Float) {
        if (spinner == null) { angle = a; invalidate() }
    }

    fun spin(on: Boolean) {
        if (on && spinner == null) {
            spinner = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 700
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { angle = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!on) {
            spinner?.cancel()
            spinner = null
        }
    }

    override fun onDraw(cv: Canvas) {
        val pad = p.strokeWidth
        cv.drawArc(RectF(pad, pad, width - pad, height - pad), angle, 270f, false, p)
    }

    override fun onDetachedFromWindow() {
        spin(false)
        super.onDetachedFromWindow()
    }
}

// ScrollView with a pull-down gesture at the top. Shows a spinner while refreshing.
class PullLayout(c: Context) : FrameLayout(c) {
    val sv = ScrollView(c)
    private val spin = SpinnerView(c)
    private val dd = resources.displayMetrics.density
    private val limit = 64f * dd
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var sx = 0f
    private var sy = 0f
    private var pulling = false
    private var curPull = 0f
    var refreshing = false
        private set
    var onRefresh: (() -> Unit)? = null

    init {
        addView(spin, FrameLayout.LayoutParams(
            (28 * dd).toInt(), (28 * dd).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = (10 * dd).toInt() })
        addView(sv, FrameLayout.LayoutParams(-1, -1))
        spin.alpha = 0f
    }

    fun setContent(v: View) { sv.addView(v) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x
                sy = e.y
                pulling = false
            }
            MotionEvent.ACTION_MOVE -> {
                // only a mostly-vertical drag down counts as a pull (horizontal bar scrolling does not)
                if (!refreshing && sv.scrollY == 0 && e.y - sy > slop && abs(e.y - sy) > abs(e.x - sx)) {
                    pulling = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!pulling) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> move(((e.y - sy) * 0.5f).coerceAtLeast(0f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pulling = false
                if (curPull >= limit) startRefresh() else settle(0f)
            }
        }
        return true
    }

    private fun move(p: Float) {
        curPull = p
        sv.translationY = p
        spin.alpha = (p / limit).coerceIn(0f, 1f)
        spin.setAngle(p * 4f)
    }

    private fun settle(to: Float) {
        curPull = to
        sv.animate().translationY(to).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        spin.animate().alpha(if (to > 0f) 1f else 0f).setDuration(200).start()
    }

    private fun startRefresh() {
        refreshing = true
        settle(limit)
        spin.spin(true)
        onRefresh?.invoke()
    }

    fun finishRefresh() {
        refreshing = false
        spin.spin(false)
        settle(0f)
    }
}
