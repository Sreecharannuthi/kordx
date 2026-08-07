package com.android.rockages.kordx.services.radio

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Read-only provider for cached artwork consumed by Android Auto projection. */
class ArtworkContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val key = uri.pathSegments.singleOrNull()
            ?: throw FileNotFoundException("Invalid artwork URI")
        val root = File(requireNotNull(context).dataDir, ARTWORK_DIRECTORY)
        val file = File(root, key)
        if (file.parentFile?.canonicalFile != root.canonicalFile || !file.isFile) {
            throw FileNotFoundException("Artwork not found")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.android.rockages.kordx.artwork"
        private const val ARTWORK_DIRECTORY = "covers"

        fun uriFor(fileName: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(fileName)
            .build()
    }
}
