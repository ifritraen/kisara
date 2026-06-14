package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            // KMK -->
            val idMap = mutableMapOf<Long, Long>()
            dbCategories.forEach { dbCat ->
                val backupCat = backupCategories.find { it.name == dbCat.name }
                if (backupCat != null) {
                    idMap[backupCat.id] = dbCat.id
                }
            }

            val categories = backupCategories
                .sortedWith(
                    compareBy<BackupCategory> { it.parentId != null }
                        .thenBy { it.order },
                )
                .map { backupCat ->
                    val dbCategory = dbCategoriesByName[backupCat.name]
                    val order = dbCategory?.order ?: nextOrder++
                    val mappedParentId = backupCat.parentId?.let { oldParentId ->
                        idMap[oldParentId]
                    }

                    val id = if (dbCategory != null) {
                        idMap[backupCat.id] = dbCategory.id
                        if (mappedParentId != null && dbCategory.parentId != mappedParentId) {
                            handler.await {
                                categoriesQueries.update(
                                    categoryId = dbCategory.id,
                                    name = dbCategory.name,
                                    order = dbCategory.order,
                                    flags = dbCategory.flags,
                                    parentId = mappedParentId,
                                    hidden = if (dbCategory.hidden) 1L else 0L,
                                )
                            }
                        }
                        dbCategory.id
                    } else {
                        val newId = handler.awaitOneExecutable {
                            categoriesQueries.insert(
                                backupCat.name,
                                order,
                                backupCat.flags,
                                parentId = mappedParentId,
                                hidden = if (backupCat.hidden) 1L else 0L,
                            )
                            categoriesQueries.selectLastInsertedRowId()
                        }
                        idMap[backupCat.id] = newId
                        newId
                    }
                    backupCat.toCategory(id).copy(order = order, parentId = mappedParentId)
                }
            // KMK <--

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
