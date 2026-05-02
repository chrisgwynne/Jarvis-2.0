package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val calendarId: Long,
)

interface CalendarCapability : Capability {
    suspend fun getUpcomingEvents(lookaheadMs: Long = 24 * 60 * 60 * 1000L): CapabilityResult<List<CalendarEvent>>
    suspend fun insertEvent(title: String, startMs: Long, endMs: Long): CapabilityResult<Long>
}

@Singleton
class CalendarCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarCapability {

    override val id = "calendar"
    override val description = "Read/write device calendar events"
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    override fun isAvailable() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun getUpcomingEvents(lookaheadMs: Long): CapabilityResult<List<CalendarEvent>> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Calendar permission not granted", true)
        }
        val now = System.currentTimeMillis()
        val end = now + lookaheadMs
        return try {
            val events = mutableListOf<CalendarEvent>()
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.CALENDAR_ID,
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val args = arrayOf(now.toString(), end.toString())
            context.contentResolver.query(uri, projection, selection, args, "${CalendarContract.Events.DTSTART} ASC")
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        events.add(
                            CalendarEvent(
                                id         = cursor.getLong(0),
                                title      = cursor.getString(1) ?: "",
                                startMs    = cursor.getLong(2),
                                endMs      = cursor.getLong(3),
                                calendarId = cursor.getLong(4),
                            )
                        )
                    }
                }
            capabilitySuccess(events)
        } catch (e: Exception) {
            capabilityFailure("CALENDAR_READ_ERROR", e.message ?: "Failed to read calendar")
        }
    }

    override suspend fun insertEvent(title: String, startMs: Long, endMs: Long): CapabilityResult<Long> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Calendar permission not granted", true)
        }
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.CALENDAR_ID, 1L)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull()
                ?: return capabilityFailure("CALENDAR_INSERT_ERROR", "Failed to insert event")
            capabilitySuccess(id)
        } catch (e: Exception) {
            capabilityFailure("CALENDAR_INSERT_ERROR", e.message ?: "Insert failed")
        }
    }
}
