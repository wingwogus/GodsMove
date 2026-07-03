package com.chamchamcham.domain.farm

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "farm")
class Farm(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    val owner: Member,

    @Column(nullable = false, length = 128)
    val name: String,

    @Column(name = "road_address", nullable = false, length = 255)
    val roadAddress: String,

    @Column(name = "jibun_address", length = 255)
    val jibunAddress: String? = null,

    @Column
    val latitude: Double? = null,

    @Column
    val longitude: Double? = null,

    @Column(length = 32)
    val pnu: String? = null,

    @Column(name = "land_category", length = 64)
    val landCategory: String? = null,

    @Column(name = "area_sqm", precision = 12, scale = 2)
    val areaSqm: BigDecimal? = null,

    @Column(name = "area_is_manual_entry", nullable = false)
    val areaIsManualEntry: Boolean = false,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_boundary_coordinate", joinColumns = [JoinColumn(name = "farm_id")])
    @OrderColumn(name = "sequence")
    val boundaryCoordinates: MutableList<FarmBoundaryCoordinate> = mutableListOf(),

    @Embedded
    val dataSource: FarmDataSource = FarmDataSource()
) : BaseTimeEntity()

@Embeddable
class FarmBoundaryCoordinate(
    @Column(name = "latitude")
    val latitude: Double? = null,

    @Column(name = "longitude")
    val longitude: Double? = null
)

@Embeddable
class FarmDataSource(
    @Column(name = "data_source_address", length = 64)
    val address: String? = null,

    @Column(name = "data_source_coordinate", length = 64)
    val coordinate: String? = null,

    @Column(name = "data_source_parcel", length = 64)
    val parcel: String? = null,

    @Column(name = "data_source_land_characteristic", length = 64)
    val landCharacteristic: String? = null
)
