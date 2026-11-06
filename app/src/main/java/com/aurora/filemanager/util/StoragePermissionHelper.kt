package com.aurora.filemanager.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Evaluates current storage access state and provides explicit, user-initiated intents
 * for granting either full file access (MANAGE_EXTERNAL_STORAGE on Android 11+) or
 * granular MediaStore permissions.
 *
 * Also accurately detects scoped-storage restricted folders (Android/data, Android/obb)
 * so the UI never fakes contents or crashes.
 */
data class StorageAccessStatus(
    val hasFullStorageAccess: Boolean,
    val hasMediaAccess: Boolean,
    val requiresAllFilesPermissionScreen: Boolean
) {
    val canReadAnySharedFiles: Boolean
        get() = hasFullStorageAccess || hasMediaAccess
}

object StoragePermissionHelper {

    fun checkAccessStatus(context: Context): StorageAccessStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isExternalStorageManager = Environment.isExternalStorageManager()
            val hasMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            StorageAccessStatus(
                hasFullStorageAccess = isExternalStorageManager,
                hasMediaAccess = isExternalStorageManager || hasMedia,
                requiresAllFilesPermissionScreen = !isExternalStorageManager
            )
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            StorageAccessStatus(
                hasFullStorageAccess = readGranted,
                hasMediaAccess = readGranted,
                requiresAllFilesPermissionScreen = false
            )
        }
    }

    fun runtimePermissionsToRequest(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun buildManageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }

    /**
     * Checks if a folder is protected by Android 11+ Scoped Storage (such as Android/data or Android/obb).
     */
    fun isScopedStorageRestrictedDirectory(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val normalized = file.absolutePath.replace('\\', '/').trimEnd('/')
        return normalized.endsWith("/Android/data", ignoreCase = true) ||
            normalized.endsWith("/Android/obb", ignoreCase = true) ||
            normalized.contains("/Android/data/", ignoreCase = true) ||
            normalized.contains("/Android/obb/", ignoreCase = true)
    }
}
