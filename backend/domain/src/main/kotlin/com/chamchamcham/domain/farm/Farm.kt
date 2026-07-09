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
    var name: String,

    @Column(name = "road_address", nullable = false, length = 255)
    var roadAddress: String,

    @Column(name = "jibun_address", length = 255)
    var jibunAddress: String? = null,

    @Column
    var latitude: Double? = null,

    @Column
    var longitude: Double? = null,

    @Column(length = 32)
    var pnu: String? = null,

    @Column(name = "land_category", length = 64)
    var landCategory: String? = null,

    @Column(name = "area_sqm", precision = 12, scale = 2)
    var areaSqm: BigDecimal? = null,

    @Column(name = "area_is_manual_entry", nullable = false)
    var areaIsManualEntry: Boolean = false,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "farm_boundary_coordinate", joinColumns = [JoinColumn(name = "farm_id")])
    @OrderColumn(name = "sequence")
    val boundaryCoordinates: MutableList<FarmBoundaryCoordinate> = mutableListOf(),

    @Embedded
    var dataSource: FarmDataSource = FarmDataSource()
) : BaseTimeEntity() {
    fun updateProfile(
        name: String,
        roadAddress: String,
        jibunAddress: String?,
        latitude: Double?,
        longitude: Double?,
        pnu: String?,
        landCategory: String?,
        areaSqm: BigDecimal?,
        areaIsManualEntry: Boolean,
        boundaryCoordinates: List<FarmBoundaryCoordinate>,
        dataSource: FarmDataSource
    ) {
        this.name = name
        this.roadAddress = roadAddress
        this.jibunAddress = jibunAddress
        this.latitude = latitude
        this.longitude = longitude
        this.pnu = pnu
        this.landCategory = landCategory
        this.areaSqm = areaSqm
        this.areaIsManualEntry = areaIsManualEntry
        this.boundaryCoordinates.clear()
        this.boundaryCoordinates.addAll(boundaryCoordinates)
        this.dataSource = dataSource
    }
}

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
