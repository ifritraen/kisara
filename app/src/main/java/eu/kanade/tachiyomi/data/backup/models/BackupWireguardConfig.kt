package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupWireguardConfig(
    @ProtoNumber(1) val filename: String,
    @ProtoNumber(2) val data: String,
)

@Serializable
data class BackupWireguardAssociation(
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val value: String,
)

@Serializable
data class BackupWireguardPreferences(
    @ProtoNumber(1) val defaultProfile: String? = null,
    @ProtoNumber(2) val sourceAssociations: List<BackupWireguardAssociation> = emptyList(),
)
