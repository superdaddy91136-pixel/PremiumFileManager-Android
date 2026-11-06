package com.aurora.filemanager.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Stores user-favorited files and pinned quick-access folders.
 * Removing a favorite only deletes this row—never the underlying file.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val addedAtMillis: Long,
    val isPinnedQuickAccess: Boolean = false
)

/**
 * Persists the timestamp when the app first discovered a file on the filesystem.
 * Used when the filesystem lacks a true birth/creation timestamp or MediaStore DATE_ADDED,
 * ensuring consistent, honest chronological ordering across app launches.
 */
@Entity(tableName = "discovered_files")
data class DiscoveredFileEntity(
    @PrimaryKey val path: String,
    val firstDiscoveredAtMillis: Long,
    val lastSeenModifiedMillis: Long,
    val lastOpenedAtMillis: Long? = null,
    val cachedSha256: String? = null
)

@Entity(tableName = "pinned_items")
data class PinnedItemEntity(
    @PrimaryKey val path: String,
    val isDirectory: Boolean,
    val pinnedAtMillis: Long
)

/**
 * Tracks recently opened files separately from Recents (newly added files).
 */
@Entity(tableName = "recent_opened")
data class RecentOpenedEntity(
    @PrimaryKey val path: String,
    val name: String,
    val categoryName: String,
    val sizeBytes: Long,
    val openedAtMillis: Long,
    val lastPlaybackPositionMs: Long = 0L
)

/**
 * Stores optional local search history (can be cleared or disabled by user).
 */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAtMillis: Long
)

/**
 * Local audit log of recent file operations performed by the user.
 */
@Entity(tableName = "operation_history")
data class OperationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,      // e.g., "COPY", "MOVE", "RENAME", "DELETE", "EXTRACT", "COMPRESS"
    val summary: String,
    val targetPath: String,
    val timestampMillis: Long,
    val succeeded: Boolean
)

@Entity(tableName = "trash_files")
data class TrashEntity(
    @PrimaryKey val originalPath: String,
    val trashPath: String,
    val originalName: String,
    val originalParentPath: String,
    val fileSizeBytes: Long,
    val deletedAtMillis: Long,
    val isDirectory: Boolean
)

@Dao
interface AuroraDao {
    // Pinned Items
    @Query("SELECT * FROM pinned_items ORDER BY pinnedAtMillis DESC")
    fun observePinnedItems(): Flow<List<PinnedItemEntity>>

    @Query("SELECT path FROM pinned_items")
    suspend fun getPinnedPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPinnedItem(entity: PinnedItemEntity)

    @Query("DELETE FROM pinned_items WHERE path = :path")
    suspend fun removePinnedItem(path: String)

    @Query("DELETE FROM pinned_items WHERE path IN (:paths)")
    suspend fun removePinnedItems(paths: List<String>)

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAtMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT path FROM favorites")
    suspend fun getAllFavoritePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeFavorite(path: String)

    @Query("DELETE FROM favorites WHERE path IN (:paths)")
    suspend fun removeFavorites(paths: List<String>)

    // Discovered Files & Timestamps
    @Query("SELECT * FROM discovered_files WHERE path IN (:paths)")
    suspend fun getDiscoveredFiles(paths: List<String>): List<DiscoveredFileEntity>

    @Query("SELECT * FROM discovered_files WHERE path = :path LIMIT 1")
    suspend fun getDiscoveredFile(path: String): DiscoveredFileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiscoveredFilesIgnore(entities: List<DiscoveredFileEntity>)

    @Query("UPDATE discovered_files SET lastOpenedAtMillis = :openedAt WHERE path = :path")
    suspend fun updateLastOpened(path: String, openedAt: Long)

    @Query("UPDATE discovered_files SET cachedSha256 = :sha256, lastSeenModifiedMillis = :modifiedAt WHERE path = :path")
    suspend fun updateCachedHash(path: String, sha256: String, modifiedAt: Long)

    // Recently Opened & Playback Resume
    @Query("SELECT * FROM recent_opened ORDER BY openedAtMillis DESC LIMIT 50")
    fun observeRecentOpened(): Flow<List<RecentOpenedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentOpened(entity: RecentOpenedEntity)

    @Query("SELECT lastPlaybackPositionMs FROM recent_opened WHERE path = :path LIMIT 1")
    suspend fun getPlaybackPosition(path: String): Long?

    @Query("UPDATE recent_opened SET lastPlaybackPositionMs = :positionMs WHERE path = :path")
    suspend fun updatePlaybackPosition(path: String, positionMs: Long)

    @Query("DELETE FROM recent_opened WHERE path = :path")
    suspend fun removeRecentOpened(path: String)

    @Query("DELETE FROM recent_opened")
    suspend fun clearRecentOpened()

    // Recent Searches
    @Query("SELECT * FROM recent_searches ORDER BY searchedAtMillis DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    // Operation History
    @Query("SELECT * FROM operation_history ORDER BY timestampMillis DESC LIMIT 60")
    fun observeOperationHistory(): Flow<List<OperationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationLog(entity: OperationHistoryEntity)

    @Query("DELETE FROM operation_history")
    suspend fun clearOperationHistory()

    // --- Trash ---
    @Query("SELECT * FROM trash_files ORDER BY deletedAtMillis DESC")
    fun observeTrashFiles(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_files WHERE originalPath = :originalPath LIMIT 1")
    suspend fun getTrashItem(originalPath: String): TrashEntity?

    @Query("SELECT * FROM trash_files WHERE deletedAtMillis < :thresholdMillis")
    suspend fun getExpiredTrashItems(thresholdMillis: Long): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity)

    @Delete
    suspend fun deleteTrashItem(item: TrashEntity)

    @Query("DELETE FROM trash_files")
    suspend fun clearTrash()
}

@Database(
    entities = [
        FavoriteEntity::class,
        PinnedItemEntity::class,
        DiscoveredFileEntity::class,
        RecentOpenedEntity::class,
        RecentSearchEntity::class,
        OperationHistoryEntity::class,
        TrashEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun auroraDao(): AuroraDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aurora_files.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
