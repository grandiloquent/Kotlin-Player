package psycho.euphoria.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.min

class TimeBar : View {
    private fun getPositionIncrement() = if (keyTimeIncrement == TIME_UNSET) if (duration == TIME_UNSET) 0 else duration / keyCountIncrement else keyTimeIncrement
    private fun getProgressText() = position.getStringForTime(mFormatterStringBuilder, mFormatter)
    private fun isInSeekBar(x: Float, y: Float) = mSeekBounds.contains(x.toInt(), y.toInt())
    private val mBufferedBar = Rect()
    private val mBufferedPaint = Paint()
    private val mFineScrubYThreshold: Float
    private val mFormatter: Formatter
    private val mFormatterStringBuilder = StringBuilder()
    private val mListeners = CopyOnWriteArraySet<OnScrubListener>()
    private val mPlayedAdMarkerPaint = Paint()
    private val mPlayedPaint = Paint()
    private val mProgressBar = Rect()
    private val mScrubberBar = Rect()
    private val mScrubberPaint = Paint()
    private val mSeekBounds = Rect()
    private val mStopScrubbingRunable = Runnable { stopScrubbing(false) }
    private val mUnplayedPaint = Paint()
    private var mBarHeight = 0
    private var mLastCoarseScrubXPosition = 0f
    private var mLocationOnScreen: IntArray? = null
    private var mScrubberDisabledSize = 0
    private var mScrubberDraggedSize = 0
    private var mScrubberEnabledSize = 0
    private var mScrubberPadding = 0
    private var mScrubbing = false
    private var mScrubPosition = 0L
    private var mTouchPosition: Point? = null
    private var mTouchTargetHeight = 0
    var keyCountIncrement = DEFAULT_INCREMENT_COUNT
        set(value) {
            keyTimeIncrement = TIME_UNSET
            field = value
        }
    var keyTimeIncrement = TIME_UNSET
        set(value) {
            field = value
            keyCountIncrement = INDEX_UNSET
        }
    var duration = TIME_UNSET
        set(value) {
            field = value
            if (mScrubbing && value == TIME_UNSET) {
                stopScrubbing(true)
            }
            update()
        }
    var position = 0L
        set(value) {
            field = value
            contentDescription = getProgressText()
            update()
        }
    var bufferedPosition = 0L
        set(value) {
            field = value
            update()
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        mFormatter = Formatter(mFormatterStringBuilder)
        val metrics = resources.displayMetrics
        mFineScrubYThreshold = FINE_SCRUB_Y_THRESHOLD_DP.dpToPx(metrics).toFloat()
        mBarHeight = DEFAULT_BAR_HEIGHT_DP.dpToPx(metrics)
        mTouchTargetHeight = DEFAULT_TOUCH_TARGET_HEIGHT_DP.dpToPx(metrics)
        mScrubberEnabledSize = DEFAULT_SCRUBBER_ENABLED_SIZE_DP.dpToPx(metrics)
        mScrubberDisabledSize = DEFAULT_SCRUBBER_DISABLED_SIZE_DP.dpToPx(metrics)
        mScrubberDraggedSize = DEFAULT_SCRUBBER_DRAGGED_SIZE_DP.dpToPx(metrics)
        val defaultColor = DEFAULT_PLAYED_COLOR.toInt()
        mPlayedPaint.color = defaultColor
        mScrubberPaint.color = getDefaultScrubberColor(defaultColor)
        mBufferedPaint.color = getDefaultBufferedColor(defaultColor)
        mUnplayedPaint.color = getDefaultUnplayedColor(defaultColor)
        mPlayedAdMarkerPaint.color = getDefaultPlayedAdMarkerColor(DEFAULT_AD_MARKER_COLOR.toInt())
        mScrubberPadding = (max(mScrubberDisabledSize, max(mScrubberEnabledSize, mScrubberDraggedSize)) + 1) / 2
        isFocusable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    fun addListener(listener: OnScrubListener) {
        //Log.e(TAG, "addListener")
        mListeners.add(listener)
    }


    private fun drawPlayhead(canvas: Canvas) {
        //Log.e(TAG, "drawPlayhead")
        if (duration <= 0) return
        val px = mScrubberBar.right.contrain(mScrubberBar.left, mProgressBar.right)
        val py = mScrubberBar.centerY()
        val scrubberSize = if (mScrubbing || isFocused) mScrubberDraggedSize else if (isEnabled) mScrubberEnabledSize else mScrubberDisabledSize
        canvas.drawCircle(px.toFloat(), py.toFloat(), scrubberSize / 2f, mScrubberPaint)
    }

    private fun drawTimeBar(canvas: Canvas) {
        //Log.e(TAG, "drawTimeBar")
        val progressBarHeight = mProgressBar.height()
        val barTop = (mProgressBar.centerY() - progressBarHeight / 2).toFloat()
        val barBottom = (barTop + progressBarHeight)
        if (duration <= 0) {
            canvas.drawRect(mProgressBar.left.toFloat(), barTop, mProgressBar.right.toFloat(), barBottom, mUnplayedPaint)
            return
        }
        val br = mBufferedBar.right
        val pl = max(max(mProgressBar.left, br), mScrubberBar.right)
        if (pl < mProgressBar.right) {
            canvas.drawRect(pl.toFloat(), barTop, mProgressBar.right.toFloat(), barBottom, mUnplayedPaint)
        }
        val bl = max(mBufferedBar.left, mScrubberBar.right)
        if (br > bl) {
            canvas.drawRect(bl.toFloat(), barTop, br.toFloat(), barBottom, mBufferedPaint)
        }
        if (mScrubberBar.width() > 0) {
            canvas.drawRect(mScrubberBar.left.toFloat(), barTop, mScrubberBar.right.toFloat(), barBottom, mPlayedPaint)
        }
    }

    private fun getScrubberPosition(): Long {
        //Log.e(TAG, "getScrubberPosition")
        if (mProgressBar.width() <= 0 || duration == TIME_UNSET) return 0L
        return (mScrubberBar.width() * duration) / mProgressBar.width()
    }


    override fun onDraw(canvas: Canvas) {
        //Log.e(TAG, "onDraw")
        canvas.let {
            it.save()
            drawTimeBar(it)
            drawPlayhead(it)
            it.restore()
        }
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        //Log.e(TAG, "onInitializeAccessibilityEvent")
        super.onInitializeAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
            event.text.add(getProgressText())
        event.className = TimeBar::class.java.name
    }

    @Suppress("DEPRECATION")
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        //Log.e(TAG, "onInitializeAccessibilityNodeInfo")
        super.onInitializeAccessibilityNodeInfo(info)
        info.apply {
            className = TimeBar::class.java.canonicalName
            contentDescription = getProgressText()
        }
        if (duration <= 0) return
        if (Build.VERSION.SDK_INT >= 21) {
            info.apply {
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            }
        } else if (Build.VERSION.SDK_INT >= 16) {
            info.apply {
                addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        //Log.e(TAG, "onKeyDown")
        if (isEnabled) {
            var pi = getPositionIncrement()
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    pi -= pi
                    if (scrubIncermentally(pi)) {
                        removeCallbacks(mStopScrubbingRunable)
                        postDelayed(mStopScrubbingRunable, STOP_SCRUBBING_TIMEOUT_MS)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (scrubIncermentally(pi)) {
                        removeCallbacks(mStopScrubbingRunable)
                        postDelayed(mStopScrubbingRunable, STOP_SCRUBBING_TIMEOUT_MS)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (mScrubbing) {
                        removeCallbacks(mStopScrubbingRunable)
                        mStopScrubbingRunable.run()
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        //Log.e(TAG, "onLayout")
        val w = right - left
        val h = bottom - top
        val barY = (h - mTouchTargetHeight) / 2
        val seekLeft = paddingLeft
        val seekRight = w - paddingRight
        val progressY = barY + (mTouchTargetHeight - mBarHeight) / 2
        mSeekBounds.set(seekLeft, barY, seekRight, barY + mTouchTargetHeight)
        mProgressBar.set(mSeekBounds.left + mScrubberPadding, progressY, mSeekBounds.right - mScrubberPadding, progressY + mBarHeight)
        //Log.e("onLayout", "w => ${w} \nh => ${h} \nbarY => ${barY} \nseekLeft => ${seekLeft} \nseekRight => ${seekRight} \nprogressY => ${progressY} \nchanged => ${changed} \nleft => ${left} \ntop => ${top} \nright => ${right} \nbottom => ${bottom} \n")

        update()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val hm = MeasureSpec.getMode(heightMeasureSpec)
        val hs = MeasureSpec.getSize(heightMeasureSpec)
        val h = if (hm == MeasureSpec.UNSPECIFIED) mTouchTargetHeight else if (hm == MeasureSpec.EXACTLY) hs else min(mTouchTargetHeight, hs)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
        //Log.e("onMeasure","hm => ${hm} \nhs => ${hs} \nh => ${h} \nwidthMeasureSpec => ${widthMeasureSpec} \nheightMeasureSpec => ${heightMeasureSpec} \n")
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        //Log.e(TAG, "onTouchEvent")
        if (!isEnabled || duration <= 0) return false
        val touchPosition = resolveRelativeTouchPosition(event)
        val x = touchPosition?.x?.toFloat() ?: 0f
        val y = touchPosition?.y?.toFloat() ?: 0f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInSeekBar(x, y)) {
                    positionScrubber(x)
                    startScrubbing()
                    mScrubPosition = getScrubberPosition()
                    update()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mScrubbing) {
                    if (y < mFineScrubYThreshold) {
                        val relativeX = x - mLastCoarseScrubXPosition
                        positionScrubber(mLastCoarseScrubXPosition + relativeX / FINE_SCRUB_RATIO)
                    } else {
                        mLastCoarseScrubXPosition = x
                        positionScrubber(x)
                    }
                }
                mScrubPosition = getScrubberPosition()
                mListeners.forEach { it.onScrubMove(this, mScrubPosition) }
                update()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (mScrubbing) {
                    stopScrubbing(event.action == MotionEvent.ACTION_CANCEL)
                    return true
                }
            }
        }
        return false
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        //Log.e(TAG, "performAccessibilityAction")
        if (super.performAccessibilityAction(action, arguments)) {
            return true
        }
        if (duration <= 0) return false
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (scrubIncermentally(-getPositionIncrement())) {
                stopScrubbing(false)
            }
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            if (scrubIncermentally(getPositionIncrement())) {
                stopScrubbing(false)
            }
        } else {
            return false
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        return true
    }

    private fun positionScrubber(x: Float) {
        //Log.e(TAG, "positionScrubber")
        mScrubberBar.right = x.toInt().contrain(mProgressBar.left, mProgressBar.right)
    }

    fun removeListener(listener: OnScrubListener) {
        //Log.e(TAG, "removeListener")
        mListeners.remove(listener)
    }

    private fun resolveRelativeTouchPosition(event: MotionEvent): Point? {
        //Log.e(TAG, "resolveRelativeTouchPosition")
        if (mLocationOnScreen == null) {
            mLocationOnScreen = IntArray(2)
            mTouchPosition = Point()
        }
        getLocationOnScreen(mLocationOnScreen)
        mLocationOnScreen?.let {
            mTouchPosition?.set(event.rawX.toInt() - it[0],
                    event.rawY.toInt() - it[1])
        }
        return mTouchPosition
    }

    private fun scrubIncermentally(positionChange: Long): Boolean {
        //Log.e(TAG, "scrubIncermentally")
        if (duration <= 0) return false
        val scrubberPosition = getScrubberPosition()
        mScrubPosition = (mScrubPosition + positionChange).contrain(0, duration)
        if (scrubberPosition == mScrubPosition) return false
        if (!mScrubbing) startScrubbing()
        mListeners.forEach { it.onScrubMove(this, mScrubPosition) }
        update()
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        //Log.e(TAG, "setEnabled")
        super.setEnabled(enabled)
        if (mScrubbing && !enabled) stopScrubbing(true)
    }

    fun setPlayedColor(color: Int) {
        //Log.e(TAG, "setPlayedColor")
        mPlayedPaint.color = color
        invalidate(mSeekBounds)
    }

    private fun startScrubbing() {
        //Log.e(TAG, "startScrubbing")
        mScrubbing = true
        isPressed = true
        parent?.requestDisallowInterceptTouchEvent(true)
        mListeners.forEach { it.onScrubStart(this, getScrubberPosition()) }
    }

    private fun stopScrubbing(canceled: Boolean) {
        //Log.e(TAG, "stopScrubbing")
        mScrubbing = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        mListeners.forEach { it.onScrubStop(this, getScrubberPosition(), canceled) }
    }

    private fun update() {
        //Log.e(TAG, "update")
        mBufferedBar.set(mProgressBar)
        mScrubberBar.set(mProgressBar)
        val newScrubberTime = if (mScrubbing) mScrubPosition else position
        if (duration > 0) {
            val bufferedPixelWidth = ((mProgressBar.width() * bufferedPosition) / duration).toInt()
            mBufferedBar.right = min(mProgressBar.left + bufferedPixelWidth, mProgressBar.right)
            val scrubberPixelPosition = ((mProgressBar.width() * newScrubberTime) / duration).toInt()
            mScrubberBar.right = min(mProgressBar.left + scrubberPixelPosition, mProgressBar.right)
        } else {
            mBufferedBar.right = mProgressBar.left
            mScrubberBar.right = mProgressBar.left
        }
        invalidate(mSeekBounds)
    }


    companion object {
        const val DEFAULT_AD_MARKER_COLOR = 0xB2FFFF00
        const val DEFAULT_BAR_HEIGHT_DP = 4
        const val DEFAULT_INCREMENT_COUNT = 20
        const val DEFAULT_PLAYED_COLOR = 0xFFFFFFFF
        const val DEFAULT_SCRUBBER_DISABLED_SIZE_DP = 0
        const val DEFAULT_SCRUBBER_DRAGGED_SIZE_DP = 16
        const val DEFAULT_SCRUBBER_ENABLED_SIZE_DP = 12
        const val DEFAULT_TOUCH_TARGET_HEIGHT_DP = 26
        const val FINE_SCRUB_RATIO = 3
        const val FINE_SCRUB_Y_THRESHOLD_DP = -50
        const val STOP_SCRUBBING_TIMEOUT_MS = 1000L
        private const val INDEX_UNSET = -1
        private const val TAG = "TimeBar"
        private const val TIME_UNSET = Long.MIN_VALUE + 1
        fun getDefaultBufferedColor(playedColor: Int): Int {
            //Log.e(TAG, "getDefaultBufferedColor")
            return -0x34000000 or (playedColor and 0x00FFFFFF)
        }

        fun getDefaultPlayedAdMarkerColor(adMarkerColor: Int): Int {
            //Log.e(TAG, "getDefaultPlayedAdMarkerColor")
            return 0x33000000 or (adMarkerColor and 0x00FFFFFF)
        }

        fun getDefaultScrubberColor(playedColor: Int): Int {
            //Log.e(TAG, "getDefaultScrubberColor")
            return -0x1000000 or playedColor
        }

        fun getDefaultUnplayedColor(playedColor: Int): Int {
            //Log.e(TAG, "getDefaultUnplayedColor")
            return 0x33000000 or (playedColor and 0x00FFFFFF)
        }
    }

    interface OnScrubListener {
        fun onScrubStart(timeBar: TimeBar, position: Long)
        fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean)
        fun onScrubMove(timeBar: TimeBar, position: Long)
    }
}