package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoriesRestorer(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val idMap = mutableMapOf<Long, Long>()
            dbCategories.forEach { dbCat ->
                val backupCat = backupCategories.find { it.name == dbCat.name }
                if (backupCat != null) {
                    idMap[backupCat.id] = dbCat.id
                }
            }

            backupCategories
                .sortedWith(
                    compareBy<BackupCategory> { it.parentId != null }
                        .thenBy { it.order },
                )
                .forEach { backupCat ->
                    val dbCategory = dbCategoriesByName[backupCat.name]
                    val order = dbCategory?.order ?: nextOrder++
                    val mappedParentId = backupCat.parentId?.let { oldParentId ->
                        idMap[oldParentId]
                    }

                    if (dbCategory != null) {
                        idMap[backupCat.id] = dbCategory.id
                        if (mappedParentId != null && dbCategory.parentId != mappedParentId) {
                            handler.await { db ->
                                db.novel_categoriesQueries.update(
                                    categoryId = dbCategory.id,
                                    name = dbCategory.name,
                                    order = dbCategory.order,
                                    flags = dbCategory.flags,
                                    parentId = mappedParentId,
                                    hidden = if (dbCategory.hidden) 1L else 0L,
                                    hidden_from_home_hub = null,
                                )
                            }
                        }
                    } else {
                        val newId = handler.awaitOneExecutable { db ->
                            db.novel_categoriesQueries.insert(
                                name = backupCat.name,
                                order = order,
                                flags = backupCat.flags,
                                hidden = if (backupCat.hidden) 1L else 0L,
                                hidden_from_home_hub = 0L,
                                parentId = mappedParentId,
                            )
                            db.novel_categoriesQueries.selectLastInsertedRowId()
                        }
                        idMap[backupCat.id] = newId
                    }
                }
        }
    }
}
