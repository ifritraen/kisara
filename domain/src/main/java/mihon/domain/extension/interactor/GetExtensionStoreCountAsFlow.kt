package mihon.domain.extension.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.repository.ExtensionStoreRepository

class GetExtensionStoreCountAsFlow(
    private val repository: ExtensionStoreRepository,
) {
    operator fun invoke(): Flow<Long> {
        return repository.getCountAsFlow()
    }
}
