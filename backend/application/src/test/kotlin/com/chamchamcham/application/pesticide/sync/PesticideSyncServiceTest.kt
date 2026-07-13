package com.chamchamcham.application.pesticide.sync

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideApplication
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * PesticideRepository/PestRepository/PesticideApplicationRepository의 커스텀 조회 메서드는 Kotlin에서
 * 선언된 non-null 파라미터를 갖고 있어, Mockito 5의 Kotlin null-safety 검증 때문에 any()/any(Class)/
 * nullable() 같은 와일드카드 매처를 스텁 설정에 쓰면 "must not be null" 예외가 난다. 그래서 이 테스트는
 * 고정된 UUID/문자열 등 구체값으로만 스텁한다. save()는 상속받은 Java(JpaRepository) 메서드라 영향이
 * 없어 any(Class)를 그대로 쓴다.
 */
@ExtendWith(MockitoExtension::class)
class PesticideSyncServiceTest {
    @Mock private lateinit var transport: PsisPesticideHttpTransport
    @Mock private lateinit var pesticideRepository: PesticideRepository
    @Mock private lateinit var pestRepository: PestRepository
    @Mock private lateinit var pesticideApplicationRepository: PesticideApplicationRepository

    private lateinit var service: PesticideSyncService

    private val pesticideId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val pestId1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val pestId2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val savedPesticide = Pesticide(id = pesticideId, itemName = "만코제브 수화제", brandName = "가가방")
    private val savedPest1 = Pest(id = pestId1, name = "역병")
    private val savedPest2 = Pest(id = pestId2, name = "탄저병")

    @BeforeEach
    fun setUp() {
        lenient().`when`(pesticideRepository.save(any(Pesticide::class.java))).thenReturn(savedPesticide)
        lenient().`when`(pestRepository.save(any(Pest::class.java))).thenAnswer { invocation ->
            when (invocation.getArgument<Pest>(0).name) {
                "역병" -> savedPest1
                else -> savedPest2
            }
        }
        lenient().`when`(pesticideApplicationRepository.save(any(PesticideApplication::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        service = PesticideSyncService(
            transport = transport,
            responseParser = PsisPesticideResponseParser(),
            rowMapper = PsisPesticideRowMapper(),
            pesticideRepository = pesticideRepository,
            pestRepository = pestRepository,
            pesticideApplicationRepository = pesticideApplicationRepository,
            transactionTemplate = TransactionTemplate(NoopTransactionManager()),
        )
    }

    @Test
    fun `dedupes the same pesticide across rows within one page`() {
        `when`(pesticideRepository.findByItemNameAndBrandName("만코제브 수화제", "가가방"))
            .thenReturn(null, savedPesticide)
        `when`(pestRepository.findByName("역병")).thenReturn(null)
        `when`(pestRepository.findByName("탄저병")).thenReturn(null)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId1, "감자")
        ).thenReturn(null)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId2, "강낭콩")
        ).thenReturn(null)
        `when`(transport.get(anyMap())).thenReturn(twoRowPageXml())

        val result = service.sync(pageSize = 100)

        assertEquals(2, result.fetchedRowCount)
        assertEquals(2, result.createdApplicationCount)
    }

    @Test
    fun `stops paginating once a page returns fewer rows than the page size`() {
        `when`(pesticideRepository.findByItemNameAndBrandName("만코제브 수화제", "가가방"))
            .thenReturn(null, savedPesticide)
        `when`(pestRepository.findByName("역병")).thenReturn(null)
        `when`(pestRepository.findByName("탄저병")).thenReturn(null)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId1, "감자")
        ).thenReturn(null)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId2, "강낭콩")
        ).thenReturn(null)
        `when`(transport.get(anyMap())).thenReturn(twoRowPageXml())

        val result = service.sync(pageSize = 100)

        assertEquals(1, result.pageCount)
    }

    @Test
    fun `does not duplicate an application already synced in a prior run`() {
        `when`(pesticideRepository.findByItemNameAndBrandName("만코제브 수화제", "가가방")).thenReturn(savedPesticide)
        `when`(pestRepository.findByName("역병")).thenReturn(savedPest1)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId1, "감자")
        ).thenReturn(
            null,
            PesticideApplication(pesticide = savedPesticide, pest = savedPest1, cropName = "감자")
        )
        `when`(transport.get(anyMap())).thenReturn(oneRowPageXml())

        service.sync(pageSize = 100)
        val secondResult = service.sync(pageSize = 100)

        assertEquals(0, secondResult.createdApplicationCount)
    }

    @Test
    fun `throws when upstream responds with an error resultCode`() {
        `when`(transport.get(anyMap())).thenReturn(errorEnvelopeXml())

        val exception = assertThrows(BusinessException::class.java) {
            service.sync(pageSize = 100)
        }

        assertEquals(ErrorCode.PESTICIDE_SYNC_FAILED, exception.errorCode)
    }

    private fun errorEnvelopeXml(): String = """
        <response>
          <header>
            <resultCode>03</resultCode>
            <resultMsg>NODATA_ERROR</resultMsg>
          </header>
          <body>
            <items/>
            <totalCount>0</totalCount>
          </body>
        </response>
    """.trimIndent()

    private fun oneRowPageXml(): String = """
        <response><body><items>
          <item>
            <prdtNm>만코제브 수화제</prdtNm>
            <trdmrkNm>가가방</trdmrkNm>
            <cropNm>감자</cropNm>
            <aplyPestNm>역병</aplyPestNm>
            <dltnMag>500배</dltnMag>
          </item>
        </items></body></response>
    """.trimIndent()

    private fun twoRowPageXml(): String = """
        <response><body><items>
          <item>
            <prdtNm>만코제브 수화제</prdtNm>
            <trdmrkNm>가가방</trdmrkNm>
            <cropNm>감자</cropNm>
            <aplyPestNm>역병</aplyPestNm>
            <dltnMag>500배</dltnMag>
          </item>
          <item>
            <prdtNm>만코제브 수화제</prdtNm>
            <trdmrkNm>가가방</trdmrkNm>
            <cropNm>강낭콩</cropNm>
            <aplyPestNm>탄저병</aplyPestNm>
            <dltnMag>500배</dltnMag>
          </item>
        </items></body></response>
    """.trimIndent()

    private class NoopTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
