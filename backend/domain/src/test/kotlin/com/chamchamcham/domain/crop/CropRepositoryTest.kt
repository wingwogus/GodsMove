package com.chamchamcham.domain.crop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
class CropRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val cropRepository: CropRepository
) {
    @Test
    fun `keyword search matches crop name ignoring case`() {
        persistCrop(name = "황기", externalNo = 1)
        persistCrop(name = "인삼", externalNo = 2)
        entityManager.flush()
        entityManager.clear()

        val result = cropRepository.findTop9ByNameContainingIgnoreCaseOrderByNameAsc("황")

        assertThat(result.map { it.name }).containsExactly("황기")
    }

    @Test
    fun `keyword search limits to top 9 ordered by name ascending`() {
        (1..12).forEach { i -> persistCrop(name = "작물${"%02d".format(i)}", externalNo = 100 + i) }
        entityManager.flush()
        entityManager.clear()

        val result = cropRepository.findTop9ByNameContainingIgnoreCaseOrderByNameAsc("작물")

        assertThat(result).hasSize(9)
        assertThat(result.map { it.name }).isSorted
    }

    private fun persistCrop(name: String, externalNo: Int): Crop =
        entityManager.persistFlushFind(
            Crop(externalNo = externalNo, name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)
        )
}
