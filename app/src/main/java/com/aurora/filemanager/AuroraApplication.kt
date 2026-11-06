package com.aurora.filemanager

import android.app.Application
import android.content.ComponentCallbacks2
import com.aurora.filemanager.data.local.AppDatabase
import com.aurora.filemanager.data.preferences.UserPreferencesRepository
import com.aurora.filemanager.data.repository.FileRepository
import com.aurora.filemanager.util.ThumbnailAndMetadataManager

/**
 * Application entry point providing singleton repositories and memory-trim handling.
 * Zero analytics, zero advertising SDKs, zero network calls.
 */
class AuroraApplication : Application() {

    companion object {
        lateinit var instance: AuroraApplication
            private set
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val fileRepository: FileRepository by lazy { FileRepository(this, database.auroraDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ThumbnailAndMetadataManager.clearMemoryCaches()
        }
    }
}
