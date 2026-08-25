package eu.kanade.tachiyomi.ui.category.novel

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.CategoryScreen

class NovelCategoryScreen : Screen() {

    @Composable
    override fun Content() {
        CategoryScreen(2).Content()
    }
}
