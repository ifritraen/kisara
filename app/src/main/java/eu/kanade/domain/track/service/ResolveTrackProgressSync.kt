package eu.kanade.domain.track.service

class ResolveTrackProgressSync {
    fun resolve(
        local: Double,
        remote: Double,
        pullEnabled: Boolean,
        trigger: Trigger,
    ): SyncAction {
        return when {
            local == remote -> SyncAction.NoOp
            pullEnabled && remote > local -> SyncAction.MarkLocalUntil(remote)
            remote < local -> SyncAction.PushRemoteTo(local)
            else -> SyncAction.NoOp
        }
    }

    enum class Trigger {
        OPEN_REFRESH,
    }

    sealed class SyncAction {
        data object NoOp : SyncAction()
        data class MarkLocalUntil(val value: Double) : SyncAction()
        data class PushRemoteTo(val value: Double) : SyncAction()
    }
}
