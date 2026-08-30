package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// KMK -->
@Serializable
data class BackupGranularScoreCriterion(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val score: Double,
    @ProtoNumber(4) val weight: Double,
    @ProtoNumber(5) val isExcluded: Boolean = false,
)

@Serializable
data class BackupGranularScore(
    @ProtoNumber(1) val templateName: String,
    @ProtoNumber(2) val criteria: List<BackupGranularScoreCriterion> = emptyList(),
    @ProtoNumber(3) val totalScore: Double = 0.0,
    @ProtoNumber(4) val scale10Score: Double = 0.0,
    @ProtoNumber(5) val ignoreUnrated: Boolean = true,
    @ProtoNumber(6) val autoSyncTracker: Boolean = true,
    @ProtoNumber(7) val updatedAt: Long = 0L,
)

@Serializable
data class BackupGranularTemplateCriterion(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val weight: Double,
)

@Serializable
data class BackupGranularTemplate(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val mediaType: String = "ALL",
    @ProtoNumber(3) val criteria: List<BackupGranularTemplateCriterion> = emptyList(),
    @ProtoNumber(4) val isDefault: Boolean = false,
)
// KMK <--
