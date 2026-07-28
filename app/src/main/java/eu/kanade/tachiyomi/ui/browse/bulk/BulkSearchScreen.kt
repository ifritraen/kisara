package eu.kanade.tachiyomi.ui.browse.bulk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.duplicate.DuplicateMangaScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BulkSearchScreen(
    val sourceIds: List<Long>,
    val queries: List<String>,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            BulkSearchScreenModel(sourceIds = sourceIds, queries = queries)
        }
        val state by screenModel.state.collectAsState()

        // Selection mode state
        val selectedMangas = remember { mutableStateListOf<Manga>() }
        var showCategoryDialog by remember { mutableStateOf(false) }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val defaultCategoryId = remember { libraryPreferences.defaultCategory().get().toLong() }
        var mangaToAddToLibrary by remember { mutableStateOf<Manga?>(null) }
        var queryToEdit by remember { mutableStateOf<String?>(null) }
        var editQueryText by remember { mutableStateOf("") }
        var isMultiSelectActiveMode by remember { mutableStateOf(false) }
        var mangaCategoryIdsMap by remember { mutableStateOf(emptyMap<Long, List<Long>>()) }
        val scope = rememberCoroutineScope()
        val allVisibleMangas = remember(state.queryResults) {
            state.queryResults.flatMap { it.results.map { pair -> pair.first } }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                val isSelectionMode = selectedMangas.isNotEmpty() || isMultiSelectActiveMode
                AppBar(
                    title = "Bulk Search Results",
                    navigateUp = navigator::pop,
                    actionModeCounter = selectedMangas.size,
                    onCancelActionMode = {
                        selectedMangas.clear()
                        isMultiSelectActiveMode = false
                    },
                    actionModeActions = {
                        val areAllSelected = allVisibleMangas.isNotEmpty() && selectedMangas.size >= allVisibleMangas.size
                        IconButton(
                            onClick = {
                                if (areAllSelected) {
                                    selectedMangas.clear()
                                } else {
                                    selectedMangas.clear()
                                    selectedMangas.addAll(allVisibleMangas)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (areAllSelected) Icons.Filled.SelectAll else Icons.Outlined.SelectAll,
                                contentDescription = "Select All",
                            )
                        }
                        IconButton(onClick = { showCategoryDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.LibraryAdd,
                                contentDescription = "Add selected to library",
                            )
                        }
                    },
                    actions = {
                        // Standard top bar actions
                        IconButton(
                            onClick = {
                                isMultiSelectActiveMode = !isMultiSelectActiveMode
                                if (!isMultiSelectActiveMode) {
                                    selectedMangas.clear()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = "Multi-select",
                                tint = if (isMultiSelectActiveMode) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.queryResults) { qr ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            queryToEdit = qr.query
                                            editQueryText = qr.query
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = qr.query,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (qr.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        queryToEdit = qr.query
                                        editQueryText = qr.query
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit Search Query",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (qr.isFailed && !qr.isLoading) {
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val resultsBySource = remember(qr.results) {
                                    qr.results.groupBy { it.second }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    resultsBySource.forEach { (source, resultsList) ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = source.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(bottom = 4.dp),
                                            )
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(end = 16.dp),
                                            ) {
                                                items(resultsList) { (manga, source) ->
                                                    val isSelected = selectedMangas.any { it.id == manga.id }
                                                    val isInLibrary = state.favoriteUrls.contains(manga.url) || manga.favorite

                                                    ResultMangaCard(
                                                        manga = manga,
                                                        source = source,
                                                        isInLibrary = isInLibrary,
                                                        isSelected = isSelected,
                                                        isMultiSelectActive = isMultiSelectActiveMode || selectedMangas.isNotEmpty(),
                                                        onClick = {
                                                            if (isMultiSelectActiveMode || selectedMangas.isNotEmpty()) {
                                                                val idx = selectedMangas.indexOfFirst { it.id == manga.id }
                                                                if (idx != -1) {
                                                                    selectedMangas.removeAt(idx)
                                                                } else {
                                                                    selectedMangas.add(manga)
                                                                }
                                                            } else {
                                                                navigator.push(MangaScreen(manga.id, true))
                                                            }
                                                        },
                                                        onLongClick = {
                                                            scope.launch {
                                                                val categoryIds = screenModel.getMangaCategoryIds(manga.id)
                                                                mangaCategoryIdsMap = mangaCategoryIdsMap + (manga.id to categoryIds)
                                                                mangaToAddToLibrary = manga
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Selection Dialog for adding to library (Bulk Mode)
                if (showCategoryDialog) {
                    ChangeCategoryDialog(
                        initialSelection = remember(state.categories) {
                            state.categories.map { category ->
                                CheckboxState.State.None(category)
                            }.toImmutableList()
                        },
                        onDismissRequest = { showCategoryDialog = false },
                        onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.CategoryScreen()) },
                        onConfirm = { addedIds, _ ->
                            showCategoryDialog = false
                            screenModel.addMangasToLibrary(selectedMangas.toList(), addedIds)
                            selectedMangas.clear()
                        },
                        onDuplicateCheck = {
                            showCategoryDialog = false
                            val selection = selectedMangas.map { it.id }
                            selectedMangas.clear()
                            navigator.push(DuplicateMangaScreen(selection))
                        },
                    )
                }

                // Category Selection Dialog for adding to library (Single Mode)
                if (mangaToAddToLibrary != null) {
                    val targetManga = mangaToAddToLibrary!!
                    val preselectedIds = mangaCategoryIdsMap[targetManga.id] ?: emptyList()
                    ChangeCategoryDialog(
                        initialSelection = remember(state.categories, preselectedIds) {
                            state.categories.map { category ->
                                if (preselectedIds.contains(category.id)) {
                                    CheckboxState.State.Checked(category)
                                } else {
                                    CheckboxState.State.None(category)
                                }
                            }.toImmutableList()
                        },
                        onDismissRequest = { mangaToAddToLibrary = null },
                        onEditCategories = { navigator.push(eu.kanade.tachiyomi.ui.category.CategoryScreen()) },
                        onConfirm = { addedIds, _ ->
                            screenModel.toggleFavorite(targetManga, addedIds)
                            mangaToAddToLibrary = null
                        },
                        onDuplicateCheck = {
                            mangaToAddToLibrary = null
                            navigator.push(DuplicateMangaScreen(targetManga.id))
                        },
                    )
                }

                // Edit Query Dialog
                if (queryToEdit != null) {
                    val oldQuery = queryToEdit!!
                    AlertDialog(
                        onDismissRequest = { queryToEdit = null },
                        title = { Text("Edit Search Query") },
                        text = {
                            Column {
                                Text("Edit search query details:")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editQueryText,
                                    onValueChange = { editQueryText = it },
                                    label = { Text("Search Query") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (editQueryText.isNotBlank()) {
                                        screenModel.editQuery(oldQuery, editQueryText.trim())
                                        queryToEdit = null
                                    }
                                },
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { queryToEdit = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultMangaCard(
    manga: Manga,
    source: Source,
    isInLibrary: Boolean,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleaned = remember(manga.title) { cleanMangaTitle(manga.title) }
    val displayLanguage = cleaned.language ?: source.lang
    val subtitleText = if (cleaned.sequel != null) {
        "${displayLanguage.uppercase()} (Pt ${cleaned.sequel})"
    } else {
        displayLanguage.uppercase()
    }

    Column(
        modifier = modifier
            .width(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MangaCover.Book(
                data = manga.asMangaCover(),
                modifier = Modifier.fillMaxSize(),
            )

            // Selection indicator/overlay
            if (isMultiSelectActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Not Selected",
                                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            // In library badge
            if (isInLibrary) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    InLibraryBadge(enabled = true)
                }
            }

            // Author & Artist overlay
            if (!cleaned.author.isNullOrBlank() && !cleaned.artist.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${cleaned.author} / ${cleaned.artist}",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        var displayTitle by remember(cleaned.title) { mutableStateOf(cleaned.title) }
        Text(
            text = displayTitle,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) {
                    val numberMatch = Regex("(\\d+)$").find(cleaned.title)
                    if (numberMatch != null) {
                        val numbers = numberMatch.groupValues[1]
                        val lastLineIndex = (textLayoutResult.lineCount - 1).coerceAtMost(1)
                        if (lastLineIndex >= 0) {
                            val lastLineEndIndex = textLayoutResult.getLineEnd(lastLineIndex)
                            val safeCut = (lastLineEndIndex - numbers.length - 3).coerceAtLeast(0)
                            val newTitle = cleaned.title.substring(0, safeCut) + "…" + numbers
                            if (newTitle != displayTitle) {
                                displayTitle = newTitle
                            }
                        }
                    }
                }
            },
        )
        Text(
            text = source.name,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitleText,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun cleanMangaTitle(title: String): CleanedTitle {
    val normalized = title.replace("“", "\"").replace("”", "\"").replace("‘", "'").replace("’", "'").trim()

    var sequel: String? = null
    var workingTitle = normalized

    // 1. Detect leading sequel/volume in parentheses: (pq)
    val startingParenRegex = Regex("""^\(([^()]+)\)\s*(.*)$""")
    val startingMatch = startingParenRegex.find(workingTitle)
    if (startingMatch != null) {
        val inside = startingMatch.groupValues[1].trim()
        val rest = startingMatch.groupValues[2].trim()
        if (rest.startsWith("[") || rest.startsWith("(") || rest.isNotEmpty()) {
            sequel = inside
            workingTitle = rest
        }
    }

    // 2. Detect author / artist in leading brackets e.g. [abc (de)] or [abc]
    var author: String? = null
    var artist: String? = null
    val authorArtistRegex = Regex("""^[\[({]([^\[\]()]+)\s*(?:\(([^()]+)\))?[\])}]""")
    val authorMatch = authorArtistRegex.find(workingTitle)
    if (authorMatch != null) {
        val part1 = authorMatch.groupValues[1].trim()
        val part2 = authorMatch.groupValues.getOrNull(2)?.trim()
        if (!part2.isNullOrEmpty()) {
            author = part1
            artist = part2
        } else {
            author = part1
        }
        workingTitle = workingTitle.substring(authorMatch.range.last + 1).trim()
    }

    // 3. Find language and additional sequel info in remaining brackets
    val languagesList = listOf(
        "english", "en", "spanish", "es", "korean", "kr", "japanese", "jp", "raw", "chinese", "zh", "french", "fr", "german", "de", "italian", "it", "russian", "ru", "vietnamese", "vi", "portuguese", "pt",
    )

    var language: String? = null
    val simpleBracketRegex = Regex("""[\[({]([^\])}]+)[\])}]""")
    for (match in simpleBracketRegex.findAll(workingTitle)) {
        val bracketText = match.groupValues[1].trim()
        val lower = bracketText.lowercase()
        if (lower.toIntOrNull() != null || lower.startsWith("vol") || lower.startsWith("ch") || lower.startsWith("part")) {
            if (sequel == null) {
                sequel = bracketText
            }
        } else if (languagesList.contains(lower)) {
            language = bracketText
        }
    }

    // 4. Repeatedly strip all remaining brackets from workingTitle
    var cleanedTitle = workingTitle
    while (cleanedTitle.contains("(") || cleanedTitle.contains("[") || cleanedTitle.contains("{")) {
        val old = cleanedTitle
        cleanedTitle = cleanedTitle
            .replace(Regex("""\([^()]*\)"""), "")
            .replace(Regex("""\[[^\[\]]*\]"""), "")
            .replace(Regex("""\{[^{}]*\}"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleanedTitle == old) break
    }

    // Strip trailing dashes/slashes
    cleanedTitle = cleanedTitle.replace(Regex("""\s+[-|/~]\s*$"""), "").trim()

    if (cleanedTitle.isEmpty()) {
        cleanedTitle = title
    }

    return CleanedTitle(
        title = cleanedTitle,
        language = language,
        sequel = sequel,
        author = author,
        artist = artist,
    )
}

data class CleanedTitle(
    val title: String,
    val language: String?,
    val sequel: String?,
    val author: String? = null,
    val artist: String? = null,
)
