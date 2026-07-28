package tachiyomi.domain.logbook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.logbook.model.LogbookActionType
import tachiyomi.domain.logbook.model.LogbookEntry

interface LogbookRepository {
    fun getAllAsFlow(): Flow<List<LogbookEntry>>
    fun getByActionTypeAsFlow(actionType: LogbookActionType): Flow<List<LogbookEntry>>
    fun searchAsFlow(query: String): Flow<List<LogbookEntry>>
    suspend fun insert(entry: LogbookEntry)
    suspend fun deleteAll()
    suspend fun deleteOlderThan(timestamp: Long)
}
