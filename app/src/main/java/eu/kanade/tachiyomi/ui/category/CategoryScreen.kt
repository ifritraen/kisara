package eu.kanade.tachiyomi.ui.category

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.anime.animeCategoryTab
import eu.kanade.tachiyomi.ui.category.manga.mangaCategoryTab
import eu.kanade.tachiyomi.ui.category.novel.novelCategoryTab
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR

class CategoryScreen(private val initialTab: Int = 0) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tabs = persistentListOf(
            mangaCategoryTab(),
            animeCategoryTab(),
            novelCategoryTab(),
        )
        val state = rememberPagerState(initialPage = initialTab) { tabs.size }

        TabbedScreen(
            titleRes = MR.strings.categories,
            tabs = tabs,
            state = state,
            navigateUp = navigator::pop,
        )
    }
}
