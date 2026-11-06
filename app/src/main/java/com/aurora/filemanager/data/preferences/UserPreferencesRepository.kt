package com.aurora.filemanager.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.filemanager.data.model.GroupingMode
import com.aurora.filemanager.data.model.SortField
import com.aurora.filemanager.data.model.SortOrder
import com.aurora.filemanager.data.model.ViewMode
import com.aurora.filemanager.ui.theme.ThemeMode
import com.aurora.filemanager.ui.viewmodel.HomeSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aurora_user_prefs")

@Immutable
data class UserPreferences(
    val recentsViewMode: ViewMode = ViewMode.LIST,
    val folderViewModes: Map<String, ViewMode> = emptyMap(),
    val gridColumns: Int = 3,
    val recentsSortField: SortField = SortField.DATE_ADDED,
    val recentsSortOrder: SortOrder = SortOrder.DESCENDING,
    val storageSortField: SortField = SortField.NAME,
    val storageSortOrder: SortOrder = SortOrder.ASCENDING,
    val recentsGrouping: GroupingMode = GroupingMode.BY_DATE,
    val storageGrouping: GroupingMode = GroupingMode.NONE,
    val showFileExtensions: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val enableThumbnails: Boolean = true,
    val resumeMediaPlayback: Boolean = true,
    val backgroundAudioPlayback: Boolean = false,
    val saveSearchHistory: Boolean = true,
    val cleanerLook: Boolean = false,
    val lastSelectedSection: HomeSection = HomeSection.RECENTS
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val RECENTS_VIEW_MODE = stringPreferencesKey("recents_view_mode")
        val FOLDER_VIEW_MODES = stringPreferencesKey("folder_view_modes")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val RECENTS_SORT_FIELD = stringPreferencesKey("recents_sort_field")
        val RECENTS_SORT_ORDER = stringPreferencesKey("recents_sort_order")
        val STORAGE_SORT_FIELD = stringPreferencesKey("storage_sort_field")
        val STORAGE_SORT_ORDER = stringPreferencesKey("storage_sort_order")
        val RECENTS_GROUPING = stringPreferencesKey("recents_grouping")
        val STORAGE_GROUPING = stringPreferencesKey("storage_grouping")
        val SHOW_EXTENSIONS = booleanPreferencesKey("show_file_extensions")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ENABLE_THUMBNAILS = booleanPreferencesKey("enable_thumbnails")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_media_playback")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio_playback")
        val SAVE_SEARCH_HISTORY = booleanPreferencesKey("save_search_history")
        val CLEANER_LOOK = booleanPreferencesKey("cleaner_look")
        val LAST_SELECTED_SECTION = stringPreferencesKey("last_selected_section")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserPreferences(
                recentsViewMode = runCatching {
                    ViewMode.valueOf(prefs[Keys.RECENTS_VIEW_MODE] ?: ViewMode.LIST.name)
                }.getOrDefault(ViewMode.LIST),
                folderViewModes = run {
                    val raw = prefs[Keys.FOLDER_VIEW_MODES] ?: ""
                    val map = mutableMapOf<String, ViewMode>()
                    if (raw.isNotBlank()) {
                        raw.split(";").forEach { entry ->
                            val parts = entry.split("=")
                            if (parts.size == 2) {
                                runCatching {
                                    map[parts[0]] = ViewMode.valueOf(parts[1])
                                }
                            }
                        }
                    }
                    map
                },
                gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(2, 5),
                recentsSortField = runCatching {
                    SortField.valueOf(prefs[Keys.RECENTS_SORT_FIELD] ?: SortField.DATE_ADDED.name)
                }.getOrDefault(SortField.DATE_ADDED),
                recentsSortOrder = runCatching {
                    SortOrder.valueOf(prefs[Keys.RECENTS_SORT_ORDER] ?: SortOrder.DESCENDING.name)
                }.getOrDefault(SortOrder.DESCENDING),
                storageSortField = runCatching {
                    SortField.valueOf(prefs[Keys.STORAGE_SORT_FIELD] ?: SortField.NAME.name)
                }.getOrDefault(SortField.NAME),
                storageSortOrder = runCatching {
                    SortOrder.valueOf(prefs[Keys.STORAGE_SORT_ORDER] ?: SortOrder.ASCENDING.name)
                }.getOrDefault(SortOrder.ASCENDING),
                recentsGrouping = runCatching {
                    GroupingMode.valueOf(prefs[Keys.RECENTS_GROUPING] ?: GroupingMode.BY_DATE.name)
                }.getOrDefault(GroupingMode.BY_DATE),
                storageGrouping = runCatching {
                    GroupingMode.valueOf(prefs[Keys.STORAGE_GROUPING] ?: GroupingMode.NONE.name)
                }.getOrDefault(GroupingMode.NONE),
                showFileExtensions = prefs[Keys.SHOW_EXTENSIONS] ?: true,
                showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
                }.getOrDefault(ThemeMode.DARK),
                enableThumbnails = prefs[Keys.ENABLE_THUMBNAILS] ?: true,
                resumeMediaPlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
                backgroundAudioPlayback = prefs[Keys.BACKGROUND_AUDIO] ?: false,
                saveSearchHistory = prefs[Keys.SAVE_SEARCH_HISTORY] ?: true,
                cleanerLook = prefs[Keys.CLEANER_LOOK] ?: false,
                lastSelectedSection = runCatching {
                    HomeSection.valueOf(prefs[Keys.LAST_SELECTED_SECTION] ?: HomeSection.RECENTS.name)
                }.getOrDefault(HomeSection.RECENTS)
            )
        }

    suspend fun setViewMode(isRecents: Boolean, mode: ViewMode) {
        if (isRecents) {
            context.dataStore.edit { prefs ->
                prefs[Keys.RECENTS_VIEW_MODE] = mode.name
            }
        }
    }

    suspend fun setFolderViewMode(folderPath: String, mode: ViewMode) {
        context.dataStore.edit { prefs ->
            val currentRaw = prefs[Keys.FOLDER_VIEW_MODES] ?: ""
            val map = mutableMapOf<String, ViewMode>()
            if (currentRaw.isNotBlank()) {
                currentRaw.split(";").forEach { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2) {
                        runCatching { map[parts[0]] = ViewMode.valueOf(parts[1]) }
                    }
                }
            }
            map[folderPath] = mode
            prefs[Keys.FOLDER_VIEW_MODES] = map.entries.joinToString(";") { "${it.key}=${it.value.name}" }
        }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GRID_COLUMNS] = columns.coerceIn(2, 5)
        }
    }

    suspend fun setSort(isRecents: Boolean, field: SortField, order: SortOrder) {
        context.dataStore.edit { prefs ->
            if (isRecents) {
                prefs[Keys.RECENTS_SORT_FIELD] = field.name
                prefs[Keys.RECENTS_SORT_ORDER] = order.name
            } else {
                prefs[Keys.STORAGE_SORT_FIELD] = field.name
                prefs[Keys.STORAGE_SORT_ORDER] = order.name
            }
        }
    }

    suspend fun setGrouping(isRecents: Boolean, grouping: GroupingMode) {
        context.dataStore.edit { prefs ->
            prefs[if (isRecents) Keys.RECENTS_GROUPING else Keys.STORAGE_GROUPING] = grouping.name
        }
    }

    suspend fun setShowFileExtensions(show: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_EXTENSIONS] = show }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setEnableThumbnails(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ENABLE_THUMBNAILS] = enabled }
    }

    suspend fun setResumeMediaPlayback(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.RESUME_PLAYBACK] = enabled }
    }

    suspend fun setBackgroundAudioPlayback(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setSaveSearchHistory(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SAVE_SEARCH_HISTORY] = enabled }
    }

    suspend fun setCleanerLook(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.CLEANER_LOOK] = enabled }
    }

    suspend fun setLastSelectedSection(section: HomeSection) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_SELECTED_SECTION] = section.name }
    }
}
