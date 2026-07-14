package com.chamchamcham.domain.pesticide

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "pesticide",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_pesticide_item_brand", columnNames = ["item_name", "brand_name"])
    ]
)
class Pesticide(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "item_name", nullable = false, length = 128)
    var itemName: String,

    @Column(name = "brand_name", nullable = false, length = 128)
    var brandName: String,

    @Column(name = "active_ingredient", length = 128)
    var activeIngredient: String? = null,

    @Column(length = 64)
    var formulation: String? = null,

    @Column(name = "usage_category", length = 32)
    var usageCategory: String? = null,

    @Column(name = "human_toxicity", length = 32)
    var humanToxicity: String? = null,

    @Column(name = "fish_toxicity", length = 32)
    var fishToxicity: String? = null,

    @Column(length = 128)
    var manufacturer: String? = null,
) : BaseTimeEntity() {
    fun updateCatalogData(
        activeIngredient: String?,
        formulation: String?,
        usageCategory: String?,
        humanToxicity: String?,
        fishToxicity: String?,
        manufacturer: String?,
    ) {
        this.activeIngredient = activeIngredient
        this.formulation = formulation
        this.usageCategory = usageCategory
        this.humanToxicity = humanToxicity
        this.fishToxicity = fishToxicity
        this.manufacturer = manufacturer
    }
}
