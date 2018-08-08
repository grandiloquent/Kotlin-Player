package psycho.euphoria.player

import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import java.text.DecimalFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

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

fun Long.formatSize(): String {
    if (this <= 0)
        return "0 B"

    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(toDouble()) / Math.log10(1024.0)).toInt()
    return "${DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
