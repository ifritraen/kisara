package eu.kanade.tachiyomi.ui.mini

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// KMK -->
class MiniHistoryScreenModel(val sourceId: Long) : ScreenModel {
    // TODO: wire to mini_history SQLDelight queries once build runs
    data class State(
        val items: List<MiniHistoryItem> = emptyList(),
        val isLoading: Boolean = false,
    )
    data class MiniHistoryItem(
        val chapterId: Long,
        val mangaId: Long,
        val mangaTitle: String,
        val chapterName: String,
        val lastRead: Long,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
}

@Composable
fun Screen.MiniHistoryTab(sourceId: Long, navigator: Navigator) {
    val screenModel = rememberScreenModel(tag = "mini_hist_$sourceId") { MiniHistoryScreenModel(sourceId) }
    val state by screenModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.items.isEmpty()) {
            Text(
                text = "No reading history yet.",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        } else {
            LazyColumn {
                items(state.items, key = { it.chapterId }) { item ->
                    ListItem(
                        headlineContent = { Text(item.mangaTitle) },
                        supportingContent = { Text(item.chapterName) },
                    )
                }
            }
        }
    }
}
// KMK <--
