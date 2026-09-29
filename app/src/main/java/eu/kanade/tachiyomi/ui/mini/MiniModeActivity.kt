package eu.kanade.tachiyomi.ui.mini

import android.os.Bundle
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.domain.mini.service.MiniModePreferences
import eu.kanade.tachiyomi.util.view.setComposeContent
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
abstract class MiniModeActivity : BaseActivity() {

    open val slotIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        // Flag MUST be set before super.onCreate() so App.onStart() can see it
        isMiniModeActive = true
        super.onCreate(savedInstanceState)

        val sourceId = intent.getLongExtra("source_id", -1L)
            .takeIf { it != -1L }
            ?: (if (slotIndex in 1..10) Injekt.get<MiniModePreferences>().getMiniSlotSource(slotIndex) else -1L)

        if (sourceId == -1L) {
            finish()
            return
        }

        setComposeContent {
            Navigator(screen = MiniModeHomeScreen(sourceId = sourceId, slotIndex = slotIndex))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Reset when the last mini activity exits
        isMiniModeActive = false
    }

    companion object {
        @Volatile
        var isMiniModeActive: Boolean = false
            internal set
    }
}

class MiniModeShortcutActivity : MiniModeActivity() { override val slotIndex = 0 }
class MiniModeSlot1  : MiniModeActivity() { override val slotIndex = 1  }
class MiniModeSlot2  : MiniModeActivity() { override val slotIndex = 2  }
class MiniModeSlot3  : MiniModeActivity() { override val slotIndex = 3  }
class MiniModeSlot4  : MiniModeActivity() { override val slotIndex = 4  }
class MiniModeSlot5  : MiniModeActivity() { override val slotIndex = 5  }
class MiniModeSlot6  : MiniModeActivity() { override val slotIndex = 6  }
class MiniModeSlot7  : MiniModeActivity() { override val slotIndex = 7  }
class MiniModeSlot8  : MiniModeActivity() { override val slotIndex = 8  }
class MiniModeSlot9  : MiniModeActivity() { override val slotIndex = 9  }
class MiniModeSlot10 : MiniModeActivity() { override val slotIndex = 10 }
// KMK <--
