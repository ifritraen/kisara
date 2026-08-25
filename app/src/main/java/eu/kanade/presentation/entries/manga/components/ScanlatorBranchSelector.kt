package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorBranchSelector(
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showAllOption: Boolean = true,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showAllOption) {
            val isAllSelected = selectedScanlator == null
            FilterChip(
                selected = isAllSelected,
                onClick = { onScanlatorSelected(null) },
                label = {
                    Text(text = stringResource(MR.strings.all))
                },
                shape = RoundedCornerShape(100.dp),
            )
        }

        scanlatorChapterCounts.forEach { (scanlator, count) ->
            val isSelected = selectedScanlator == scanlator
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onScanlatorSelected(null)
                    } else {
                        onScanlatorSelected(scanlator)
                    }
                },
                label = {
                    Text(text = "$scanlator ($count)")
                },
                shape = RoundedCornerShape(100.dp),
            )
        }
    }
}
