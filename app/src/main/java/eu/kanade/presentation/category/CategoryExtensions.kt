package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

val Category.visualName: String
    @Composable
    get() = when {
        isLocalCategory -> stringResource(MR.strings.label_local)
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isLocalCategory -> context.stringResource(MR.strings.label_local)
        isSystemCategory -> context.stringResource(MR.strings.label_default)
        else -> name
    }
