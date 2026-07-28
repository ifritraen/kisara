package tachiyomi.domain.logbook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.logbook.model.LogbookActionType
import tachiyomi.domain.logbook.model.LogbookEntry
import tachiyomi.domain.logbook.repository.LogbookRepository

class GetLogbookEntries(
    private val repository: LogbookRepository,
) {
    fun subscribe(actionType: LogbookActionType? = null, query: String? = null): Flow<List<LogbookEntry>> {
        return when {
            !query.isNullBlanks() -> repository.searchAsFlow("%$query%")
            actionType != null -> repository.getByActionTypeAsFlow(actionType)
            else -> repository.getAllAsFlow()
        }
    }

    private fun String?.isNullBlanks(): Boolean = this.isNullOrBlank()
}

class InsertLogbookEntry(
    private val repository: LogbookRepository,
) {
    suspend fun await(
        actionType: LogbookActionType,
        title: String,
        targetId: Long? = null,
        targetName: String? = null,
        extraData: String? = null,
    ) {
        val entry = LogbookEntry(
            id = 0,
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            title = title,
            targetId = targetId,
            targetName = targetName,
            extraData = extraData,
        )
        repository.insert(entry)
    }
}

class ClearLogbook(
    private val repository: LogbookRepository,
) {
    suspend fun await() {
        repository.deleteAll()
    }
}
