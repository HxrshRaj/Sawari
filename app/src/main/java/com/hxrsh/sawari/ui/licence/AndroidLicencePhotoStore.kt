package com.hxrsh.sawari.ui.licence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private storage (`filesDir/licence`): not shared with other apps, not in the gallery, never uploaded.
 * Files here are drafts: the booking screen deletes them on remove and after a successful booking.
 */
@Singleton
class AndroidLicencePhotoStore @Inject constructor(@ApplicationContext private val context: Context) : LicencePhotoStore {
    private val dir get() = File(context.filesDir, "licence").also { it.mkdirs() }

    override fun newFile(): File = File(dir, "licence-${UUID.randomUUID()}.jpg")

    override fun delete(path: String) {
        // Only ever delete inside our own folder, whatever path we are handed.
        val f = File(path)
        if (f.parentFile?.canonicalPath == dir.canonicalPath) f.delete()
    }

    override suspend fun importFrom(open: () -> InputStream?): String? = withContext(Dispatchers.IO) {
        val target = newFile()
        try {
            val input = open() ?: return@withContext null
            input.use { src -> target.outputStream().use { src.copyTo(it) } }
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }
}
