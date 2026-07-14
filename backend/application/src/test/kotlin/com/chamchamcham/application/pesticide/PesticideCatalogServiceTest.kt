package com.chamchamcham.application.pesticide

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideQueryRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PesticideCatalogServiceTest {
    @Mock private lateinit var pesticideRepository: PesticideRepository
    @Mock private lateinit var pesticideQueryRepository: PesticideQueryRepository
    @Mock private lateinit var pesticideApplicationRepository: PesticideApplicationRepository

    private lateinit var service: PesticideCatalogService

    @BeforeEach
    fun setUp() {
        service = PesticideCatalogService(
            pesticideRepository = pesticideRepository,
            pesticideQueryRepository = pesticideQueryRepository,
            pesticideApplicationRepository = pesticideApplicationRepository,
            cursorCodec = OpaqueCursorCodec(),
        )
    }

    @Test
    fun `search returns no next cursor when results fit within the page`() {
        val pesticide = Pesticide(id = UUID.randomUUID(), itemName = "만코제브 수화제", brandName = "가가방")
        `when`(
            pesticideQueryRepository.search(PesticideQueryRepository.SearchCondition(keyword = null, cursor = null, size = 21))
        ).thenReturn(listOf(pesticide))

        val result = service.search(keyword = null, cursor = null, size = 20)

        assertEquals(1, result.items.size)
        assertEquals("가가방", result.items[0].brandName)
        assertNull(result.nextCursor)
    }

    @Test
    fun `search returns a next cursor when more rows exist than the page size`() {
        val pesticides = (1..3).map {
            Pesticide(id = UUID.randomUUID(), itemName = "품목$it", brandName = "상표$it")
        }
        `when`(
            pesticideQueryRepository.search(PesticideQueryRepository.SearchCondition(keyword = null, cursor = null, size = 3))
        ).thenReturn(pesticides)

        val result = service.search(keyword = null, cursor = null, size = 2)

        assertEquals(2, result.items.size)
        assertEquals(false, result.nextCursor.isNullOrBlank())
    }

    @Test
    fun `search rejects a non-positive size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(keyword = null, cursor = null, size = 0)
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `listPestsByPesticide throws when the pesticide does not exist`() {
        val pesticideId = UUID.randomUUID()
        `when`(pesticideRepository.existsById(pesticideId)).thenReturn(false)

        val exception = assertThrows(BusinessException::class.java) {
            service.listPestsByPesticide(pesticideId)
        }

        assertEquals(ErrorCode.PESTICIDE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `listPestsByPesticide returns pests treatable by the pesticide`() {
        val pesticideId = UUID.randomUUID()
        `when`(pesticideRepository.existsById(pesticideId)).thenReturn(true)
        `when`(pesticideApplicationRepository.findDistinctPestsByPesticideId(pesticideId))
            .thenReturn(listOf(Pest(id = UUID.randomUUID(), name = "역병")))

        val result = service.listPestsByPesticide(pesticideId)

        assertEquals(1, result.size)
        assertEquals("역병", result[0].name)
    }
}
