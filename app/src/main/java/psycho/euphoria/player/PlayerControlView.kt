package psycho.euphoria.player

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.util.RepeatModeUtil
import com.google.android.exoplayer2.util.Util
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


class PlayerControlView : FrameLayout {

    var controlDispatcher = DefaultControlDispatcher()
        set(value) {
            if (value == null) {
                field = DefaultControlDispatcher()
            } else {
                controlDispatcher = value
            }
        }
    var player: Player? = null
        set(value) {
            if (field == value) {
                return
            }
            if (field != null) {
                field?.removeListener(mComponetListener)
            }
            field = value
            field?.let {
                it.addListener(mComponetListener)
            }
            updateAll()
        }

    private val mFormatter: Formatter
    private var mCurrentMode = 0
    private var mHideAtMs = 0L
    private val mComponetListener = ComponetListener()
    private var mIsAttachedToWindow = false
    private var mMultiWindowTimeBar = false
    private var mScrubbing = false
    private var mTimeBar: TimeBar? = null
    private var mRepeatOffButtonDrawable: Drawable? = null
    private var mRepeatOneButtonDrawable: Drawable? = null
    private var mRepeatAllButtonDrawable: Drawable? = null
    private var mRepeatToggleButton: ImageView? = null
    private var mExtraAdGroupTimesMs: LongArray? = null
    private var mRepeatOneButtonContentDescription: String? = null
    private var mRepeatAllButtonContentDescription: String? = null
    private var mRepeatOffButtonContentDescription: String? = null
    private var mPositionView: TextView? = null
    private var mDurationView: TextView? = null
    private var mPreviousButton: View? = null
    private var mPauseButton: View? = null
    private var mPlayButton: View? = null
    private var mRewindButton: View? = null
    private var mFastForwardButton: View? = null
    private var mNextButton: View? = null
    private val mHideAction = Runnable { hide() }
    private val mUpdateProgressAction = Runnable { updateProgress() }
    private val mFormatBuilder = StringBuilder()
    private val mPeriod = Timeline.Period()
    private val mWindow = Timeline.Window()

