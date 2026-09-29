package mihon.core.archive

import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import mihon.core.archive.ArchiveEntry as MihonArchiveEntry

class ArchiveInputStream(
    buffer: Long,
    size: Long,
    // SY -->
    encrypted: Boolean,
    // SY <--
) : InputStream() {
    private val lock = Any()

    @Volatile
    private var isClosed = false

    private val archive = Archive.readNew()

    init {
        try {
            // SY -->
            if (encrypted) {
                Archive.readAddPassphrase(archive, CbzCrypto.getDecryptedPasswordCbz())
            }
            // SY <--
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenMemoryUnsafe(archive, buffer, size)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    private val oneByteBuffer = ByteBuffer.allocateDirect(1)
    private val directBuffer = ByteBuffer.allocateDirect(64 * 1024)

    override fun read(): Int {
        synchronized(lock) {
            if (isClosed) return -1
            oneByteBuffer.clear()
            oneByteBuffer.limit(1)
            Archive.readData(archive, oneByteBuffer)
            oneByteBuffer.flip()
            return if (oneByteBuffer.hasRemaining()) oneByteBuffer.get().toUByte().toInt() else -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        synchronized(lock) {
            if (isClosed) return -1
            val toRead = minOf(len, directBuffer.capacity())
            directBuffer.clear()
            directBuffer.limit(toRead)
            Archive.readData(archive, directBuffer)
            directBuffer.flip()
            val readBytes = directBuffer.remaining()
            if (readBytes <= 0) return -1
            directBuffer.get(b, off, readBytes)
            return readBytes
        }
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            Archive.readFree(archive)
        }
    }

    fun getNextEntry(): MihonArchiveEntry? {
        return Archive.readNextHeader(archive).takeUnless { it == 0L }?.let { entry ->
            val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.decodeToString() ?: return null
            val isFile = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFREG
            // SY -->
            val isEncrypted = ArchiveEntry.isEncrypted(entry)
            // SY <--
            MihonArchiveEntry(
                name,
                isFile,
                // SY -->
                isEncrypted,
                // SY <--
            )
        }
    }
}
