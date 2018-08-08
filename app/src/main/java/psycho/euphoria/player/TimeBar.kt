package psycho.euphoria.player

import android.R.attr.*
import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import java.util.*
import android.graphics.Point
import android.os.Build
import android.view.MotionEvent
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.min
import android.R.attr.min
import android.graphics.Canvas


class TimeBar : View {

    var playerColor: Int
        get() = DEFAULT_PLAYED_COLOR.toInt()
        set(value) {

        }

    val mPlayedPaint = Paint()
    val mScrubberPaint = Paint()
    val mBufferedPaint = Paint()
    val mUnplayedPaint = Paint()
    val mPlayedAdMarkerPaint = Paint()

    val mSeekBounds = Rect()
    val mProgressBar = Rect()
    val mBufferedBar = Rect()
    val mScrubberBar = Rect()
    val mFormatterStringBuilder = StringBuilder()
    val mFormatter: Formatter

    val mStopScrubbingRunable = Runnable { stopScrubbing(false) }
    var mBarHeight = 0
    var mTouchTargetHeight = 0
    var mScrubberDisabledSize = 0
    var mScrubberEnabledSize = 0
    var mScrubberDraggedSize = 0
    var scrubberDrawable: Drawable? = null

    var mScrubberPadding = 0
    var mScrubbing = false
    val mListeners = CopyOnWriteArraySet<OnScrubListener>()
    var mLocationOnScreen: IntArray? = null
    var mTouchPosition: Point? = null
    var mScrubPosition = 0L

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

    private fun isInSeekBar(x: Float, y: Float) = mSeekBounds.contains(x.toInt(), y.toInt())
    private fun getProgressText() = position.getStringForTime(mFormatterStringBuilder, mFormatter)
    private fun getPositionIncrement() = if (keyTimeIncrement == TIME_UNSET) if (duration == TIME_UNSET) 0 else duration / keyCountIncrement else keyTimeIncrement

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        mFormatter = Formatter(mFormatterStringBuilder)

        val metrics = resources.displayMetrics

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

