package psycho.euphoria.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackPreparer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.metadata.id3.ApicFrame
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.video.VideoListener
import android.view.KeyEvent.KEYCODE_DPAD_CENTER


class PlayerView : FrameLayout {

    var errorMessageProvider: ErrorMessageProvider<in ExoPlaybackException>? = null
        set(value) {
            if (field != value) {
                field = value
                updateErrorMessage()
            }

        }

    var defaultArtwork: Bitmap? = null
    var useArtwork = false
    var showBuffering = false
    var keepContentOnPlayerReset = false
    var customErrorMessage: CharSequence? = null
    var controllerShowTimeoutMs = 0L
        set(value) {
            field = value
            if (mController.isVisible()) {
                showController()
            }
        }
    var controllerHideOnTouch = false
    var controllerAutoShow = false
    var controllerHideOnAds = false
    var surfaceView: View? = null
    var overFrameLayout: FrameLayout? = null
    var subtitleView: SubtitleView? = null
    var useController = false

    private val mController: PlayerControlView


    private var mComponentListener: ComponentListener? = null
    private var mErrorMessageView: View? = null
    private var mContentFrame: AspectRatioFrameLayout?
    private var mShutterView: View?
    private var mAtrworkView: ImageView?
    private var mBufferingView: View?
    private var mTextureViewRotation = 0


    var player: Player? = null
        set(value) {
            if (field == value) return
            field?.let {
                it.removeListener(mComponentListener)
                val old = it.videoComponent
                if (old != null) {
                    old.removeVideoListener(mComponentListener)
                    surfaceView?.let {
                        if (it is TextureView) {
                            old.clearVideoTextureView(it)
                        } else if (it is SurfaceView) {
                            old.clearVideoSurfaceView(it)
                        }
                    }
                }
                it.textComponent?.let {
                    it.removeTextOutput(mComponentListener)
                }
            }
            field = value
            if (useController) mController.player = player
            subtitleView?.setCues(null)

            updateBuffering()
            updateForCurrentTrackSelections(false)


            field?.let {
                it.videoComponent?.let {
                    val videoComponent = it
                    surfaceView?.let {

                        if (it is TextureView) {
                            videoComponent.setVideoTextureView(it)
                        } else if (it is SurfaceView) {
                            videoComponent.setVideoSurfaceView(it)
                        }
                        videoComponent.addVideoListener(mComponentListener)
                    }
                }
                it.addListener(mComponentListener)
                maybeShowController(false)

            } ?: run { hideController() }
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        mComponentListener = ComponentListener()
        
        var playLayoutId = R.layout.exo_player_view
        var shutterColor = -1
        var surfaceType = SURFACE_TYPE_SURFACE_VIEW
        var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        var useArtwork = true
        var defaultArtworkId = 0
        var showBuffering = false

        LayoutInflater.from(context).inflate(playLayoutId, this)

        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        mContentFrame = findViewById(R.id.exo_content_frame)
        mContentFrame?.let {
            setResizeModeRaw(it, resizeMode)
        }
        mShutterView = findViewById(R.id.exo_shutter)
        mShutterView?.let {
            if (shutterColor != -1) it.setBackgroundColor(shutterColor)
        }
        mContentFrame?.let {
            if (surfaceType != SURFACE_TYPE_NONE) {
                val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                surfaceView = if (surfaceType == SURFACE_TYPE_TEXTURE_VIEW) TextureView(context) else SurfaceView(context)
                surfaceView?.layoutParams = params
                it.addView(surfaceView, 0)
                Log.e(TAG, "add Surface View ${surfaceType == SURFACE_TYPE_TEXTURE_VIEW}")
            }
        } ?: run { surfaceView = null }
        overFrameLayout = findViewById(R.id.exo_overlay)

        mAtrworkView = findViewById(R.id.exo_artwork)
        this.useArtwork = useArtwork && mAtrworkView != null
        if (defaultArtworkId != 0) {
            defaultArtwork = BitmapFactory.decodeResource(resources, defaultArtworkId)
        }

        subtitleView = findViewById(R.id.exo_subtitles)
        subtitleView?.apply {
            setUserDefaultStyle()
            setUserDefaultTextSize()
        }
        mBufferingView = findViewById(R.id.exo_buffering)
        mBufferingView?.apply {
            visibility = View.GONE
        }
        this.showBuffering = showBuffering
        mErrorMessageView = findViewById(R.id.exo_error_message)
        mErrorMessageView?.apply {
            visibility = View.GONE
        }
        mController = PlayerControlView(context)
        val controllerPlaceHolder = findViewById<View>(R.id.exo_controller_placeholder)
        controllerPlaceHolder?.let {
            mController.layoutParams = it.layoutParams
            val parent = it.parent as ViewGroup
            val controllerIndex = parent.indexOfChild(it)
            parent.removeView(it)
            parent.addView(mController, controllerIndex)
        }
        this.controllerShowTimeoutMs = PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS
        this.controllerHideOnTouch = true
        this.controllerAutoShow = true
        this.controllerHideOnAds = true
        this.useController = true

        hideController()
    }


