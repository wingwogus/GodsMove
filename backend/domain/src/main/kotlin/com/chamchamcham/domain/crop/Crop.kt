package com.chamchamcham.domain.crop

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "crop",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_crop_external_no", columnNames = ["external_no"])
    ]
)
class Crop(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "external_no", nullable = false)
    val externalNo: Int,

    @Column(nullable = false, length = 128)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "use_part_category", nullable = false, length = 64)
    var usePartCategory: CropUsePartCategory
) : BaseTimeEntity() {
    fun updateCatalogData(name: String, usePartCategory: CropUsePartCategory) {
        this.name = name
        this.usePartCategory = usePartCategory
    }
}
