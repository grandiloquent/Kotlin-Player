package psycho.euphoria.player

import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import java.io.File
import java.text.DecimalFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min


const val REQUEST_PERMISSION_CODE = 10


private const val TIME_UNSET = Long.MIN_VALUE + 1


fun Drawable.setDrawableLayoutDirection(layoutDirection: Int) = Build.VERSION.SDK_INT >= 23 && setLayoutDirection(layoutDirection)
fun Int.dpToPx(metrics: DisplayMetrics) = (this * metrics.density + 0.5f).toInt()
fun Int.contrain(minValue: Int, maxValue: Int) = max(minValue, min(this, maxValue))
fun Long.contrain(minValue: Long, maxValue: Long) = max(minValue, min(this, maxValue))

fun Long.getStringForTime(sb: StringBuilder, formatter: Formatter): String {
    var timeMs = this

    if (this == TIME_UNSET) {
        timeMs = 0
    }
    val totalSeconds = (timeMs + 500) / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    sb.setLength(0)
    return if (hours > 0)
        formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
    else
        formatter.format("%02d:%02d", minutes, seconds).toString()
}

inline fun atLeast(sdk: Int, f1: () -> Unit, f2: () -> Unit) {
    if (Build.VERSION.SDK_INT >= sdk) f1()
    else f2.invoke()
}

inline fun atMost(sdk: Int, f1: () -> Unit, f2: () -> Unit) {
    if (Build.VERSION.SDK_INT <= sdk) f1()
    else f2.invoke()
}


fun File.isVideo(): Boolean {
    return arrayOf(".mp4", ".flv").any { name.endsWith(it, true) }
}

inline fun more(sdk: Int, f1: () -> Unit, f2: () -> Unit) {
    if (Build.VERSION.SDK_INT > sdk) f1()
    else f2.invoke()
}

fun Long.formatSize(): String {
    if (this <= 0)
        return "0 B"

    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(toDouble()) / Math.log10(1024.0)).toInt()
    return "${DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
