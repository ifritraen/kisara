package tachiyomi.domain.logbook.model

data class LogbookEntry(
    val id: Long,
    val timestamp: Long,
    val actionType: LogbookActionType,
    val title: String,
    val targetId: Long?,
    val targetName: String?,
    val extraData: String?,
)

enum class LogbookActionType {
    LIBRARY,
    READING,
    SETTINGS,
    EXTENSIONS,
    SYSTEM,
}
