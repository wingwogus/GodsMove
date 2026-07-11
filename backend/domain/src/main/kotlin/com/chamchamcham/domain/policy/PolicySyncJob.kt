package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "policy_sync_job")
class PolicySyncJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val source: PolicySource,

    @Column(name = "target_year", nullable = false, length = 4)
    val targetYear: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    val triggerType: PolicySyncTriggerType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PolicySyncJobStatus = PolicySyncJobStatus.RUNNING,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0,

    @Column(name = "synced_count", nullable = false)
    var syncedCount: Int = 0,

    @Column(name = "detail_success_count", nullable = false)
    var detailSuccessCount: Int = 0,

    @Column(name = "detail_failure_count", nullable = false)
    var detailFailureCount: Int = 0,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "created_by_member_id", columnDefinition = "uuid")
    val createdByMemberId: UUID? = null,
) : BaseTimeEntity() {
    fun succeed(totalCount: Int, syncedCount: Int, detailSuccessCount: Int, detailFailureCount: Int) {
        this.status = PolicySyncJobStatus.SUCCEEDED
        this.totalCount = totalCount
        this.syncedCount = syncedCount
        this.detailSuccessCount = detailSuccessCount
        this.detailFailureCount = detailFailureCount
        this.errorMessage = null
        this.finishedAt = LocalDateTime.now()
    }

    fun fail(message: String) {
        this.status = PolicySyncJobStatus.FAILED
        this.errorMessage = message.take(1000)
        this.finishedAt = LocalDateTime.now()
    }
}