        if (scrubberDrawable == null) {
            mScrubberPadding = (max(mScrubberDisabledSize, max(mScrubberEnabledSize, mScrubberDraggedSize)) + 1) / 2
        }
        isFocusable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }



    fun addListener(listener: OnScrubListener) {
        mListeners.add(listener)
    }
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateDrawableState()
    }
    private fun drawPlayhead(canvas: Canvas) {
        if (duration <= 0) return
        val px = mScrubberBar.right.contrain(mScrubberBar.left, mProgressBar.right)
        val py = mScrubberBar.centerY()
        scrubberDrawable?.let {
            val sw = it.intrinsicWidth
            val sh = it.intrinsicHeight
            it.setBounds(
                    px - sw / 2,
                    py - sh / 2,
                    px + sw / 2,
                    py + sh / 2
            )
            it.draw(canvas)
        } ?: run {
            val scrubberSize = if (mScrubbing || isFocused) mScrubberDraggedSize else if (isEnabled) mScrubberEnabledSize else mScrubberDisabledSize
            canvas.drawCircle(px.toFloat(), py.toFloat(), scrubberSize / 2f, mScrubberPaint)
        }
    }
    private fun drawTimeBar(canvas: Canvas) {
        val progressBarHeight = mProgressBar.height()
        val barTop = (mProgressBar.centerY() - progressBarHeight / 2).toFloat()
        val barBottom = (barTop + progressBarHeight).toFloat()
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
    fun getScrubberPosition(): Long {
        if (mProgressBar.width() <= 0 || duration == TIME_UNSET) return 0L
        return (mScrubberBar.width() * duration) / mProgressBar.width()
    }
    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        scrubberDrawable?.jumpToCurrentState()
    }
    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
            event.text.add(getProgressText())
        event.className = TimeBar::class.java.name
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
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
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val barY = (h - mTouchTargetHeight) / 2
        val seekLeft = paddingLeft
        val seekRight = w - paddingRight
        val progressY = barY + (mTouchTargetHeight - mBarHeight) / 2
        mSeekBounds.set(seekLeft, barY, seekRight, barY + mTouchTargetHeight)
        mProgressBar.set(mSeekBounds.left + mScrubberPadding, progressY, mSeekBounds.right - mScrubberPadding, progressY + mBarHeight)
        update()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hm = MeasureSpec.getMode(heightMeasureSpec)
        val hs = MeasureSpec.getSize(heightMeasureSpec)
        val h = if (hm == MeasureSpec.UNSPECIFIED) mTouchTargetHeight else if (hm == MeasureSpec.EXACTLY) hs else min(mTouchTargetHeight, hs)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
        updateDrawableState()
    }
    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        scrubberDrawable?.let {
            if (it.setDrawableLayoutDirection(layoutDirection)) invalidate()
        }
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
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
        mScrubberBar.right = x.toInt().contrain(mProgressBar.left, mProgressBar.right)
    }
    fun removeListener(listener: OnScrubListener) {
        mListeners.remove(listener)
    }
    private fun resolveRelativeTouchPosition(event: MotionEvent): Point? {
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
        super.setEnabled(enabled)
        if (mScrubbing && !enabled) stopScrubbing(true)
    }
    fun setPlayedColor(color: Int) {
        mPlayedPaint.color = color
        invalidate(mSeekBounds)
    }
    private fun startScrubbing() {
        mScrubbing = true
        isPressed = true
        parent?.requestDisallowInterceptTouchEvent(true)
        mListeners.forEach { it.onScrubStart(this, getScrubberPosition()) }
    }
    private fun stopScrubbing(canceled: Boolean) {
        mScrubbing = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        mListeners.forEach { it.onScrubStop(this, getScrubberPosition(), canceled) }
    }
    private fun update() {
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
    private fun updateDrawableState() {
        if (scrubberDrawable?.isStateful == true && scrubberDrawable?.setState(drawableState) == true)
            invalidate()
    }

    companion object {
        private const val TIME_UNSET = Long.MIN_VALUE + 1
        private const val INDEX_UNSET = -1
        private const val TAG = "TimeBar"
        const val DEFAULT_AD_MARKER_COLOR = 0xB2FFFF00
        const val DEFAULT_AD_MARKER_WIDTH_DP = 4
        const val DEFAULT_BAR_HEIGHT_DP = 4
        const val DEFAULT_INCREMENT_COUNT = 20
        const val DEFAULT_PLAYED_COLOR = 0xFFFFFFFF
        const val DEFAULT_SCRUBBER_DISABLED_SIZE_DP = 0
        const val DEFAULT_SCRUBBER_DRAGGED_SIZE_DP = 16
        const val DEFAULT_SCRUBBER_ENABLED_SIZE_DP = 12
        const val DEFAULT_TOUCH_TARGET_HEIGHT_DP = 26
        const val FINE_SCRUB_RATIO = 3
        const val FINE_SCRUB_Y_THRESHOLD_DP = -50
        const val STOP_SCRUBBING_TIMEOUT_MS = 1000
        fun getDefaultScrubberColor(playedColor: Int): Int {
            return -0x1000000 or playedColor
        }

        fun getDefaultUnplayedColor(playedColor: Int): Int {
            return 0x33000000 or (playedColor and 0x00FFFFFF)
        }

        fun getDefaultBufferedColor(playedColor: Int): Int {
            return -0x34000000 or (playedColor and 0x00FFFFFF)
        }

        fun getDefaultPlayedAdMarkerColor(adMarkerColor: Int): Int {
            return 0x33000000 or (adMarkerColor and 0x00FFFFFF)
        }
    }


    interface OnScrubListener {

        fun onScrubStart(timeBar: TimeBar, position: Long)
        fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean)
        fun onScrubMove(timeBar: TimeBar, position: Long)
    }
}
