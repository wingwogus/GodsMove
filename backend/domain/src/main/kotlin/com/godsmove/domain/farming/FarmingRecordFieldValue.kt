package com.godsmove.domain.farming

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "farming_record_field_value")
class FarmingRecordFieldValue(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    val record: FarmingRecord,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_type_field_id", nullable = false)
    val workTypeField: WorkTypeField,

    @Column(name = "value_text", columnDefinition = "text")
    val valueText: String? = null,

    @Column(name = "value_number", precision = 18, scale = 4)
    val valueNumber: BigDecimal? = null,

    @Column(name = "value_boolean")
    val valueBoolean: Boolean? = null,

    @Column(name = "value_date")
    val valueDate: LocalDate? = null,

    @Column(name = "value_json", columnDefinition = "text")
    val valueJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
