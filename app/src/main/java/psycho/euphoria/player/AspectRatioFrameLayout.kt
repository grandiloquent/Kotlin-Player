package psycho.euphoria.player

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.abs

class AspectRatioFrameLayout : FrameLayout {

    var listener: AspectRatioListener? = null
    var videoAspectRatio = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var resizeMode = RESIZE_MODE_FIT
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    private val mDispatcher = AspectRatioUpdatedDispatcher()


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (videoAspectRatio <= 0f) return
        var mw = measuredWidth
        var mh = measuredHeight
        val vr = mw.toFloat() / mh
        val aspectDeformation = videoAspectRatio / vr - 1
        if (abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
            mDispatcher.scheduleUpdate(videoAspectRatio, vr, false)
            return
        }
        when (resizeMode) {
            RESIZE_MODE_FIXED_WIDTH -> mh = (mw / videoAspectRatio).toInt()
            RESIZE_MODE_FIXED_HEIGHT -> mw = (mh * videoAspectRatio).toInt()
            RESIZE_MODE_ZOOM -> {
                if (aspectDeformation > 0f) {
                    mw = (mh * videoAspectRatio).toInt()
                } else {
                    mh = (mw / videoAspectRatio).toInt()
                }
            }
            RESIZE_MODE_FILL -> {
                if (aspectDeformation > 0f) {
                    mh = (mw / videoAspectRatio).toInt()
                } else {
                    mw = (mh * videoAspectRatio).toInt()
                }
            }
            else -> {
            }
        }

        mDispatcher.scheduleUpdate(videoAspectRatio, vr, true)
        super.onMeasure(MeasureSpec.makeMeasureSpec(mw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mh, MeasureSpec.EXACTLY))
    }

    companion object {
        private const val TAG = "AspectRatioFrameLayout"
        const val RESIZE_MODE_FILL = 3
        const val RESIZE_MODE_FIT = 0
        const val RESIZE_MODE_FIXED_HEIGHT = 2
        const val RESIZE_MODE_FIXED_WIDTH = 1
        const val RESIZE_MODE_ZOOM = 4
        private const val MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f
    }

    private inner class AspectRatioUpdatedDispatcher : Runnable {
        private var mIsScheuled = false
        private var mTargetRatio = 0f
        private var mNaturalRatio = 0f
        private var mAspectRatioMisMatch = false


        fun scheduleUpdate(targetRatio: Float, naturalRatio: Float, aspectRatioMisMatch: Boolean) {
            mTargetRatio = targetRatio
            mNaturalRatio = naturalRatio
            mAspectRatioMisMatch = aspectRatioMisMatch
            if (mIsScheuled) {
                mIsScheuled = true
                post(this)
            }
        }

        override fun run() {
            mIsScheuled = false
            listener?.apply {
                onAspectRatioUpdated(mTargetRatio, mNaturalRatio, mAspectRatioMisMatch)
            }


        }
    }

    interface AspectRatioListener {
        fun onAspectRatioUpdated(targetRatio: Float, naturalRatio: Float, ratioMismatch: Boolean)
    }
}