    var showShuffleButton = false
    var showMultiWindowTimeBar = false
        set(value) {
            field = value
            updateTimeBarMode()
        }
    var visibilityListener: VisibilityListener? = null
    var shuffleButton: View? = null
        set(value) {
            field = value
            updateShuffleButton()
        }
    var playbackPreparer: PlaybackPreparer? = null
    var rewindMs = 0L
        set(value) {
            field = value
            updateNavigation()
        }
    var fastForwardMs = 0L
        set(value) {
            field = value
            updateNavigation()
        }
    var showTimeoutMs = 0L
        set(value) {
            field = value
            if (isVisible()) {
                hideAfterTimeout()
            }
        }
    var repeatToggleModes = 0
        set(value) {
            player?.let {

                if (field == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE && mCurrentMode != Player.REPEAT_MODE_OFF) {
                    controlDispatcher.dispatchSetRepeatMode(it, Player.REPEAT_MODE_OFF)
                } else if (field == RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE && mCurrentMode == Player.REPEAT_MODE_ALL) {
                    controlDispatcher.dispatchSetRepeatMode(it, Player.REPEAT_MODE_ONE)
                } else if (field == RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL && mCurrentMode == Player.REPEAT_MODE_ONE) {
                    controlDispatcher.dispatchSetRepeatMode(it, Player.REPEAT_MODE_ALL)
                } else {
                }
            }
        }


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)


    init {
        val controllerLayoutId = R.layout.exo_player_control_view
        rewindMs = DEFAULT_REWIND_MS.toLong()
        fastForwardMs = DEFAULT_FAST_FORWARD_MS.toLong()
        showTimeoutMs = DEFAULT_SHOW_TIMEOUT_MS
        repeatToggleModes = DEFAULT_REPEAT_TOGGLE_MODES
        mHideAtMs = C.TIME_UNSET
        showShuffleButton = false

        mFormatter = Formatter(mFormatBuilder, Locale.getDefault())

        controlDispatcher = com.google.android.exoplayer2.DefaultControlDispatcher()
        LayoutInflater.from(context).inflate(controllerLayoutId, this)
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        mDurationView = findViewById(R.id.exo_duration)
        mPositionView = findViewById(R.id.exo_position)
        mTimeBar = findViewById(R.id.exo_progress)

        mTimeBar?.addListener(mComponetListener)
        mPlayButton = findViewById(R.id.exo_play);
        mPlayButton?.setOnClickListener(mComponetListener)

        mPauseButton = findViewById(R.id.exo_play);
        mPauseButton?.setOnClickListener(mComponetListener)

        mPreviousButton = findViewById(R.id.exo_play);
        mPreviousButton?.setOnClickListener(mComponetListener)

        mNextButton = findViewById(R.id.exo_play);
        mNextButton?.setOnClickListener(mComponetListener)
        mRewindButton = findViewById(R.id.exo_play);
        mRewindButton?.setOnClickListener(mComponetListener)
        mFastForwardButton = findViewById(R.id.exo_play);
        mFastForwardButton?.setOnClickListener(mComponetListener)
        mRepeatToggleButton = findViewById(R.id.exo_play);
        mRepeatToggleButton?.setOnClickListener(mComponetListener)
        shuffleButton = findViewById(R.id.exo_shuffle);
        shuffleButton?.setOnClickListener(mComponetListener)

        val resources = context.resources
        mRepeatOffButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_off)
        mRepeatOneButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_one)
        mRepeatAllButtonDrawable = resources.getDrawable(R.drawable.exo_controls_repeat_all)
        mRepeatOffButtonContentDescription = resources.getString(R.string.exo_controls_repeat_off_description)
        mRepeatOneButtonContentDescription = resources.getString(R.string.exo_controls_repeat_one_description)
        mRepeatAllButtonContentDescription = resources.getString(R.string.exo_controls_repeat_all_description)
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return dispatchMediaKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    fun dispatchMediaKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (player == null || !isHandledMediaKey(keyCode)) return false
        return true
    }

    private fun fastForward() {
        if (fastForwardMs <= 0) return
        val durationMs = player?.duration ?: 0L
        var seekPositionMs = (player?.currentPosition ?: 0L) + fastForwardMs
        if (durationMs != C.TIME_UNSET) {
            seekPositionMs = min(seekPositionMs, durationMs)
        }
        seekTo(seekPositionMs)
    }

    fun hide() {
        if (isVisible()) {
            visibility = View.GONE
            visibilityListener?.onVisibilityChange(visibility)
            removeCallbacks(mUpdateProgressAction)
            removeCallbacks(mHideAction)
            mHideAtMs = C.TIME_UNSET
        }
    }

    private fun hideAfterTimeout() {
        removeCallbacks(mHideAction)
        if (showTimeoutMs > 0) {
            mHideAtMs = SystemClock.uptimeMillis() + showTimeoutMs
            if (mIsAttachedToWindow) {
                postDelayed(mHideAction, showTimeoutMs.toLong())
            }
        } else {
            mHideAtMs = C.TIME_UNSET
        }
    }

    private fun isPlaying(): Boolean {
        return player?.playbackState != Player.STATE_ENDED
                && player?.playbackState != Player.STATE_IDLE
                && player?.playWhenReady == true
    }

    fun isVisible(): Boolean {
        return visibility == View.VISIBLE
    }

    private fun next() {
        player?.let {
            val timeline = it.currentTimeline
            if (timeline.isEmpty) return
            val windowIndex = it.currentWindowIndex
            val nextWindowIndex = it.nextWindowIndex
            if (nextWindowIndex != C.INDEX_UNSET) {
                seekTo(nextWindowIndex, C.TIME_UNSET)
            } else if (timeline.getWindow(windowIndex, mWindow, false).isDynamic) {
                seekTo(windowIndex, C.TIME_UNSET)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mIsAttachedToWindow = true
        if (mHideAtMs != C.TIME_UNSET) {
            val delayMs = mHideAtMs - SystemClock.uptimeMillis()
            if (delayMs <= 0) hide()
            else postDelayed(mHideAction, delayMs)
        } else if (isVisible()) {
            hideAfterTimeout()
        }
        updateAll()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mIsAttachedToWindow = false
        removeCallbacks(mUpdateProgressAction)
        removeCallbacks(mHideAction)
    }

    private fun previous() {
        player?.let {
            val timeline = it.currentTimeline
            if (timeline.isEmpty) return
            val windowIndex = it.currentWindowIndex
            timeline.getWindow(windowIndex, mWindow)
            val previousWindowIndex = it.previousWindowIndex
            if (previousWindowIndex != C.INDEX_UNSET
                    && (it.currentPosition <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS
                            || (mWindow.isDynamic && !mWindow.isSeekable))) {
            } else {
            }
        }
    }

    private fun requestPlayPauseFocus() {
        val playing = isPlaying()
        if (mPlayButton != null && !playing) {
            mPlayButton?.requestFocus()
        } else if (playing && mPauseButton != null) {
            mPauseButton?.requestFocus()
        }
    }

    private fun rewind() {
        if (rewindMs <= 0) return
        seekTo(max((player?.currentPosition ?: 0L) - rewindMs, 0))
    }

    private fun seekTo(positionMs: Long) {
        player?.let {
            seekTo(it.currentWindowIndex, positionMs)
        }
    }

    private fun seekTo(windowIndex: Int, positionMs: Long) {
        val dispatched = controlDispatcher.dispatchSeekTo(player, windowIndex, positionMs)
        if (!dispatched) {
            updateProgress()
        }
    }

    private fun seekToTimeBarPosition(positionMillis: Long) {
        player?.let {
            var positionMs = positionMillis
            var windowIndex = 0
            val timeline = it.currentTimeline
            if (mMultiWindowTimeBar && !timeline.isEmpty) {
                val windowCount = timeline.windowCount
                windowIndex = 0
                while (true) {
                    val windowDurationMs = timeline.getWindow(windowIndex, mWindow).durationMs
                    if (positionMs < windowDurationMs) {
                        break
                    } else if (windowIndex == windowCount - 1) {
                        positionMs = windowDurationMs
                        break
                    }
                    positionMs = positionMs - windowDurationMs;
                    windowIndex++
                }
            } else {
                windowIndex = it.currentWindowIndex
            }
            seekTo(windowIndex, positionMs)
        }
    }

    private fun setButtonEnabled(enabled: Boolean, view: View?) {
        view?.apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.3f
            visibility = View.VISIBLE
        }
    }

    fun show() {
        if (!isVisible()) {
            visibility = View.VISIBLE
            visibilityListener?.onVisibilityChange(visibility)
            updateAll()
            requestPlayPauseFocus()
        }
        hideAfterTimeout()
    }

    private fun updateAll() {
        updatePlayPauseButton()
        updateNavigation()
        updateRepeatModeButton()
        updateShuffleButton()
        updateProgress()
    }

    private fun updateNavigation() {
        if (!isVisible() || !mIsAttachedToWindow) {
            return
        }
        val timeline = player?.currentTimeline
        val haveNonEmptyTimeline = timeline?.isEmpty != true
        var isSeekable = false
        var enablePrevious = false
        var enableNext = false
        player?.let {
            if (haveNonEmptyTimeline && !it.isPlayingAd) {
                val windowIndex = it.currentWindowIndex
                timeline?.getWindow(windowIndex, mWindow)
                isSeekable = mWindow.isSeekable
                enablePrevious =
                        isSeekable || !mWindow.isDynamic || it.previousWindowIndex != C.INDEX_UNSET;
                enableNext = mWindow.isDynamic || it.nextWindowIndex != C.INDEX_UNSET;
            }
        }
        setButtonEnabled(enablePrevious, mPreviousButton)
        setButtonEnabled(enableNext, mNextButton)
        setButtonEnabled(fastForwardMs > 0 && isSeekable, mFastForwardButton)
        setButtonEnabled(rewindMs > 0 && isSeekable, mRewindButton)
        mTimeBar?.let {
            it.isEnabled = isSeekable
        }
    }

    private fun updatePlayPauseButton() {
        if (!isVisible() || !mIsAttachedToWindow) {
            return
        }
        var requestPlayPauseFocus = false
        val playing = isPlaying()
        mPlayButton?.let {
            requestPlayPauseFocus = requestPlayPauseFocus or (playing && it.isFocused)
            it.visibility = if (playing) View.GONE else View.VISIBLE
        }
        mPauseButton?.let {
            requestPlayPauseFocus = requestPlayPauseFocus or (!playing && it.isFocused)
            it.visibility = if (!playing) View.GONE else View.VISIBLE
        }
        if (requestPlayPauseFocus) requestPlayPauseFocus()
    }

    private fun updateProgress() {
        if (!isVisible() || !mIsAttachedToWindow) return
        var position = 0L
        var bufferedPosition = 0L
        var duration = 0L
        player?.let {
            var currentWindowTimeBarOffsetUs = 0L
            var durationUs = 0L
            var adGroupCount = 0
            val timeline = it.currentTimeline
            if (!timeline.isEmpty) {
                val currentWindowIndex = it.currentWindowIndex
                val firstWindowIndex = if (mMultiWindowTimeBar) 0 else currentWindowIndex
                val lastWindowIndex = if (mMultiWindowTimeBar) timeline.windowCount - 1 else currentWindowIndex
                for (i in firstWindowIndex..lastWindowIndex) {
                    if (i == currentWindowIndex) {
                        currentWindowTimeBarOffsetUs = durationUs
                    }
                    timeline.getWindow(i, mWindow)
                    if (mWindow.durationUs == C.TIME_UNSET) {
                        break
                    }
                    for (j in mWindow.firstPeriodIndex..mWindow.lastPeriodIndex) {
                        timeline.getPeriod(j, mPeriod)
                        val periodAdGroupCount = mPeriod.adGroupCount
                        for (adGroupIndex in 0 until periodAdGroupCount) {
                        }
                    }
                    durationUs += mWindow.durationUs
                }
            }
            duration = C.usToMs(durationUs)
            position = C.usToMs(currentWindowTimeBarOffsetUs)
            bufferedPosition = position
            if (it.isPlayingAd) {
                position += it.currentPosition
                bufferedPosition = position
            } else {
                position += it.currentPosition
                bufferedPosition += it.bufferedPercentage
            }
            if (mTimeBar != null) {
                //val extraAdGroupCount = mExtraAdGroupTimesMs?.let { it.size } ?: 0
            }
        }
        mDurationView?.apply {
            text = Util.getStringForTime(mFormatBuilder, mFormatter, duration)
        }
        mPositionView?.apply {
            if (!mScrubbing) {
                text = Util.getStringForTime(mFormatBuilder, mFormatter, duration)
            }
        }
        mTimeBar?.let {
            it.position = position
            it.bufferedPosition = bufferedPosition
            it.duration = duration
        }
        removeCallbacks(mUpdateProgressAction)
        val playbackState = player?.playbackState ?: run { Player.STATE_IDLE }
        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            var delayMs = 0L
            player?.let {
                if (it.playWhenReady && playbackState == Player.STATE_READY) {
                    val playbackSpeed = it.playbackParameters.speed
                    if (playbackSpeed <= 0.1f) {
                        delayMs = 1000L
                    } else if (playbackSpeed <= 5f) {
                        val mediaTimeUpdatePeriodMs = 1000 / max(1f, round(1 / playbackSpeed))
                        var mediaTimeDelayMs = mediaTimeUpdatePeriodMs - (position % mediaTimeUpdatePeriodMs)
                        if (mediaTimeDelayMs < (mediaTimeUpdatePeriodMs / 5)) {
                            mediaTimeDelayMs += mediaTimeUpdatePeriodMs
                        }
                        delayMs = if (playbackSpeed == 1f) mediaTimeDelayMs.toLong() else (mediaTimeDelayMs / playbackSpeed).toLong()
                    } else {
                        delayMs = 200L
                    }
                } else {
                    delayMs = 1000L
                }
                postDelayed(mUpdateProgressAction, delayMs)
            }
        }
    }

    private fun updateRepeatModeButton() {
        if (!isVisible() || !mIsAttachedToWindow || mRepeatToggleButton == null) return
        if (repeatToggleModes == RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE) {
            mRepeatToggleButton?.visibility = View.GONE
            return
        }
        player?.let {
            setButtonEnabled(false, mRepeatToggleButton)
            return
        }
        setButtonEnabled(true, mRepeatToggleButton)
        player?.let {
            when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> {
                    mRepeatToggleButton?.apply {
                        setImageDrawable(mRepeatOffButtonDrawable)
                        contentDescription = mRepeatOffButtonContentDescription
                    }
                }
                Player.REPEAT_MODE_ONE -> {
                    mRepeatToggleButton?.apply {
                        setImageDrawable(mRepeatOneButtonDrawable)
                        contentDescription = mRepeatOneButtonContentDescription
                    }
                }
                Player.REPEAT_MODE_ALL -> {
                    mRepeatToggleButton?.apply {
                        setImageDrawable(mRepeatAllButtonDrawable)
                        contentDescription = mRepeatAllButtonContentDescription
                    }
                }
                else -> {
                }
            }
            mRepeatToggleButton?.visibility = View.VISIBLE
        }
    }

    private fun updateShuffleButton() {
        if (!isVisible() || !mIsAttachedToWindow || shuffleButton == null) {
            return
        }
        shuffleButton?.let {
            if (!showShuffleButton) {
                it.visibility = View.GONE
            } else if (player == null) {
                setButtonEnabled(false, shuffleButton)
            } else {
                it.alpha = if (player?.shuffleModeEnabled == true) 1f else 0.3f
                it.isEnabled = true
                it.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTimeBarMode() {
        player?.let {
            mMultiWindowTimeBar = showMultiWindowTimeBar && canShowMultiWindowTimeBar(it.currentTimeline, mWindow)
        }
    }

    companion object {
        private const val MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR = 100
        private const val MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000
        const val DEFAULT_FAST_FORWARD_MS = 15000
        const val DEFAULT_REPEAT_TOGGLE_MODES =
                RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE
        const val DEFAULT_REWIND_MS = 5000
        const val DEFAULT_SHOW_TIMEOUT_MS = 5000L

        private fun isHandledMediaKey(keyCode: Int): Boolean {
            return (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }

        private fun canShowMultiWindowTimeBar(timeline: Timeline, window: Timeline.Window): Boolean {

            if (timeline.windowCount > MAX_WINDOWS_FOR_MULTI_WINDOW_TIME_BAR) {
                return false
            }
            val windowCount = timeline.windowCount
            for (i in 0 until windowCount) {
                if (timeline.getWindow(i, window).durationUs == C.TIME_UNSET) {
                    return false
                }
            }
            return true
        }

    }


    interface VisibilityListener {
        fun onVisibilityChange(visibility: Int)
    }

    private inner class ComponetListener : Player.DefaultEventListener(), TimeBar.OnScrubListener, OnClickListener {

        override fun onClick(view: View) {
            player?.let {
                when {
                    view == mNextButton -> next()
                    view == mPreviousButton -> previous()
                    view == mFastForwardButton -> fastForward()
                    view == mRewindButton -> rewind()
                    view == mPlayButton -> {
                        if (it.playbackState == Player.STATE_IDLE) {
                            playbackPreparer?.preparePlayback()
                        } else if (it.playbackState == Player.STATE_ENDED) {
                            controlDispatcher.dispatchSeekTo(it, it.currentWindowIndex, C.TIME_UNSET)
                        } else {
                            controlDispatcher.dispatchSetPlayWhenReady(it, true)
                        }
                    }
                    view == mPauseButton -> controlDispatcher.dispatchSetPlayWhenReady(it, false)
                    view == mRepeatToggleButton -> controlDispatcher.dispatchSetRepeatMode(it, RepeatModeUtil.getNextRepeatMode(it.repeatMode, repeatToggleModes))
                    view == shuffleButton -> controlDispatcher.dispatchSetShuffleModeEnabled(it, !it.shuffleModeEnabled)
                    else -> {
                    }
                }
            }
            hideAfterTimeout()
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            updatePlayPauseButton()
            updateProgress()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatModeButton()
            updateNavigation()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleButton()
            updateNavigation()
        }

        override fun onPositionDiscontinuity(reason: Int) {
            updateNavigation()
            updateProgress()
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            updateNavigation()
            updateTimeBarMode()
            updateProgress()
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            mPositionView?.let {
                it.text = Util.getStringForTime(mFormatBuilder, mFormatter, position)
            }
        }

        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            removeCallbacks(mHideAction)
            mScrubbing = true
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            mScrubbing = false
            if (!canceled && player != null) {
                seekToTimeBarPosition(position)
            }
            hideAfterTimeout()
        }
    }
}