    private fun closeShutter() {
    }

    private fun hideArtwork() {
    }

    fun hideController() {
        mController?.let {
            it.hide()
        }
    }

    private fun isPlayingAd(): Boolean {
        return player?.isPlayingAd == true && player?.playWhenReady == true
    }

    private fun maybeShowController(isForced: Boolean) {
        if (isPlayingAd() && controllerHideOnAds) return
        if (useController) {
            val wasShowingIndefinitely = mController.isVisible() && mController.showTimeoutMs <= 0
            val shouldShowIndefinitely = shouldShowControllerIndefinitely()
            if (isForced || wasShowingIndefinitely || shouldShowIndefinitely) {
                showController(shouldShowIndefinitely)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!useController || player == null || event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        if (!mController.isVisible()) {
            maybeShowController(true)
        } else if (controllerHideOnTouch) {
            mController.hide()
        }
        return true
    }

    private fun setArtworkFromBitmap(bitmap: Bitmap?): Boolean {
        bitmap?.let {
            val bw = it.width
            val bh = it.height
            if (bw > 0 && bh > 0) {
                mContentFrame?.apply {
                    videoAspectRatio = bw.toFloat() / bh
                }
                mAtrworkView?.apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE
                    return true
                }
            }
            return false
        } ?: run { return false }
    }

    private fun setArtworkFromMetadata(metadata: Metadata?): Boolean {
        metadata?.let {
            for (i in 0 until it.length()) {
                var entry = it.get(i)
                if (entry is ApicFrame) {
                    val bitmapData = entry.pictureData
                    val bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size)
                    return setArtworkFromBitmap(bitmap)
                }
            }
        }
        return false
    }

    fun setControllerVisibilityListener(listener: PlayerControlView.VisibilityListener) {
        mController.visibilityListener = listener
    }

    fun setPlaybackPreparer(playbackPreparer: PlaybackPreparer) {
        mController.playbackPreparer = playbackPreparer
    }

    private fun shouldShowControllerIndefinitely(): Boolean {
        player?.let {
            val playbackState = it.playbackState
            return controllerAutoShow && (playbackState == Player.STATE_IDLE
                    || playbackState == Player.STATE_ENDED
                    || !it.playWhenReady)
        } ?: run { return true }
    }

    fun showController() {
        showController(shouldShowControllerIndefinitely())
    }

    private fun showController(showIndefinitely: Boolean) {
        if (!useController) {
            return
        }
        mController.showTimeoutMs = if (showIndefinitely) 0 else controllerShowTimeoutMs
        mController.show()
    }

    private fun updateBuffering() {
        mBufferingView?.let {
            val showing = showBuffering && player?.playbackState == Player.STATE_BUFFERING && player?.playWhenReady == true
            mBufferingView?.visibility = if (showing) View.VISIBLE else View.GONE
        }
    }

    private fun updateErrorMessage() {
        mErrorMessageView?.let {
        }
    }

