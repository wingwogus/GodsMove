package com.chamchamcham.domain.farm

import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class FarmTest {
    @Test
    fun `update profile replaces editable farm fields`() {
        val owner = Member(id = UUID.randomUUID(), email = "owner@example.com", passwordHash = null)
        val farm = Farm(
            id = UUID.randomUUID(),
            owner = owner,
            name = "기존 농장",
            roadAddress = "강원특별자치도 횡성군 기존로 1",
            jibunAddress = "강원특별자치도 횡성군 기존리 1",
            latitude = 37.1,
            longitude = 128.1,
            pnu = "old-pnu",
            landCategory = "전",
            areaSqm = BigDecimal("100.00"),
            areaIsManualEntry = false,
            boundaryCoordinates = mutableListOf(FarmBoundaryCoordinate(37.1, 128.1)),
            dataSource = FarmDataSource(address = "OLD", coordinate = "OLD")
        )

        farm.updateProfile(
            name = "수정 농장",
            roadAddress = "강원특별자치도 평창군 새로 2",
            jibunAddress = null,
            latitude = 37.2,
            longitude = 128.2,
            pnu = "new-pnu",
            landCategory = "답",
            areaSqm = BigDecimal("200.50"),
            areaIsManualEntry = true,
            boundaryCoordinates = listOf(
                FarmBoundaryCoordinate(37.2, 128.2),
                FarmBoundaryCoordinate(37.3, 128.3)
            ),
            dataSource = FarmDataSource(address = "KAKAO", coordinate = "KAKAO", parcel = "PUBLIC_DATA")
        )

        assertThat(farm.name).isEqualTo("수정 농장")
        assertThat(farm.roadAddress).isEqualTo("강원특별자치도 평창군 새로 2")
        assertThat(farm.jibunAddress).isNull()
        assertThat(farm.latitude).isEqualTo(37.2)
        assertThat(farm.longitude).isEqualTo(128.2)
        assertThat(farm.pnu).isEqualTo("new-pnu")
        assertThat(farm.landCategory).isEqualTo("답")
        assertThat(farm.areaSqm).isEqualByComparingTo("200.50")
        assertThat(farm.areaIsManualEntry).isTrue()
        assertThat(farm.boundaryCoordinates).hasSize(2)
        assertThat(farm.dataSource.address).isEqualTo("KAKAO")
        assertThat(farm.dataSource.coordinate).isEqualTo("KAKAO")
        assertThat(farm.dataSource.parcel).isEqualTo("PUBLIC_DATA")
        assertThat(farm.dataSource.landCharacteristic).isNull()
    }
}
