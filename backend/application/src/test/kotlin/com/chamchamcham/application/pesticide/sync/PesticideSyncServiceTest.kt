package com.chamchamcham.application.pesticide.sync

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideApplication
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import com.chamchamcham.domain.pesticide.PesticideSyncJob
import com.chamchamcham.domain.pesticide.PesticideSyncJobRepository
import com.chamchamcham.domain.pesticide.PesticideSyncJobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.UUID

/**
 * PesticideRepository/PestRepository/PesticideApplicationRepository/PesticideSyncJobRepository의 커스텀
 * 조회 메서드는 Kotlin에서 선언된 non-null 파라미터를 갖고 있어, Mockito 5의 Kotlin null-safety 검증 때문에
 * any()/any(Class)/nullable() 같은 와일드카드 매처를 스텁 설정에 쓰면 "must not be null" 예외가 난다. 그래서
 * 이 테스트는 고정된 UUID/문자열 등 구체값으로만 스텁한다. save()는 상속받은 Java(JpaRepository) 메서드라
 * 영향이 없어 any(Class)를 그대로 쓴다.
 */
@ExtendWith(MockitoExtension::class)
class PesticideSyncServiceTest {
    @Mock private lateinit var transport: PsisPesticideHttpTransport
    @Mock private lateinit var pesticideRepository: PesticideRepository
    @Mock private lateinit var pestRepository: PestRepository
    @Mock private lateinit var pesticideApplicationRepository: PesticideApplicationRepository
    @Mock private lateinit var pesticideSyncJobRepository: PesticideSyncJobRepository

    private lateinit var service: PesticideSyncService
    private lateinit var persistedJob: PesticideSyncJob

    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val pesticideId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val pestId = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val savedPesticide = Pesticide(id = pesticideId, itemName = "만코제브 수화제", brandName = "가가방")
    private val savedPest = Pest(id = pestId, name = "역병")

    @BeforeEach
    fun setUp() {
        lenient().`when`(pesticideRepository.save(any(Pesticide::class.java))).thenReturn(savedPesticide)
        lenient().`when`(pestRepository.save(any(Pest::class.java))).thenReturn(savedPest)
        lenient().`when`(pesticideApplicationRepository.save(any(PesticideApplication::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        service = PesticideSyncService(
            transport = transport,
            responseParser = PsisPesticideResponseParser(),
            rowMapper = PsisPesticideRowMapper(),
            pesticideRepository = pesticideRepository,
            pestRepository = pestRepository,
            pesticideApplicationRepository = pesticideApplicationRepository,
            pesticideSyncJobRepository = pesticideSyncJobRepository,
            transactionTemplate = TransactionTemplate(NoopTransactionManager()),
        )
    }

    @Test
    fun `createSyncJob saves a RUNNING job and returns its summary`() {
        stubJobSaveAndFind()

        val result = service.createSyncJob(adminMemberId)

        assertEquals(jobId, result.jobId)
        assertEquals(PesticideSyncJobStatus.RUNNING, result.status)
    }

    @Test
    fun `runExistingJob paginates by startPoint until totalCount is exhausted and succeeds the job`() {
        stubJobSaveAndFind()
        `when`(pesticideRepository.findByItemNameAndBrandName("만코제브 수화제", "가가방"))
            .thenReturn(null, savedPesticide)
        `when`(pestRepository.findByName("역병")).thenReturn(null, savedPest)
        `when`(
            pesticideApplicationRepository.findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId, "감자")
        ).thenReturn(null, PesticideApplication(pesticide = savedPesticide, pest = savedPest, cropName = "감자"))
        `when`(transport.get(anyMap())).thenAnswer { invocation ->
            val params = invocation.getArgument<Map<String, String>>(0)
            if (params["startPoint"] == "1") {
                pageXml(totalCount = 51, itemCount = 50)
            } else {
                pageXml(totalCount = 51, itemCount = 1)
            }
        }

        service.createSyncJob(adminMemberId)
        service.runExistingJob(jobId)

        assertEquals(PesticideSyncJobStatus.SUCCEEDED, persistedJob.status)
        assertEquals(51, persistedJob.totalCount)
        assertEquals(51, persistedJob.fetchedRowCount)
        assertEquals(1, persistedJob.createdApplicationCount)
        assertNotNull(persistedJob.finishedAt)

        val detail = service.getJob(jobId)
        assertEquals(PesticideSyncJobStatus.SUCCEEDED, detail.status)
        assertEquals(51, detail.fetchedRowCount)
    }

    @Test
    fun `runExistingJob marks the job FAILED when upstream responds with an errorCode`() {
        stubJobSaveAndFind()
        `when`(transport.get(anyMap())).thenReturn(errorEnvelopeXml())

        service.createSyncJob(adminMemberId)
        service.runExistingJob(jobId)

        assertEquals(PesticideSyncJobStatus.FAILED, persistedJob.status)
        assertNotNull(persistedJob.errorMessage)
        verifyNoInteractions(pesticideRepository, pestRepository, pesticideApplicationRepository)
    }

    @Test
    fun `probe fetches one page and reports diagnostics without writing to the database`() {
        `when`(transport.get(anyMap())).thenReturn(pageXml(totalCount = 2, itemCount = 2))

        val result = service.probe(rows = 10)

        assertEquals(2, result.totalCount)
        assertEquals(2, result.itemCount)
        val mapped = result.mapped
        requireNotNull(mapped)
        assertEquals("만코제브 수화제", mapped.itemName)
        verifyNoInteractions(pesticideRepository, pestRepository, pesticideApplicationRepository)
    }

    @Test
    fun `probe throws when upstream responds with an errorCode`() {
        `when`(transport.get(anyMap())).thenReturn(errorEnvelopeXml())

        val exception = assertThrows(BusinessException::class.java) {
            service.probe(rows = 10)
        }

        assertEquals(ErrorCode.PESTICIDE_SYNC_FAILED, exception.errorCode)
    }

    private fun stubJobSaveAndFind() {
        `when`(pesticideSyncJobRepository.save(any(PesticideSyncJob::class.java))).thenAnswer { invocation ->
            val job = invocation.arguments[0] as PesticideSyncJob
            persistedJob = PesticideSyncJob(
                id = jobId,
                status = job.status,
                startedAt = job.startedAt,
                finishedAt = job.finishedAt,
                totalCount = job.totalCount,
                fetchedRowCount = job.fetchedRowCount,
                createdApplicationCount = job.createdApplicationCount,
                errorMessage = job.errorMessage,
                createdByMemberId = job.createdByMemberId,
            )
            persistedJob
        }
        lenient().`when`(pesticideSyncJobRepository.findById(jobId)).thenAnswer { Optional.of(persistedJob) }
    }

    private fun errorEnvelopeXml(): String = """
        <service>
          <errorCode>ERR_101</errorCode>
          <errorMsg>인증키가 등록되지 않았습니다. 정상적인 인증키를 확인하세요.</errorMsg>
        </service>
    """.trimIndent()

    private fun pageXml(totalCount: Int, itemCount: Int): String {
        val items = (1..itemCount).joinToString("\n") {
            """
            <item>
              <pestiKorName>만코제브 수화제</pestiKorName>
              <pestiBrandName>가가방</pestiBrandName>
              <cropName>감자</cropName>
              <diseaseWeedName>역병</diseaseWeedName>
              <dilutUnit>500배</dilutUnit>
            </item>
            """.trimIndent()
        }
        return """
            <service>
              <totalCount>$totalCount</totalCount>
              <list>
                $items
              </list>
            </service>
        """.trimIndent()
    }

    private class NoopTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
