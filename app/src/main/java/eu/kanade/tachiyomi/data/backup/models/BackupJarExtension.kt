package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupJarExtension(
    @ProtoNumber(1) val filename: String,
    @ProtoNumber(2) val data: ByteArray,
    @ProtoNumber(3) val repoName: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupJarExtension

        if (filename != other.filename) return false
        if (!data.contentEquals(other.data)) return false
        if (repoName != other.repoName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (repoName?.hashCode() ?: 0)
        return result
    }
}
