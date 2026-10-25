package com.zivxio.gitscreen.detection

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies a screenshot from its original gallery location into GitScreen's own
 * organized folder structure: Pictures/GitScreen/<AppName>/<Version>/<file>.
 *
 * We copy rather than move for MVP safety — the user's original screenshot is
 * left untouched in case detection got something wrong. Once the flow is
 * trusted this can switch to a move (delete original after successful copy).
 */
class ScreenshotOrganizer(private val context: Context) {

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * @return the content Uri of the newly created, organized copy, or null on failure.
     */
    fun organize(originalUri: Uri, appName: String, versionLabel: String, takenAt: Long): Uri? {
        val resolver = context.contentResolver
        val safeAppName = sanitize(appName)
        val safeVersion = sanitize(versionLabel)
        val fileName = "gitscreen_${fileNameFormat.format(Date(takenAt))}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/GitScreen/$safeAppName/$safeVersion"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val newUri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openInputStream(originalUri)?.use { input ->
                resolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(newUri, doneValues, null, null)
            }
            newUri
        } catch (e: Exception) {
            resolver.delete(newUri, null, null)
            null
        }
    }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9 ._-]"), "_").ifBlank { "Unknown" }
}
