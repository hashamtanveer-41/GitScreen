package com.zivxio.gitscreen.service

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore

/**
 * Notifies [onNewImage] whenever a new row appears under the images collection.
 * We filter down to actual "screenshot-looking" paths in [ScreenshotWatcherService],
 * since the observer itself can't easily distinguish screenshots from other new photos.
 */
class ScreenshotContentObserver(
    handler: Handler,
    private val onNewImage: (Uri) -> Unit
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val target = uri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        onNewImage(target)
    }

    companion object {
        val CONTENT_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
