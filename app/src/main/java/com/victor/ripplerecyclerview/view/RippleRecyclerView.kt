package com.victor.ripplerecyclerview.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.victor.ripplerecyclerview.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class RippleRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {

    companion object {
        private const val MAX_SCROLL_STEP = 50f
        /**
         * 手势滑动距离缩放因子
         */
        private const val SCROLL_FACTOR = 0.75f
        private const val MIN_TOUCH_SLOP = 5
    }

    private val tipViewRecMaxWidth = dip2px(40)
    /**
     * 手势滑动距离缩放因子
     */
    private val scrollFactor = 1.0f
    /**
     * 触发滑动跳转距离
     */
    private val maxScrollWidth = dip2px(90) * scrollFactor
    private var hasMoreData = false
    private var mLastMotionPos = 0f
    private var mOffset = 0
    private var viewPaddingBottom = 0f
    private var viewPaddingTop = 0f
    /**
     * 背景圆角半径
     */
    private var bgCornerSize = 0f
    private var scrollLayoutManager: ScrollerLinearLayoutManager? = null
    private var textLineSpace = 0f

    private var springAnimation: Animation? = null
    private val mReleaseBackAnimDuration = 1500L
    var contentTip = context.getString(R.string.move_left_more)
    var bgColor = 0
    var textColor = 0
    private var textStartXOffset = 0
    // 缓存
    private var wordRect = Rect()


    /**
     * 视图可用区域高度
     */
    private var mVisibleHeight = 0f
    private var mPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
    }

    var realTimeCheckOverScroll = false
    var onCrossOverListener: ((Boolean) -> Unit)? = null

    init {
        overScrollMode = View.OVER_SCROLL_NEVER
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.RippleRecyclerView, 0, 0)
        mPaint.textSize = typedArray.getDimension(R.styleable.RippleRecyclerView_ripple_textSize, 12f)
        viewPaddingTop = typedArray.getDimension(R.styleable.RippleRecyclerView_ripple_marginTop, 0f)
        viewPaddingBottom = typedArray.getDimension(R.styleable.RippleRecyclerView_ripple_marginBottom, 0f)
        textLineSpace = typedArray.getDimension(R.styleable.RippleRecyclerView_ripple_line_space, 0f)
        val tipText = typedArray.getString(R.styleable.RippleRecyclerView_ripple_text)
        if (tipText?.isNotBlank() == true) {
            contentTip = tipText
        }
        bgColor = typedArray.getColor(R.styleable.RippleRecyclerView_ripple_bgColor, ContextCompat.getColor(context, R.color.color_f6f6f6))
        textColor = typedArray.getColor(R.styleable.RippleRecyclerView_ripple_textColor, ContextCompat.getColor(context, R.color.color_333))

        typedArray.recycle()
        initAnimation()
    }

    fun dip2px(dpValue: Float): Float {
        val appScale = resources.displayMetrics.density
        return dpValue * appScale + 0.5f
    }

    fun dip2px(dpValue: Int): Int {
        return dip2px(dpValue.toFloat()).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mVisibleHeight = h - viewPaddingTop - viewPaddingBottom
        checkTextSize()
        checkTextLineSpace()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                clearAnimation()
                scrollLayoutManager?.enableHorizontalScroll(true)
                mLastMotionPos = e.rawX
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.rawX - mLastMotionPos) > MIN_TOUCH_SLOP) {
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (hasMoreData && onTouchEventInternal(ev)) {
            true
        } else super.onTouchEvent(ev)
    }

    private fun onTouchEventInternal(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                val posDiff = (ev.rawX - mLastMotionPos).toInt()
                mLastMotionPos = ev.rawX
                if (!canScrollHorizontally(1) && (mOffset < 0 || posDiff < 0)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollLayoutManager?.enableHorizontalScroll(false)
                    mOffset = if (posDiff > 0) {
                        mOffset + min(posDiff * SCROLL_FACTOR, MAX_SCROLL_STEP).toInt()
                    } else {
                        mOffset + max(posDiff * SCROLL_FACTOR, -MAX_SCROLL_STEP).toInt()
                    }
                    if (mOffset > 0) {
                        mOffset = 0
                    }
                    invalidate()

                    if (realTimeCheckOverScroll) {
                        onCrossOverListener?.invoke(abs(mOffset) > maxScrollWidth)
                    }

                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                eventEndProcess()
                mLastMotionPos = ev.rawX
            }
        }
        return false
    }

    private fun eventEndProcess() {
        scrollLayoutManager?.enableHorizontalScroll(true)
        if (mOffset < 0) {
            startReleaseAnimation()
            if (!realTimeCheckOverScroll) {
                onCrossOverListener?.invoke(abs(mOffset) > maxScrollWidth)
            }
        }
    }

    private fun initAnimation() {
        springAnimation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                mOffset = ((1 - interpolatedTime) * mOffset).toInt()
                if (hasEnded()) {
                    mOffset = 0
                }
                invalidate()
            }
        }
    }

    fun hasMoreData(hasMoreData: Boolean?) {
        this.hasMoreData = hasMoreData ?: false
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)
        this.scrollLayoutManager = layout as? ScrollerLinearLayoutManager
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        startReleaseAnimation()
    }

    private fun startReleaseAnimation() {
        if (mOffset != 0) {
            springAnimation?.duration = mReleaseBackAnimDuration
            springAnimation?.interpolator = AccelerateDecelerateInterpolator()
            startAnimation(springAnimation)
        }
    }

    var flingListener: ((velocityX: Int, velocityY: Int) -> Unit?)? = null

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        val result = super.fling(velocityX, velocityY)
        if (result && mOffset == 0) {
            flingListener?.invoke(velocityX, velocityY)
        }
        return result
    }

    override fun draw(canvas: Canvas) {
        if (mOffset == 0 || !hasMoreData || contentTip.isBlank()) {
            super.draw(canvas)
        } else {
            mPaint.color = bgColor
            canvas.save()
            canvas.translate(mOffset.toFloat(), 0f)
            super.draw(canvas)
            canvas.restore()
            val path = Path()
            path.addRoundRect(RectF((width - min(tipViewRecMaxWidth, abs(mOffset))).toFloat(),
                viewPaddingTop, width.toFloat(), mVisibleHeight),
                floatArrayOf(bgCornerSize, bgCornerSize, 0f, 0f, 0f, 0f, bgCornerSize, bgCornerSize),
                Path.Direction.CCW)
            canvas.drawPath(path, mPaint)
            mPaint.color = textColor
            val rect = Rect()
            mPaint.getTextBounds("滑", 0, 1, rect)
            val textHeight = rect.height() + textLineSpace
            var topY = (mVisibleHeight - textHeight * contentTip.length) / 2 + rect.height()
            for (element in contentTip) {
                canvas.save()
                canvas.drawText(element.toString(), (width + textStartXOffset -
                        min((tipViewRecMaxWidth + rect.width()) / 2 + textStartXOffset,
                            abs(mOffset))).toFloat(), topY, mPaint)
                canvas.rotate(90f)
                canvas.restore()
                topY += textHeight
            }
            drawWave(canvas)
        }
    }

    private fun drawWave(canvas: Canvas?) {
        if (tipViewRecMaxWidth >= abs(mOffset)) {
            return
        }
        mPaint.color = bgColor
        // 画二阶贝塞尔曲线
        val path = Path()
        val startX = (width - tipViewRecMaxWidth).toFloat()
        path.moveTo(startX, viewPaddingTop)
        path.quadTo(
            startX - (abs(mOffset) - tipViewRecMaxWidth) * 1.5f,
            viewPaddingTop + mVisibleHeight / 2,
            (width - tipViewRecMaxWidth).toFloat(),
            mVisibleHeight + viewPaddingTop)
        canvas?.drawPath(path, mPaint)
    }

    fun textSize(textSize: Float) {
        mPaint.textSize = textSize
        checkTextSize()
        checkTextLineSpace()
        invalidate()
    }

    private fun checkTextSize() {
        val rect = Rect()
        mPaint.getTextBounds("滑", 0, 1, rect)
        if (mVisibleHeight -  rect.height() * contentTip.length < 0) {
            mPaint.textSize = mPaint.textSize * 0.9f
            checkTextSize()
        }
        textStartXOffset = (tipViewRecMaxWidth - rect.width()) / 2
    }

    private fun checkTextLineSpace() {
        if (contentTip.isBlank()) {
            return
        }
        val rect = Rect()
        mPaint.getTextBounds("滑", 0, 1, rect)
        if (mVisibleHeight -  (textLineSpace + rect.height()) * contentTip.length < 0) {
            textLineSpace = mVisibleHeight / contentTip.length - rect.height()
        }
    }

    class ScrollerLinearLayoutManager(context: Context?, orientation: Int, reverseLayout: Boolean) : LinearLayoutManager(context, orientation, reverseLayout) {
        private var enable = true
        fun enableHorizontalScroll(enable: Boolean) {
            this.enable = enable
        }

        override fun canScrollHorizontally(): Boolean {
            return if (!enable) {
                false
            } else {
                return super.canScrollHorizontally()
            }
        }
    }

}