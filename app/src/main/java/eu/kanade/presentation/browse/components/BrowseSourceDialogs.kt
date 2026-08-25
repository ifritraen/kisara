package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.KisaraBottomSheet
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RemoveMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    mangaToRemove: Manga,
) {
    val colorScheme = MaterialTheme.colorScheme

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(MR.strings.are_you_sure),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_remove))
                }
            }
        },
    ) {
        Text(
            text = stringResource(MR.strings.remove_manga, mangaToRemove.title),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
fun SavedSearchDeleteDialog(
    onDismissRequest: () -> Unit,
    name: String,
    deleteSavedSearch: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(SYMR.strings.save_search_delete),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                    onClick = {
                        deleteSavedSearch()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    ) {
        Text(
            text = stringResource(SYMR.strings.save_search_delete_message, name),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
fun SavedSearchCreateDialog(
    onDismissRequest: () -> Unit,
    currentSavedSearches: ImmutableList<String>,
    saveSearch: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val context = LocalContext.current

    KisaraBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(SYMR.strings.save_search),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onDismissRequest,
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        val searchName = textFieldValue.text.trim()
                        if (searchName.isNotBlank() && searchName !in currentSavedSearches) {
                            saveSearch(searchName)
                            onDismissRequest()
                        } else {
                            context.toast(SYMR.strings.save_search_invalid_name)
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(text = stringResource(SYMR.strings.save_search_hint))
                },
            )
        }
    }
}
