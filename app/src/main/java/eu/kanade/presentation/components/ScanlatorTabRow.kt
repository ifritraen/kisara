package eu.kanade.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorTabRow(
    scanlators: List<String>,
    selectedScanlator: String?,
    onSelectScanlator: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = stringResource(MR.strings.all),
) {
    if (scanlators.size <= 1) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedScanlator == null,
            onClick = { onSelectScanlator(null) },
            label = {
                Text(
                    text = allLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            modifier = Modifier.height(28.dp),
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = null,
        )
        scanlators.forEach { scanlator ->
            FilterChip(
                selected = selectedScanlator.equals(scanlator, ignoreCase = true),
                onClick = { onSelectScanlator(scanlator) },
                label = {
                    Text(
                        text = scanlator,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )
        }
    }
}
