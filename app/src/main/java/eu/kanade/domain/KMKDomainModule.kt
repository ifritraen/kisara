package eu.kanade.domain

import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class KMKDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<LibraryUpdateErrorWithRelationsRepository> {
            LibraryUpdateErrorWithRelationsRepositoryImpl(get())
        }
        addFactory { GetLibraryUpdateErrorWithRelations(get()) }

        addSingletonFactory<LibraryUpdateErrorMessageRepository> { LibraryUpdateErrorMessageRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrorMessages(get()) }
        addFactory { DeleteLibraryUpdateErrorMessages(get()) }
        addFactory { InsertLibraryUpdateErrorMessages(get()) }

        addSingletonFactory<LibraryUpdateErrorRepository> { LibraryUpdateErrorRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrors(get()) }
        addFactory { DeleteLibraryUpdateErrors(get()) }
        addFactory { InsertLibraryUpdateErrors(get()) }

        // Logbook
        addSingletonFactory<tachiyomi.domain.logbook.repository.LogbookRepository> { tachiyomi.data.logbook.LogbookRepositoryImpl(get()) }
        addFactory { tachiyomi.domain.logbook.interactor.GetLogbookEntries(get()) }
        addFactory { tachiyomi.domain.logbook.interactor.InsertLogbookEntry(get()) }
        addFactory { tachiyomi.domain.logbook.interactor.ClearLogbook(get()) }

        // Page Bookmark
        addSingletonFactory<tachiyomi.domain.pagebookmark.repository.PageBookmarkRepository> { tachiyomi.data.pagebookmark.PageBookmarkRepositoryImpl(get()) }
        addFactory { tachiyomi.domain.pagebookmark.interactor.GetPageBookmarks(get()) }
        addFactory { tachiyomi.domain.pagebookmark.interactor.TogglePageBookmark(get()) }
        addFactory { tachiyomi.domain.pagebookmark.interactor.DeletePageBookmark(get()) }

        // AutoTrack
        addFactory { eu.kanade.domain.track.interactor.AutoTrack(get(), get(), get()) }
        addFactory { eu.kanade.domain.track.interactor.TrackOnCategorySet(get(), get(), get(), get()) }

        // Content Filter
        addFactory { tachiyomi.domain.suggestions.interactor.FilterMangaByBlockedContent(get(), get(), get(), get()) }
    }
}
