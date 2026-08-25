package eu.kanade.presentation.browse.novel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.components.cards.KisaraNormalCard
import eu.kanade.presentation.components.cards.NormalCardStyle
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.tachiyomi.source.novel.NovelPluginImageWarmupEffect
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.NovelSearchScreenModel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalNovelSearchCardRow(
    titles: List<Novel>,
    getNovel: @Composable (Novel) -> State<Novel>,
    onClick: (Novel) -> Unit,
    onLongClick: (Novel) -> Unit,
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val warmupTargets by remember(titles) {
        derivedStateOf {
            titles
                .asSequence()
                .map { it.thumbnailUrl }
                .take(GLOBAL_NOVEL_WARMUP_WINDOW)
                .toList()
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    LazyRow(
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(titles) {
            val title by getNovel(it)
            NovelItem(
                title = title.title,
                cover = title.asNovelCover(),
                isFavorite = title.favorite,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
            )
        }
    }
}

@Composable
fun GlobalNovelSearchLibraryCardRow(
    items: List<NovelSearchScreenModel.LibrarySearchResult>,
    onClick: (Novel) -> Unit,
    onLongClick: (Novel) -> Unit,
) {
    if (items.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(
            items = items,
            key = { "novel-lib-search-${it.novel.id}" },
        ) { item ->
            val novel = item.novel

            Column(
                modifier = Modifier.width(108.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NovelItem(
                    title = novel.title,
                    cover = novel.asNovelCover(),
                    isFavorite = true,
                    onClick = { onClick(novel) },
                    onLongClick = { onLongClick(novel) },
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.categoryNames,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NovelItem(
    title: String,
    cover: NovelCover,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = Modifier.width(108.dp)) {
        NovelPluginImageWarmupEffect(cover.url, cover.lastModified)
        KisaraNormalCard(
            style = NormalCardStyle.DEFAULT,
            title = title,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

private const val GLOBAL_NOVEL_WARMUP_WINDOW = 12

@Composable
private fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
    )
}
