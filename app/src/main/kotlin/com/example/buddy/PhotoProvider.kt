package com.example.buddy

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class PhotoProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException("no context")
        if (uri.authority != AUTHORITY || uri.pathSegments != listOf(NAME)) throw FileNotFoundException("denied")
        return file(ctx)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = resolve(uri)
        val flags = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> throw FileNotFoundException("mode not allowed")
        }
        if (mode != "r") f.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(f, flags)
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val f = try { resolve(uri) } catch (e: FileNotFoundException) { return null }
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = arrayOfNulls<Any>(cols.size)
        for (i in cols.indices) {
            row[i] = when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = "com.example.buddy.photo"
        private const val NAME = "shot.jpg"

        fun uri(): Uri = Uri.parse("content://$AUTHORITY/$NAME")

        fun file(ctx: Context): File = File(File(ctx.cacheDir, "ocr"), NAME)

        fun clear(ctx: Context) {
            File(ctx.cacheDir, "ocr").listFiles()?.forEach { it.delete() }
        }
    }
}
