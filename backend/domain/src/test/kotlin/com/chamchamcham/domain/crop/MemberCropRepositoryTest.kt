package com.chamchamcham.domain.crop

import com.chamchamcham.domain.farm.Farm
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
    private val memberCropRepository: MemberCropRepository
) {
    @Test
    fun `policy crop query fetches every crop without additional select`() {
        val member = entityManager.persistFlushFind(
            Member(email = "policy-query@example.com", passwordHash = null)
        )
        val farm = entityManager.persistFlushFind(
            Farm(
                owner = member,
                name = "정책 조회 농장",
                roadAddress = "충청북도 제천시 정책로 1"
            )
        )
        listOf(
            Crop(externalNo = 9001, name = "참당귀", usePartCategory = CropUsePartCategory.ROOT_BARK),
            Crop(externalNo = 9002, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
            Crop(externalNo = 9003, name = "작약", usePartCategory = CropUsePartCategory.ROOT_BARK)
        ).forEach { crop ->
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

        val memberCrops = memberCropRepository.findAllWithCropByMemberId(
            requireNotNull(member.id)
        )
        val afterRepositoryQuery = statistics.prepareStatementCount
        val cropValues = memberCrops.map { it.crop.name to it.crop.usePartCategory }
        val afterCropAccess = statistics.prepareStatementCount

        assertThat(memberCrops).hasSize(3)
        assertThat(cropValues).containsExactlyInAnyOrder(
            "참당귀" to CropUsePartCategory.ROOT_BARK,
            "황기" to CropUsePartCategory.ROOT_BARK,
            "작약" to CropUsePartCategory.ROOT_BARK
        )
        assertThat(afterRepositoryQuery).isLessThanOrEqualTo(3L)
        assertThat(afterCropAccess).isEqualTo(afterRepositoryQuery)
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class MemberCropRepositoryTestApplication