    private fun updateForCurrentTrackSelections(isNewPlayer: Boolean) {
        if (player?.currentTrackGroups?.isEmpty == true) {
            if (!keepContentOnPlayerReset) {
                hideArtwork()
                closeShutter()
            }
            return
        }
        if (isNewPlayer && !keepContentOnPlayerReset) {
            closeShutter()
        }
        player?.let {
            val selections = it.currentTrackSelections
            for (i in 0 until selections.length) {
                if (it.getRendererType(i) == C.TRACK_TYPE_VIDEO && selections[i] != null) {
                    hideArtwork()
                    return
                }
            }
        }
        closeShutter()
        if (useArtwork) {
            player?.let {
                val selections = it.currentTrackSelections
                for (i in 0 until selections.length) {
                    val selection = selections[i]
                    selection?.let {
                        for (j in 0 until it.length()) {
                            val metadata = it.getFormat(j).metadata
                            if (metadata != null && setArtworkFromMetadata(metadata)) return
                        }
                    }
                }
            }
            if (setArtworkFromBitmap(defaultArtwork)) {
                return
            }
        }
        hideArtwork()
    }

    companion object {
        const val SURFACE_TYPE_NONE = 0
        const val SURFACE_TYPE_SURFACE_VIEW = 1
        const val SURFACE_TYPE_TEXTURE_VIEW = 2
        private const val TAG = "PlayerView"
        private fun setResizeModeRaw(aspectRatioFrameLayout: AspectRatioFrameLayout, resizeMode: Int) {
            aspectRatioFrameLayout.resizeMode = resizeMode
        }

        private fun applyTextureViewRotation(textureView: TextureView, textureViewRotation: Int) {
            val tw = textureView.width
            val th = textureView.height
            if (tw == 0 || th == 0 || textureViewRotation == 0) textureView.setTransform(null)
            else {
                val tm = Matrix()
                val px = tw / 2
                val py = th / 2
                tm.postRotate(textureViewRotation.toFloat(), px.toFloat(), py.toFloat())
                val otr = RectF(0.toFloat(), 0.toFloat(), tw.toFloat(), th.toFloat())
                val rtr = RectF()
                tm.mapRect(rtr, otr)
                tm.postScale(tw / rtr.width(),
                        th / rtr.height(),
                        px.toFloat(),
                        py.toFloat())

                textureView.setTransform(tm)
            }
        }

        private fun isDpadKey(keyCode: Int): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (keyCode == KeyEvent.KEYCODE_DPAD_UP
                        || keyCode == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            } else {
                (keyCode == KeyEvent.KEYCODE_DPAD_UP
                        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            }
        }
    }


    private inner class ComponentListener : Player.DefaultEventListener(), TextOutput, VideoListener, OnLayoutChangeListener {

        override fun onRepeatModeChanged(repeatMode: Int) {
            mShutterView?.visibility = View.INVISIBLE
        }

        override fun onPositionDiscontinuity(reason: Int) {
            if (isPlayingAd() && controllerHideOnAds) {
                hideController()
            }
        }

        override fun onCues(cues: MutableList<Cue>?) {

            subtitleView?.onCues(cues)
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            updateBuffering()
            if (isPlayingAd() && controllerHideOnAds) {
                hideController()
            } else {
                maybeShowController(false)
            }
        }

        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            mContentFrame?.let {
                var ratio = if (height == 0 || width == 0) 1f else (width * pixelWidthHeightRatio) / height
                surfaceView?.let {
                    if (it is TextureView) {
                        if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                            ratio = 1 / ratio
                        }
                        if (mTextureViewRotation != 0) {
                            surfaceView?.removeOnLayoutChangeListener(this)
                        }
                        mTextureViewRotation = unappliedRotationDegrees
                        if (mTextureViewRotation != 0) {
                            surfaceView?.addOnLayoutChangeListener(this)
                        }
                        applyTextureViewRotation(it, mTextureViewRotation)
                    }
                }
                it.videoAspectRatio = ratio
            }
        }

        override fun onRenderedFirstFrame() {
        }

        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        }

    }
}