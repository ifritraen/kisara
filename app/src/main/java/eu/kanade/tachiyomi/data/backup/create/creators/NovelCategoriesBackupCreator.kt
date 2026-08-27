package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoriesBackupCreator(
    private val getCategories: GetNovelCategories = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getCategories.await()
            .filterNot { it.id == 0L || it.order == -1L }
            .map {
                BackupCategory(
                    name = it.name,
                    order = it.order,
                    id = it.id,
                    flags = it.flags,
                    hidden = it.hidden,
                    parentId = it.parentId,
                )
            }
    }
}
