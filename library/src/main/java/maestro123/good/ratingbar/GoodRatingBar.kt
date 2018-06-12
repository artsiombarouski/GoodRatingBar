package maestro123.good.ratingbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.support.graphics.drawable.VectorDrawableCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.TintTypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Created by maestro123 on 6/8/18.
 */
open class GoodRatingBar : View {

    var space: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var stepSize = 0.5f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var indicatorsCount: Int = 5
        set(value) {
            if (field != value) {
                field = value
                indicatorStates = Array(value, { IndicatorState() })
                requestLayout()
                invalidate()
            }
        }

    var isIndicator: Boolean = false

    var colorResolver: ColorResolver
        set(value) {
            field = value
            invalidate()
            requestLayout()
        }

    var indicatorDrawable: Drawable? = null
        set(value) {
            field = value
            if (value is VectorDrawableCompat
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && value is VectorDrawable)) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            requestLayout()
            invalidate()
        }

    var rate: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                if (isDragging) {
                    rateStateChangeListeners?.onEach { it.onRateChanged(this, value) }
                } else if (!isTouched) {
                    rateChangeListeners?.onEach { it.onRateChanged(this, value, isDragging) }
                }
                invalidate()
            }
        }

    private var drawableBounds: Rect = Rect()
    private var indicatorStates = Array(indicatorsCount, { IndicatorState() })

    private var touchDownX = 0f
    private var isTouched = false
    private var isDragging = false
    private val scaledTouchSlide: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var rateChangeListeners: MutableList<OnRateChangeListener>? = null
    private var rateStateChangeListeners: MutableList<OnRateStateChangeListener>? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, R.attr.goodRatingBarStyle)

    @SuppressLint("RestrictedApi")
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        val typed = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.GoodRatingBar, defStyleAttr, R.style.DefaultGoodRatingBarStyle)
        space = typed.getDimensionPixelSize(R.styleable.GoodRatingBar_grb_space, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).roundToInt())
        indicatorDrawable = typed.getDrawable(R.styleable.GoodRatingBar_grb_indicator)
        indicatorsCount = typed.getInt(R.styleable.GoodRatingBar_grb_indicatorCount, indicatorsCount)
        stepSize = typed.getFloat(R.styleable.GoodRatingBar_grb_stepSize, stepSize)
        isIndicator = typed.getBoolean(R.styleable.GoodRatingBar_grb_isIndicator, isIndicator)

        colorResolver = if (typed.hasValue(R.styleable.GoodRatingBar_grb_colorResolver)) {
            Class.forName(typed.getString(R.styleable.GoodRatingBar_grb_colorResolver)).newInstance() as ColorResolver
        } else {
            DefaultColorResolver(context, attrs, defStyleAttr, R.style.DefaultGoodRatingBarStyle)
        }

        typed.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isIndicator || !isEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouched = true
                if (isInScrollingContainer()) {
                    touchDownX = event.x
                } else {
                    startDrag(event)
                }
            }

            MotionEvent.ACTION_MOVE -> if (isDragging) {
                trackTouchEvent(event)
            } else {
                val x = event.x
                if (Math.abs(x - touchDownX) > scaledTouchSlide) {
                    startDrag(event)
                }
            }

            MotionEvent.ACTION_UP -> {
                isTouched = false
                if (isDragging) {
                    trackTouchEvent(event)
                    onStopTrackingTouch()
                    isPressed = false
                } else {
                    onStartTrackingTouch()
                    trackTouchEvent(event)
                    onStopTrackingTouch()
                }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                isTouched = false
                if (isDragging) {
                    onStopTrackingTouch()
                    isPressed = false
                }
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (indicatorStates.isEmpty()) return

        canvas.drawIndicators(colorResolver.getColor(this, 0f, drawableState))

        val saveCount = canvas.save()

        val fullRate: Int = minOf(indicatorsCount, ceil(rate).toInt()) - 1
        val offset = if (fullRate > -1) indicatorStates[fullRate].bounds.left + indicatorStates[fullRate].bounds.width() * (rate - fullRate)
        else 0f
        canvas.clipRect(0f, 0f, offset, height.toFloat())

        canvas.drawIndicators(colorResolver.getColor(this, rate, drawableState))

        canvas.restoreToCount(saveCount)
    }

    fun addOnRateChangeListener(listener: OnRateChangeListener) {
        if (rateChangeListeners == null) {
            rateChangeListeners = mutableListOf()
        }
        rateChangeListeners!!.add(listener)
    }

    fun removeOnRateChangeListener(listener: OnRateChangeListener) {
        rateChangeListeners?.remove(listener)
    }

    fun addOnRateStateChangeListener(listener: OnRateStateChangeListener) {
        if (rateStateChangeListeners == null) {
            rateStateChangeListeners = mutableListOf()
        }
        rateStateChangeListeners!!.add(listener)
    }

    fun removeOnRateStateChangeListener(listener: OnRateStateChangeListener) {
        rateStateChangeListeners?.remove(listener)
    }

    fun setIndicatorDrawable(resource: Int) {
        indicatorDrawable = ContextCompat.getDrawable(context, resource)
    }

    //TODO: replace drawableBound with simple width and height params
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (indicatorDrawable != null) {
            indicatorDrawable?.let { drawable ->
                val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val availableWidthWithoutSpace = availableWidth - space * (indicatorsCount - 1)
                val availableHeight = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
                val widthMode = MeasureSpec.getMode(widthMeasureSpec)
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)

                val drawableWidth = drawable.intrinsicWidth
                val drawableHeight = drawable.intrinsicHeight

                var width = 0
                var height = 0
                var space = space

                drawableBounds.top = 0
                drawableBounds.left = 0

                if (drawableWidth * indicatorsCount > availableWidthWithoutSpace) {
                    drawableBounds.right = availableWidthWithoutSpace / indicatorsCount
                } else {
                    drawableBounds.right = drawableWidth
                }
                if (widthMode == MeasureSpec.EXACTLY) {
                    space = (availableWidth - drawableBounds.width() * indicatorsCount) / (indicatorsCount - 1)
                    width = availableWidth
                } else {
                    width = drawableBounds.width() * indicatorsCount + space * (indicatorsCount - 1)
                }

                if (drawableHeight > availableHeight
                        && (availableHeight > 0 && heightMode == MeasureSpec.EXACTLY)) {
                    drawableBounds.bottom = availableHeight
                } else {
                    drawableBounds.bottom = drawableHeight
                }
                if (heightMode == MeasureSpec.EXACTLY) {
                    height = availableHeight
                } else {
                    height = drawableBounds.height()
                }

                val drawableBoundsWidth = drawableBounds.width()
                val drawableBoundHeight = drawableBounds.height()

                if (drawableBoundsWidth != drawableWidth || drawableBoundHeight != drawableHeight) {
                    if (drawableBoundsWidth < drawableBoundHeight) {
                        val scale = drawableBoundsWidth / drawableWidth.toFloat()
                        drawableBounds.bottom = (drawableHeight * scale).toInt()
                        if (heightMode != MeasureSpec.EXACTLY) {
                            height = drawableBounds.height()
                        }
                    } else {
                        val scale = drawableBoundHeight / drawableHeight.toFloat()
                        drawableBounds.right = (drawableWidth * scale).toInt()
                        if (widthMode != MeasureSpec.EXACTLY) {
                            width = drawableBounds.width() * indicatorsCount + space * (indicatorsCount - 1)
                        } else {
                            space = (availableWidth - drawableBounds.width() * indicatorsCount) / (indicatorsCount - 1)
                        }
                    }
                }

                var startOffset = paddingLeft
                val topOffset = paddingTop + height / 2 - drawableBounds.height() / 2

                indicatorStates.forEachIndexed { index, state ->
                    state.bounds.apply {
                        set(drawableBounds)
                        offset(startOffset, topOffset)
                        startOffset += drawableBounds.width() + space
                    }
                }

                setMeasuredDimension(
                        width + paddingLeft + paddingRight,
                        height + paddingTop + paddingBottom)
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun Canvas.drawIndicators(color: Int) {
        indicatorDrawable?.let { drawable ->
            indicatorStates.forEach {
                drawable.bounds = it.bounds
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                drawable.draw(this)
            }
        }
    }

    private fun startDrag(event: MotionEvent) {
        isPressed = true

        onStartTrackingTouch()
        trackTouchEvent(event)
        attemptClaimDrag()

        rateStateChangeListeners?.onEach { it.onRateChangeStart(this, rate) }
    }

    //TODO: implement rtl
    private fun trackTouchEvent(event: MotionEvent) {
        val x = Math.round(event.x)
        val width = width
        val availableWidth = width - paddingLeft - paddingRight
        val xClamped = x - paddingLeft

        val xCompute = if (x < paddingLeft) 0 else if (x > width - paddingRight) availableWidth else xClamped
        val initialRate = ceil((xCompute) / (availableWidth.toFloat()) * indicatorsCount) - 1

        val state = indicatorStates.find { x >= it.bounds.left && x <= it.bounds.right }

        if (state != null) {
            val diff = x - state.bounds.left
            val percent = if (stepSize == 1f) stepSize else stepSize * round(diff.toFloat() / state.bounds.width() / stepSize)
            rate = initialRate + percent
        } else {
            invalidate()
        }
    }

    private fun attemptClaimDrag() {
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun onStartTrackingTouch() {
        isDragging = true
    }

    private fun onStopTrackingTouch() {
        if (isDragging) {
            isDragging = false
            rateChangeListeners?.onEach { it.onRateChanged(this, rate, true) }
            rateStateChangeListeners?.onEach { it.onRateChangeFinish(this, rate) }
        }
    }

    private fun isInScrollingContainer(): Boolean {
        var p: ViewParent? = parent
        while (p != null && p is ViewGroup) {
            if (p.shouldDelayChildPressedState()) {
                return true
            }
            p = p.parent
        }
        return false
    }

    interface ColorResolver {
        fun getColor(ratingBar: GoodRatingBar, rate: Float, state: IntArray): Int
    }

    interface OnRateChangeListener {
        fun onRateChanged(ratingBar: GoodRatingBar, rate: Float, fromUser: Boolean)
    }

    interface OnRateStateChangeListener {
        fun onRateChangeStart(ratingBar: GoodRatingBar, rate: Float)
        fun onRateChanged(ratingBar: GoodRatingBar, rate: Float)
        fun onRateChangeFinish(ratingBar: GoodRatingBar, rate: Float)
    }

    inner class IndicatorState {
        val bounds = Rect()
    }

    @SuppressLint("RestrictedApi")
    open class DefaultColorResolver : ColorResolver {

        private val emptyColor: ColorStateList
        private val fillColor: ColorStateList

        constructor() {
            emptyColor = ColorStateList.valueOf(Color.BLACK)
            fillColor = ColorStateList.valueOf(Color.WHITE)
        }

        constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
            val typed = TintTypedArray.obtainStyledAttributes(context, attrs, THEME_ATTRS, defStyleAttr, defStyleRes)

            emptyColor = if (typed.hasValue(2)) typed.getColorStateList(2)
            else typed.getColorStateList(0)

            fillColor = if (typed.hasValue(3)) typed.getColorStateList(3)
            else typed.getColorStateList(1)

            typed.recycle()
        }

        override fun getColor(ratingBar: GoodRatingBar, rate: Float, state: IntArray): Int = when {
            rate <= 0 -> emptyColor.getColorForState(state, 0)
            else -> fillColor.getColorForState(state, 0)
        }

        companion object {
            private val THEME_ATTRS = intArrayOf(
                    R.attr.colorControlNormal, R.attr.colorControlActivated,
                    R.attr.grb_empty_color, R.attr.grb_fill_color)
        }

    }

    open class SimpleColorResolver : ColorResolver {

        private val emptyColor: ColorStateList
        private val fillColor: ColorStateList

        constructor(emptyColor: Int, fillColor: Int)
                : this(ColorStateList.valueOf(emptyColor), ColorStateList.valueOf(fillColor))

        constructor(emptyColor: ColorStateList, fillColor: ColorStateList) {
            this.emptyColor = emptyColor
            this.fillColor = fillColor
        }


        override fun getColor(ratingBar: GoodRatingBar, rate: Float, state: IntArray): Int = when {
            rate <= 0 -> emptyColor.getColorForState(state, 0)
            else -> fillColor.getColorForState(state, 0)
        }

    }

}