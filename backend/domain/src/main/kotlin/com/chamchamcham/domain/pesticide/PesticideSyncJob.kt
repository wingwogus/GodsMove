package com.chamchamcham.domain.pesticide

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
@Table(name = "pesticide_sync_job")
class PesticideSyncJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PesticideSyncJobStatus = PesticideSyncJobStatus.RUNNING,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0,

    @Column(name = "fetched_row_count", nullable = false)
    var fetchedRowCount: Int = 0,

    @Column(name = "created_application_count", nullable = false)
    var createdApplicationCount: Int = 0,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "created_by_member_id", columnDefinition = "uuid")
    val createdByMemberId: UUID? = null,
) : BaseTimeEntity() {
    fun succeed(totalCount: Int, fetchedRowCount: Int, createdApplicationCount: Int) {
        this.status = PesticideSyncJobStatus.SUCCEEDED
        this.totalCount = totalCount
        this.fetchedRowCount = fetchedRowCount
        this.createdApplicationCount = createdApplicationCount
        this.errorMessage = null
        this.finishedAt = LocalDateTime.now()
    }

    fun fail(message: String) {
        this.status = PesticideSyncJobStatus.FAILED
        this.errorMessage = message.take(1000)
        this.finishedAt = LocalDateTime.now()
    }
}
