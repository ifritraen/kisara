package tachiyomi.data.logbook

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.logbook.model.LogbookActionType
import tachiyomi.domain.logbook.model.LogbookEntry
import tachiyomi.domain.logbook.repository.LogbookRepository

class LogbookRepositoryImpl(
    private val handler: DatabaseHandler,
) : LogbookRepository {

    override fun getAllAsFlow(): Flow<List<LogbookEntry>> {
        return handler.subscribeToList {
            logbookQueries.getAll(::mapLogbook)
        }
    }

    override fun getByActionTypeAsFlow(actionType: LogbookActionType): Flow<List<LogbookEntry>> {
        return handler.subscribeToList {
            logbookQueries.getByActionType(actionType.name, ::mapLogbook)
        }
    }

    override fun searchAsFlow(query: String): Flow<List<LogbookEntry>> {
        return handler.subscribeToList {
            logbookQueries.search(query, ::mapLogbook)
        }
    }

    override suspend fun insert(entry: LogbookEntry) {
        handler.await {
            logbookQueries.insert(
                timestamp = entry.timestamp,
                actionType = entry.actionType.name,
                title = entry.title,
                targetId = entry.targetId,
                targetName = entry.targetName,
                extraData = entry.extraData,
            )
        }
    }

    override suspend fun deleteAll() {
        handler.await {
            logbookQueries.deleteAll()
        }
    }

    override suspend fun deleteOlderThan(timestamp: Long) {
        handler.await {
            logbookQueries.deleteOlderThan(timestamp)
        }
    }

    private fun mapLogbook(
        id: Long,
        timestamp: Long,
        actionType: String,
        title: String,
        targetId: Long?,
        targetName: String?,
        extraData: String?,
    ): LogbookEntry {
        return LogbookEntry(
            id = id,
            timestamp = timestamp,
            actionType = runCatching { LogbookActionType.valueOf(actionType) }.getOrDefault(LogbookActionType.SYSTEM),
            title = title,
            targetId = targetId,
            targetName = targetName,
            extraData = extraData,
        )
    }
}
