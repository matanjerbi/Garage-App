package com.matan.garage.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Files (photos, receipts, PDFs) live in the app's private "media" folder; the DB stores only file names. */
object Media {
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }
    fun file(context: Context, name: String) = File(dir(context), name)

    /** Copies a picked file into the media folder. */
    fun importUri(context: Context, uri: Uri): Attachment? {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext = when {
            mime == "application/pdf" -> "pdf"
            mime.startsWith("image/") -> mime.substringAfter("/").let { if (it == "jpeg") "jpg" else it }
            else -> "bin"
        }
        val name = "${UUID.randomUUID()}.$ext"
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { inp -> file(context, name).outputStream().use { inp.copyTo(it) } }
        return Attachment(ownerType = OwnerType.SERVICE_RECORD, ownerId = 0, fileName = name, mimeType = mime)
    }

    fun newPhotoFile(context: Context): File = file(context, "${UUID.randomUUID()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun open(context: Context, fileName: String, mime: String) {
        val f = file(context, fileName)
        if (!f.exists()) {
            Toast.makeText(context, "הקובץ לא נמצא", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, f), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "אין אפליקציה שיכולה לפתוח את הקובץ", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadBitmap(context: Context, fileName: String, maxPx: Int): ImageBitmap? {
        val f = file(context, fileName)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    }
}
