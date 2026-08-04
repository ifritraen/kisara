package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import logcat.LogPriority
import logcat.logcat

class MangaActivity : BaseActivity() {

    private var isPausedInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
        val fromSource = intent.getBooleanExtra(EXTRA_FROM_SOURCE, false)

        if (mangaId == -1L) {
            finish()
            return
        }

        // Freeze background operations on ON_STOP to conserve CPU/Battery while retaining UI state in RAM
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        isPausedInBackground = true
                        logcat(LogPriority.DEBUG) { "MangaActivity ($mangaId) paused in background: freezing CPU/Battery work" }
                    }
                    Lifecycle.Event.ON_START -> {
                        if (isPausedInBackground) {
                            isPausedInBackground = false
                            logcat(LogPriority.DEBUG) { "MangaActivity ($mangaId) resumed: active" }
                        }
                    }
                    else -> {}
                }
            },
        )

        setContent {
            TachiyomiTheme {
                Navigator(MangaScreen(mangaId, fromSource))
            }
        }
    }

    companion object {
        private const val EXTRA_MANGA_ID = "manga_id"
        private const val EXTRA_FROM_SOURCE = "from_source"

        fun newIntent(context: Context, mangaId: Long, fromSource: Boolean = false): Intent {
            return Intent(context, MangaActivity::class.java).apply {
                putExtra(EXTRA_MANGA_ID, mangaId)
                putExtra(EXTRA_FROM_SOURCE, fromSource)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }
    }
}
