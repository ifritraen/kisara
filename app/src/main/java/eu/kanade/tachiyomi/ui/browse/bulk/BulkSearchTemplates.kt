package eu.kanade.tachiyomi.ui.browse.bulk

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
data class BulkSearchTemplate(
    val name: String,
    val sourceIds: List<Long>,
)

object BulkSearchTemplates {
    private const val PREFS_NAME = "bulk_search_templates"
    private const val KEY_TEMPLATES = "templates_list"

    private val json: Json by lazy { Injekt.get() }

    fun getTemplates(context: Context): List<BulkSearchTemplate> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = prefs.getString(KEY_TEMPLATES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<BulkSearchTemplate>>(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTemplate(context: Context, template: BulkSearchTemplate) {
        val templates = getTemplates(context).toMutableList()
        templates.removeAll { it.name.lowercase() == template.name.lowercase() }
        templates.add(0, template) // Add to the beginning/top
        saveList(context, templates)
    }

    fun deleteTemplate(context: Context, name: String) {
        val templates = getTemplates(context).toMutableList()
        templates.removeAll { it.name.lowercase() == name.lowercase() }
        saveList(context, templates)
    }

    fun renameTemplate(context: Context, oldName: String, newName: String) {
        val templates = getTemplates(context).toMutableList()
        val index = templates.indexOfFirst { it.name.lowercase() == oldName.lowercase() }
        if (index >= 0) {
            val template = templates[index]
            // If new name already exists, remove it first to avoid duplicates
            templates.removeAll { it.name.lowercase() == newName.lowercase() }
            templates[index] = template.copy(name = newName)
            saveList(context, templates)
        }
    }

    private fun saveList(context: Context, list: List<BulkSearchTemplate>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = json.encodeToString(list)
        prefs.edit {
            putString(KEY_TEMPLATES, serialized)
        }
    }
}
