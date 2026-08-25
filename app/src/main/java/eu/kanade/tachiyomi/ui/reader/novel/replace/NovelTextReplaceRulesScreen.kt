package eu.kanade.tachiyomi.ui.reader.novel.replace

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

class NovelTextReplaceRulesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        NovelTextReplaceRulesScreenContent(onBack = { navigator.pop() })
    }
}

@Composable
private fun NovelTextReplaceRulesScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Injekt.get<NovelReaderPreferences>() }
    var rules by remember { mutableStateOf(prefs.replaceRules()) }
    var editorRule by remember { mutableStateOf<ReplaceRule?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showTest by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun commit(next: List<ReplaceRule>) {
        rules = next
        prefs.setReplaceRules(next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(KMR.strings.novel_reader_text_replace)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editorRule = ReplaceRule(
                                id = (rules.maxOfOrNull { it.id } ?: 0L) + 1,
                                order = rules.size,
                            )
                            editorIsNew = true
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(KMR.strings.novel_reader_text_replace_add),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.novel_reader_text_replace_import)) },
                                onClick = {
                                    menuExpanded = false
                                    showImport = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.novel_reader_text_replace_export)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText(null, prefs.exportReplaceRules())),
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        context.contextStringResource(
                                            KMR.strings.novel_reader_text_replace_export_copied,
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.novel_reader_text_replace_test)) },
                                onClick = {
                                    menuExpanded = false
                                    showTest = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(KMR.strings.novel_reader_text_replace_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    editorRule = rule
                                    editorIsNew = false
                                }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name.ifBlank { rule.pattern },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "\"${rule.pattern}\" → \"${rule.replacement}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { enabled ->
                                        commit(
                                            rules.toMutableList().also {
                                                it[index] = rule.copy(isEnabled = enabled)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        if (index < rules.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            editorRule?.let { rule ->
                RuleEditorDialog(
                    initial = rule,
                    isNew = editorIsNew,
                    onDismiss = { editorRule = null },
                    onSave = { updated ->
                        val next = if (editorIsNew) {
                            rules + updated
                        } else {
                            rules.map { if (it.id == rule.id) updated else it }
                        }
                        commit(next)
                        editorRule = null
                    },
                    onDelete = {
                        commit(rules.filterNot { it.id == rule.id })
                        editorRule = null
                    },
                )
            }

            if (showImport) {
                ImportRulesDialog(
                    onDismiss = { showImport = false },
                    onImport = { raw ->
                        prefs.importReplaceRules(raw)
                            .onSuccess { imported ->
                                commit(imported)
                                Toast.makeText(
                                    context,
                                    context.contextStringResource(
                                        KMR.strings.novel_reader_text_replace_import_done,
                                        imported.size,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    context.contextStringResource(KMR.strings.novel_reader_text_replace_import_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        showImport = false
                    },
                )
            }

            if (showTest) {
                TestRulesDialog(rules = rules, onDismiss = { showTest = false })
            }
        }
    }
}

@Composable
private fun RulesGlassDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = cardShape,
                )
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: ReplaceRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReplaceRule) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var pattern by remember(initial) { mutableStateOf(initial.pattern) }
    var replacement by remember(initial) { mutableStateOf(initial.replacement) }
    var isRegex by remember(initial) { mutableStateOf(initial.isRegex) }
    var isEnabled by remember(initial) { mutableStateOf(initial.isEnabled) }
    var scopeTitle by remember(initial) { mutableStateOf(initial.scopeTitle) }
    var scopeContent by remember(initial) { mutableStateOf(initial.scopeContent) }

    val patternValid = pattern.isNotBlank() && (!isRegex || runCatching { Regex(pattern) }.isSuccess)

    RulesGlassDialog(
        title = stringResource(
            if (isNew) {
                KMR.strings.novel_reader_text_replace_add
            } else {
                KMR.strings.novel_reader_text_replace_edit
            },
        ),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RulesTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = KMR.strings.novel_reader_text_replace_name,
                )
                RulesTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = KMR.strings.novel_reader_text_replace_pattern,
                    placeholder = KMR.strings.novel_reader_text_replace_pattern_hint,
                    isError = !patternValid,
                    supportingText = if (!patternValid) {
                        KMR.strings.novel_reader_text_replace_invalid_pattern
                    } else {
                        null
                    },
                )
                RulesTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = KMR.strings.novel_reader_text_replace_replacement,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(KMR.strings.novel_reader_text_replace_regex),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_scope),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScopeOption(
                    label = KMR.strings.novel_reader_text_replace_scope_content,
                    selected = scopeContent && !scopeTitle,
                    onClick = {
                        scopeContent = true
                        scopeTitle = false
                    },
                )
                ScopeOption(
                    label = KMR.strings.novel_reader_text_replace_scope_title,
                    selected = scopeTitle && !scopeContent,
                    onClick = {
                        scopeTitle = true
                        scopeContent = false
                    },
                )
                ScopeOption(
                    label = KMR.strings.novel_reader_text_replace_scope_both,
                    selected = scopeTitle && scopeContent,
                    onClick = {
                        scopeTitle = true
                        scopeContent = true
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(KMR.strings.novel_reader_text_replace_enabled),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        buttons = {
            if (!isNew) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(KMR.strings.novel_reader_text_replace_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = patternValid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            pattern = pattern,
                            replacement = replacement,
                            isRegex = isRegex,
                            isEnabled = isEnabled,
                            scopeTitle = scopeTitle,
                            scopeContent = scopeContent,
                        ),
                    )
                },
            ) {
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_save),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun ScopeOption(
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(label),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ImportRulesDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    RulesGlassDialog(
        title = stringResource(KMR.strings.novel_reader_text_replace_import),
        onDismiss = onDismiss,
        content = {
            RulesTextField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = KMR.strings.novel_reader_text_replace_import_prompt,
                minLines = 6,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = raw.isNotBlank(),
                onClick = { onImport(raw) },
            ) {
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_import),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun TestRulesDialog(
    rules: List<ReplaceRule>,
    onDismiss: () -> Unit,
) {
    var sample by remember {
        mutableStateOf(
            "Глава 12. Рейнар встал и посмотрел на Лайлу. " +
                "本章未完，请点击下一页继续阅读",
        )
    }
    var result by remember(sample, rules) { mutableStateOf(sample) }
    LaunchedEffect(sample, rules) {
        result = withContext(Dispatchers.Default) {
            applyReplaceRulesToText(sample, rules.filter { it.isEnabled && it.isValid() })
        }
    }
    RulesGlassDialog(
        title = stringResource(KMR.strings.novel_reader_text_replace_test),
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RulesTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = KMR.strings.novel_reader_text_replace_test_sample,
                    minLines = 4,
                )
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_test_result),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(KMR.strings.novel_reader_text_replace_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun RulesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource? = null,
    placeholder: StringResource? = null,
    isError: Boolean = false,
    supportingText: StringResource? = null,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(stringResource(it)) } },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(stringResource(it)) } },
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
