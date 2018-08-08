package psycho.euphoria.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.ErrorMessageProvider


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
    var player: Player? = null

    private val mController: PlayerControlView

    private var mErrorMessageView: View? = null
    private var mContentFrame: AspectRatioFrameLayout?
    private var mShutterView: View?
    private var mAtrworkView: View?
    private var mBufferingView: View?


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
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

    fun showController() {
        showController(shouldShowControllerIndefinitely())
    }

    fun hideController() {
        mController?.let {
            it.hide()
        }
    }

    private fun showController(showIndefinitely: Boolean) {
        if (!useController) {
            return
        }
        mController.showTimeoutMs = if (showIndefinitely) 0 else controllerShowTimeoutMs
        mController.show()
    }

    private fun shouldShowControllerIndefinitely(): Boolean {

        player?.let {
            val playbackState = it.playbackState
            return controllerAutoShow && (playbackState == Player.STATE_IDLE
                    || playbackState == Player.STATE_ENDED
                    || !it.playWhenReady)
        } ?: run { return true }

    }

    private fun isPlayingAd(): Boolean {
        return player?.isPlayingAd == true && player?.playWhenReady == true
    }

    private fun updateErrorMessage() {
        mErrorMessageView?.let {

        }
    }

    companion object {
        const val SURFACE_TYPE_NONE = 0
        const val SURFACE_TYPE_SURFACE_VIEW = 1
        const val SURFACE_TYPE_TEXTURE_VIEW = 2

        private fun setResizeModeRaw(aspectRatioFrameLayout: AspectRatioFrameLayout, resizeMode: Int) {
            aspectRatioFrameLayout.resizeMode = resizeMode
        }
    }
}