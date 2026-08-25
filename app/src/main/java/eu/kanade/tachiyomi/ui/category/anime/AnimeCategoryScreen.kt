package eu.kanade.tachiyomi.ui.category.anime

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.CategoryScreen

class AnimeCategoryScreen : Screen() {

    @Composable
    override fun Content() {
        CategoryScreen(1).Content()
    }
}
