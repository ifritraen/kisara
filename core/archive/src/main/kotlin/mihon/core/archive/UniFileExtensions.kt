package mihon.core.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile

internal fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor {
    val path = filePath
    if (!path.isNullOrBlank()) {
        try {
            val file = java.io.File(path)
            if (file.exists() || mode.contains("w") || mode.contains("t")) {
                val modeBits = ParcelFileDescriptor.parseMode(mode)
                return ParcelFileDescriptor.open(file, modeBits)
            }
        } catch (_: Exception) {}
    }
    return context.contentResolver.openFileDescriptor(uri, mode)
        ?: error("Failed to open file descriptor: ${filePath ?: uri}")
}

fun UniFile.archiveReader(context: Context) = openFileDescriptor(context, "r").use { ArchiveReader(it) }

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))
