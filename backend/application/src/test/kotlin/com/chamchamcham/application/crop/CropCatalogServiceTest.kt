package com.chamchamcham.application.crop

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CropCatalogServiceTest {
    @Mock
    private lateinit var cropRepository: CropRepository

    private lateinit var service: CropCatalogService

    @BeforeEach
    fun setUp() {
        service = CropCatalogService(cropRepository)
    }

    @Test
    fun `listCrops returns crop summaries`() {
        val cropId = UUID.fromString("20000000-0000-0000-0000-000000000001")
        `when`(cropRepository.findAllByOrderByNameAscExternalNoAsc()).thenReturn(
            listOf(
                Crop(
                    id = cropId,
                    externalNo = 422,
                    name = "참당귀",
                    usePartCategory = CropUsePartCategory.ROOT_BARK
                )
            )
        )

        val result = service.listCrops()

        assertThat(result).containsExactly(
            CropResult.CropSummary(
                id = cropId,
                externalNo = 422,
                name = "참당귀",
                usePartCategory = "ROOT_BARK",
                usePartCategoryLabel = "뿌리·껍질"
            )
        )
    }

    @Test
    fun `listCategories returns all enum categories in declaration order`() {
        val result = service.listCategories()

        assertThat(result).containsExactlyElementsOf(
            CropUsePartCategory.entries.map {
                CropResult.CategorySummary(code = it.name, label = it.label)
            }
        )
    }
}
