package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesBackupCreator(
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) {

    operator fun invoke(animes: List<BackupAnime>): List<BackupSource> {
        return animes
            .asSequence()
            .map(BackupAnime::source)
            .distinct()
            .map(sourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun AnimeSource.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
