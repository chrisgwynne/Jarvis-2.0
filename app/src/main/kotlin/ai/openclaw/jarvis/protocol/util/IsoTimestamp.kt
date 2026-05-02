package ai.openclaw.jarvis.protocol.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UTC ISO-8601 timestamp helper used on every protocol envelope.
 * Kept as a separate object so test code can swap the clock if needed
 * (currently a thin wrapper around `Date()` — protocol replay/golden
 * tests pass the timestamp explicitly).
 */
object IsoTimestamp {
    fun now(): String = format(System.currentTimeMillis())

    fun format(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }
}
