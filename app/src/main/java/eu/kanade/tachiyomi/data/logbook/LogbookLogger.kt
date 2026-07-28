package eu.kanade.tachiyomi.data.logbook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.domain.logbook.interactor.InsertLogbookEntry
import tachiyomi.domain.logbook.model.LogbookActionType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object LogbookLogger {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val insertLogbookEntry: InsertLogbookEntry by lazy { Injekt.get() }

    fun logLibraryAdd(mangaId: Long, mangaTitle: String, categoryName: String? = null) {
        scope.launch {
            val title = if (!categoryName.isNullOrBlank()) {
                "Added \"$mangaTitle\" to library in category \"$categoryName\""
            } else {
                "Added \"$mangaTitle\" to library"
            }
            insertLogbookEntry.await(
                actionType = LogbookActionType.LIBRARY,
                title = title,
                targetId = mangaId,
                targetName = mangaTitle,
                extraData = categoryName,
            )
        }
    }

    fun logLibraryRemove(mangaId: Long, mangaTitle: String) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.LIBRARY,
                title = "Removed \"$mangaTitle\" from library",
                targetId = mangaId,
                targetName = mangaTitle,
            )
        }
    }

    fun logCategoryChange(mangaId: Long, mangaTitle: String, categoryName: String) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.LIBRARY,
                title = "Moved \"$mangaTitle\" to category \"$categoryName\"",
                targetId = mangaId,
                targetName = mangaTitle,
                extraData = categoryName,
            )
        }
    }

    fun logReadingStart(mangaId: Long, mangaTitle: String, chapterName: String) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.READING,
                title = "Started reading \"$mangaTitle\" - $chapterName",
                targetId = mangaId,
                targetName = mangaTitle,
                extraData = chapterName,
            )
        }
    }

    fun logReadingFinish(mangaId: Long, mangaTitle: String, chapterName: String) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.READING,
                title = "Finished reading \"$mangaTitle\" - $chapterName",
                targetId = mangaId,
                targetName = mangaTitle,
                extraData = chapterName,
            )
        }
    }

    fun logSettingChange(settingName: String, newValue: String, destination: String? = null) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.SETTINGS,
                title = "Changed setting \"$settingName\" to $newValue",
                targetName = settingName,
                extraData = destination,
            )
        }
    }

    fun logExtensionAction(extensionName: String, action: String) {
        scope.launch {
            insertLogbookEntry.await(
                actionType = LogbookActionType.EXTENSIONS,
                title = "$action extension \"$extensionName\"",
                targetName = extensionName,
            )
        }
    }
}
