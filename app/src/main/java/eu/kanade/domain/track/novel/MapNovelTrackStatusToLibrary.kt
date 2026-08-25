package eu.kanade.domain.track.novel

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.library.model.LibraryTrackStatus

class MapNovelTrackStatusToLibrary(
    private val trackerManager: TrackerManager,
) {
    fun map(trackerId: Long, status: Long): LibraryTrackStatus {
        val tracker = trackerManager.get(trackerId) ?: return LibraryTrackStatus.OTHER
        val novelTracker = tracker as? Tracker ?: return LibraryTrackStatus.OTHER
        val statusList = novelTracker.getStatusList()
        return when (status) {
            novelTracker.getReadingStatus() -> LibraryTrackStatus.READING
            novelTracker.getRereadingStatus() -> LibraryTrackStatus.REPEATING
            novelTracker.getCompletionStatus() -> LibraryTrackStatus.COMPLETED
            else -> {
                val statusIndex = statusList.indexOf(status)
                when (statusIndex) {
                    2 -> LibraryTrackStatus.ON_HOLD
                    3 -> LibraryTrackStatus.DROPPED
                    4 -> LibraryTrackStatus.PLAN_TO_READ
                    else -> LibraryTrackStatus.OTHER
                }
            }
        }
    }
}
