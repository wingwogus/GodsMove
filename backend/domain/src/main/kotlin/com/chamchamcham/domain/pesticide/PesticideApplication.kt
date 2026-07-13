package com.chamchamcham.domain.pesticide

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * PSIS 농약안전사용기준 원본 행(작물 x 병해충 x 약제) 1건을 그대로 보존한 사실 테이블.
 * 희석배수 등 사용법 필드는 원문(raw) 포맷이 다양해 파싱하지 않고 문자열 그대로 저장한다.
 */
@Entity
@Table(
    name = "pesticide_application",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_pesticide_application_natural_key",
            columnNames = ["pesticide_id", "pest_id", "crop_name"]
        )
    ]
)
class PesticideApplication(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pesticide_id", nullable = false)
    val pesticide: Pesticide,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pest_id", nullable = false)
    val pest: Pest,

    @Column(name = "crop_name", nullable = false, length = 128)
    val cropName: String,

    @Column(name = "dilution_rate", length = 64)
    var dilutionRate: String? = null,

    @Column(name = "usage_amount", length = 64)
    var usageAmount: String? = null,

    @Column(name = "usage_timing", length = 64)
    var usageTiming: String? = null,

    @Column(name = "max_usage_count", length = 32)
    var maxUsageCount: String? = null,
) : BaseTimeEntity() {
    fun updateUsageDetails(
        dilutionRate: String?,
        usageAmount: String?,
        usageTiming: String?,
        maxUsageCount: String?,
    ) {
        this.dilutionRate = dilutionRate
        this.usageAmount = usageAmount
        this.usageTiming = usageTiming
        this.maxUsageCount = maxUsageCount
    }
}
