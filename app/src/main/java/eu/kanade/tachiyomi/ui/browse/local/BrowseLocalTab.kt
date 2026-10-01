package eu.kanade.tachiyomi.ui.browse.local

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.library.components.LibraryLocalContent
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR

@Composable
fun Screen.browseLocalTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    return TabContent(
        titleRes = MR.strings.label_local,
        content = { contentPadding, _ ->
            LibraryLocalContent(
                contentPadding = contentPadding,
                onClickManga = { mangaId -> navigator.push(MangaScreen(mangaId)) },
            )
        },
    )
}
