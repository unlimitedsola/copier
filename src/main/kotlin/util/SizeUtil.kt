package love.sola.copier.util

import kotlin.math.ln
import kotlin.math.pow

/**
 * Copied from [StackOverflow](https://stackoverflow.com/questions/3758606)
 * @author Sola
 */
fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}
