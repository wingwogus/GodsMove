package com.chamchamcham.domain.farming

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

@Entity
@Table(
    name = "work_type_field",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_work_type_field_work_type_field_key",
            columnNames = ["work_type_id", "field_key"]
        )
    ]
)
class WorkTypeField(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_type_id", nullable = false)
    val workType: WorkType,

    @Column(name = "field_key", nullable = false, length = 128)
    val fieldKey: String,

    @Column(nullable = false, length = 128)
    val title: String,

    @Column(name = "data_type", nullable = false, length = 32)
    val dataType: String,

    @Column(nullable = false)
    val required: Boolean = false,

    @Column(length = 32)
    val unit: String? = null,

    @Column(name = "input_method", nullable = false, length = 32)
    val inputMethod: String,

    @Column(columnDefinition = "text")
    val description: String? = null,
) : BaseTimeEntity()
