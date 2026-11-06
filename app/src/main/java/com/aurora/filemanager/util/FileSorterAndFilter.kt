package com.aurora.filemanager.util

import com.aurora.filemanager.data.model.FileCategory
import com.aurora.filemanager.data.model.FileItem
import com.aurora.filemanager.data.model.GroupingMode
import com.aurora.filemanager.data.model.SortField
import com.aurora.filemanager.data.model.SortOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FileSectionGroup(
    val title: String,
    val items: List<FileItem>
)

/**
 * Pure, deterministic sorting, grouping, and search-matching engine.
 * Designed so unit tests can verify every advertised sorting and filtering option directly.
 */
object FileSorterAndFilter {

    /**
     * Sorts files according to the user's selected [SortField] and [SortOrder].
     * When [directoriesFirst] is true (Storage browser), folders are kept grouped above regular files.
     */
    fun sortFiles(
        items: List<FileItem>,
        sortField: SortField,
        sortOrder: SortOrder,
        directoriesFirst: Boolean
    ): List<FileItem> {
        val baseComparator = Comparator<FileItem> { a, b ->
            val rawCompare = when (sortField) {
                SortField.NAME -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                SortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortField.DATE_ADDED -> a.timestamps.primaryAddedOrDiscoveredMillis
                    .compareTo(b.timestamps.primaryAddedOrDiscoveredMillis)
                SortField.DATE_MODIFIED -> a.timestamps.lastModifiedMillis
                    .compareTo(b.timestamps.lastModifiedMillis)
                SortField.TYPE -> {
                    val catCmp = a.category.displayName.compareTo(b.category.displayName)
                    if (catCmp != 0) catCmp
                    else a.extension.lowercase(Locale.ROOT).compareTo(b.extension.lowercase(Locale.ROOT))
                }
            }
            val ordered = if (sortOrder == SortOrder.ASCENDING) rawCompare else -rawCompare
            if (ordered != 0) ordered else a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
        }

        return items.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) {
                if (a.isPinned) -1 else 1
            } else if (directoriesFirst && a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                baseComparator.compare(a, b)
            }
        }
    }

    /**
     * Groups already-sorted files into sections by Date or Category when requested.
     */
    fun groupFiles(
        sortedItems: List<FileItem>,
        groupingMode: GroupingMode,
        sortField: SortField,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FileSectionGroup> {
        if (groupingMode == GroupingMode.NONE || sortedItems.isEmpty()) {
            return listOf(FileSectionGroup(title = "", items = sortedItems))
        }

        return when (groupingMode) {
            GroupingMode.BY_CATEGORY -> {
                sortedItems
                    .groupBy { it.category.displayName }
                    .map { (title, list) -> FileSectionGroup(title = title, items = list) }
            }
            GroupingMode.BY_DATE -> {
                val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val startOfToday = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val startOfYesterday = startOfToday - 86_400_000L
                val startOfWeek = startOfToday - 7 * 86_400_000L
                val startOfMonth = startOfToday - 30 * 86_400_000L
                val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

                val grouped = sortedItems.groupBy { item ->
                    val ts = if (sortField == SortField.DATE_MODIFIED) {
                        item.timestamps.lastModifiedMillis
                    } else {
                        item.timestamps.primaryAddedOrDiscoveredMillis
                    }
                    when {
                        ts >= startOfToday -> "Today"
                        ts >= startOfYesterday -> "Yesterday"
                        ts >= startOfWeek -> "Last 7 Days"
                        ts >= startOfMonth -> "Last 30 Days"
                        ts > 0L -> monthFormatter.format(Date(ts))
                        else -> "Earlier"
                    }
                }
                
                // Ensure logical ordering of time-based groups regardless of pinned items
                val predefinedOrder = listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days")
                val sortedGroups = grouped.entries.sortedWith { a, b ->
                    val idxA = predefinedOrder.indexOf(a.key)
                    val idxB = predefinedOrder.indexOf(b.key)
                    if (idxA != -1 && idxB != -1) idxA.compareTo(idxB)
                    else if (idxA != -1) -1
                    else if (idxB != -1) 1
                    else if (a.key == "Earlier") 1
                    else if (b.key == "Earlier") -1
                    else b.key.compareTo(a.key) // Reverse alphabetical for Month Year
                }
                
                sortedGroups.map { (title, list) -> FileSectionGroup(title = title, items = list) }
            }
            GroupingMode.NONE -> listOf(FileSectionGroup(title = "", items = sortedItems))
        }
    }

    /**
     * Evaluates search query and category/large-file filters with full Unicode and case-insensitive support.
     */
    fun filterFiles(
        items: List<FileItem>,
        query: String,
        selectedCategories: Set<FileCategory>,
        largeFilesOnly: Boolean,
        largeFileThresholdBytes: Long = 50L * 1024L * 1024L // 50 MB default
    ): List<FileItem> {
        val trimmedQuery = query.trim().lowercase(Locale.ROOT)
        return items.filter { item ->
            val matchesCategory = selectedCategories.isEmpty() || item.category in selectedCategories
            val matchesLarge = !largeFilesOnly || (!item.isDirectory && item.sizeBytes >= largeFileThresholdBytes)
            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                item.name.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                    item.extension.lowercase(Locale.ROOT) == trimmedQuery.removePrefix(".") ||
                    item.category.displayName.lowercase(Locale.ROOT).contains(trimmedQuery) ||
                    (item.artist?.lowercase(Locale.ROOT)?.contains(trimmedQuery) == true) ||
                    (item.album?.lowercase(Locale.ROOT)?.contains(trimmedQuery) == true)
            }
            matchesCategory && matchesLarge && matchesQuery
        }
    }

    /**
     * Generates a safe, non-colliding filename in [existingNames] when ConflictStrategy.KEEP_BOTH_RENAME is chosen.
     */
    fun resolveUniqueFileName(originalName: String, isDirectory: Boolean, existingNames: Set<String>): String {
        val lowerExisting = existingNames.map { it.lowercase(Locale.ROOT) }.toSet()
        if (originalName.lowercase(Locale.ROOT) !in lowerExisting) {
            return originalName
        }

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (!isDirectory && dotIndex > 0) {
            originalName.substring(0, dotIndex)
        } else {
            originalName
        }
        val extSuffix = if (!isDirectory && dotIndex > 0) {
            originalName.substring(dotIndex)
        } else {
            ""
        }

        var counter = 1
        while (true) {
            val candidate = "$baseName ($counter)$extSuffix"
            if (candidate.lowercase(Locale.ROOT) !in lowerExisting) {
                return candidate
            }
            counter++
        }
    }
}
