package com.chamchamcham.domain.crop

import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest(
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"]
)
@ActiveProfiles("test")
class MemberCropRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository
) {
    @Test
    fun `policy profile queries fetch multiple farms and crops without additional select`() {
        val member = entityManager.persistFlushFind(
            Member(email = "policy-query@example.com", passwordHash = null)
        )
        listOf(
            Triple(
                "제천 참당귀 농장",
                "충청북도 제천시 정책로 1",
                Crop(externalNo = 9001, name = "참당귀", usePartCategory = CropUsePartCategory.ROOT_BARK)
            ),
            Triple(
                "영주 황기 농장",
                "경상북도 영주시 정책로 2",
                Crop(externalNo = 9002, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
            ),
            Triple(
                "의성 작약 농장",
                "경상북도 의성군 정책로 3",
                Crop(externalNo = 9003, name = "작약", usePartCategory = CropUsePartCategory.ROOT_BARK)
            )
        ).forEachIndexed { index, (farmName, roadAddress, crop) ->
            val farm = entityManager.persistFlushFind(
                Farm(
                    owner = member,
                    name = farmName,
                    roadAddress = roadAddress,
                    jibunAddress = roadAddress.replace("정책로", "정책길"),
                    boundaryCoordinates = mutableListOf(
                        FarmBoundaryCoordinate(36.0 + index, 127.0 + index)
                    )
                )
            )
            val savedCrop = entityManager.persistFlushFind(crop)
            entityManager.persist(
                MemberCrop(member = member, farm = farm, crop = savedCrop)
            )
        }
        entityManager.flush()
        entityManager.clear()

        val statistics = entityManager.entityManager.entityManagerFactory
            .unwrap(SessionFactory::class.java)
            .statistics
        statistics.clear()

        val loadedMember = entityManager.find(Member::class.java, requireNotNull(member.id))
        val farms = farmRepository.findAllWithBoundaryCoordinatesByOwnerId(
            requireNotNull(loadedMember.id)
        )
        val memberCrops = memberCropRepository.findAllWithCropByMemberId(
            requireNotNull(loadedMember.id)
        )
        val afterRepositoryQuery = statistics.prepareStatementCount
        val farmValues = farms.map {
            Triple(
                it.roadAddress,
                it.jibunAddress,
                it.boundaryCoordinates.single().latitude to it.boundaryCoordinates.single().longitude
            )
        }
        val cropValues = memberCrops.map { it.crop.name to it.crop.usePartCategory }
        val afterProfileFieldAccess = statistics.prepareStatementCount

        assertThat(farms).hasSize(3)
        assertThat(memberCrops).hasSize(3)
        assertThat(farmValues).containsExactlyInAnyOrder(
            Triple("충청북도 제천시 정책로 1", "충청북도 제천시 정책길 1", 36.0 to 127.0),
            Triple("경상북도 영주시 정책로 2", "경상북도 영주시 정책길 2", 37.0 to 128.0),
            Triple("경상북도 의성군 정책로 3", "경상북도 의성군 정책길 3", 38.0 to 129.0)
        )
        assertThat(cropValues).containsExactlyInAnyOrder(
            "참당귀" to CropUsePartCategory.ROOT_BARK,
            "황기" to CropUsePartCategory.ROOT_BARK,
            "작약" to CropUsePartCategory.ROOT_BARK
        )
        assertThat(afterRepositoryQuery).isLessThanOrEqualTo(3L)
        assertThat(afterProfileFieldAccess).isEqualTo(afterRepositoryQuery)
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class MemberCropRepositoryTestApplication
