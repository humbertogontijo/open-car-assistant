package cc.opencar.assistant.feature.web

import android.content.SharedPreferences

/** Persists which entity cards the user has hidden from the main grids. */
class EntityVisibilityStore(private val prefs: SharedPreferences) {
    fun hiddenIds(): Set<String> =
        prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun isHidden(id: String): Boolean = id in hiddenIds()

    fun hide(id: String) {
        if (id.isBlank()) return
        val next = hiddenIds().toMutableSet()
        if (!next.add(id)) return
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun unhide(id: String) {
        val next = hiddenIds().toMutableSet()
        if (!next.remove(id)) return
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun setHidden(id: String, hidden: Boolean) {
        if (hidden) hide(id) else unhide(id)
    }

    companion object {
        private const val KEY = "hidden_entities"
    }
}
