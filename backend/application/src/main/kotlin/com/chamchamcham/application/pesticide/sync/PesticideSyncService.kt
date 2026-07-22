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
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * PSIS 원본 데이터(작물 x 병해충 x 약제 사실 테이블, 약 143,912건)를 startPoint 오프셋 페이지네이션으로
 * 전량 순회하며 Pesticide/Pest/PesticideApplication으로 dedup·upsert한다.
 *
 * 매 서버 기동마다 자동 실행되지 않는다 — 관리자 트리거(AdminPesticideSyncController)로만 호출된다.
 * 143k건 전체 순회는 오래 걸리므로 [createSyncJob]으로 RUNNING 잡을 즉시 반환하고, 실제 순회는
 * [PesticideSyncAsyncRunner]가 비동기로 [runExistingJob]을 호출해 수행한다(PolicySyncJob과 동일한 패턴).
 * 행 단위 findBy 쿼리를 쓰므로 143k건 전체 동기화는 느릴 수 있다(1회성 관리 작업이라 우선 정확성을
 * 우선하고, 실제 실행해보고 느리면 배치 upsert로 최적화한다 - YAGNI).
 */
@Service
class PesticideSyncService(
    private val transport: PsisPesticideHttpTransport,
    private val responseParser: PsisPesticideResponseParser,
    private val rowMapper: PsisPesticideRowMapper,
    private val pesticideRepository: PesticideRepository,
    private val pestRepository: PestRepository,
    private val pesticideApplicationRepository: PesticideApplicationRepository,
    private val pesticideSyncJobRepository: PesticideSyncJobRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    fun createSyncJob(adminMemberId: UUID?): PesticideSyncResult.JobSummary {
        val job = transactionTemplate.execute {
            pesticideSyncJobRepository.save(PesticideSyncJob(createdByMemberId = adminMemberId))
        } ?: error("Transaction did not return a pesticide sync job")
        return PesticideSyncResult.JobSummary.from(job)
    }

    fun runExistingJob(jobId: UUID) {
        try {
            var startPoint = 1
            var fetchedRowCount = 0
            var createdApplicationCount = 0
            var totalCount: Int? = null

            while (true) {
                val body = transport.get(pageQuery(startPoint = startPoint, displayCount = DEFAULT_PAGE_SIZE))
                val envelope = responseParser.parseEnvelope(body)
                failOnUpstreamError(envelope)
                if (totalCount == null) {
                    totalCount = envelope.totalCount
                }
                val rawRows = envelope.items
                if (rawRows.isEmpty()) {
                    break
                }

                val rows = rawRows.mapNotNull(rowMapper::map)
                fetchedRowCount += rawRows.size
                createdApplicationCount += transactionTemplate.execute { upsertRows(rows) } ?: 0

                startPoint += DEFAULT_PAGE_SIZE
                val currentTotal = totalCount
                if (currentTotal != null && startPoint > currentTotal) {
                    break
                }
            }

            // 중간에 일시적 빈 페이지가 오면 totalCount에 못 미친 채 순회가 끝날 수 있다. 이때
            // SUCCEEDED로 두면 부분 적재가 조용히 성공으로 보이므로, 미달이면 실패로 표시한다(재실행은
            // dedup되어 안전).
            val resolvedTotal = totalCount
            if (resolvedTotal != null && fetchedRowCount < resolvedTotal) {
                throw IllegalStateException(
                    "PSIS 동기화가 불완전하게 종료되었습니다: 전체 ${resolvedTotal}건 중 ${fetchedRowCount}건만 수집(재실행 필요)"
                )
            }
            succeedJob(jobId, resolvedTotal ?: fetchedRowCount, fetchedRowCount, createdApplicationCount)
        } catch (exception: Exception) {
            failJob(jobId, exception)
        }
    }

    fun getJob(jobId: UUID): PesticideSyncResult.JobDetail {
        return transactionTemplate.execute {
            PesticideSyncResult.JobDetail.from(findJob(jobId))
        } ?: error("Transaction did not return a pesticide sync job detail")
    }

    fun probe(rows: Int = DEFAULT_PROBE_ROWS): PesticideProbeResult {
        val body = transport.get(pageQuery(startPoint = 1, displayCount = rows))
        val envelope = responseParser.parseEnvelope(body)
        failOnUpstreamError(envelope)

        val sampleRawItem = envelope.items.firstOrNull()
        return PesticideProbeResult(
            errorCode = envelope.errorCode,
            errorMsg = envelope.errorMsg,
            totalCount = envelope.totalCount,
            itemCount = envelope.items.size,
            distinctTagNames = envelope.items.flatMap { it.keys }.distinct().sorted(),
            sampleRawItem = sampleRawItem,
            requiredKeyResolution = sampleRawItem?.let(rowMapper::diagnoseRequired) ?: emptyMap(),
            mapped = sampleRawItem?.let(rowMapper::map),
        )
    }

    private fun failOnUpstreamError(envelope: PsisPesticideEnvelope) {
        val errorCode = envelope.errorCode ?: return
        throw BusinessException(
            ErrorCode.PESTICIDE_SYNC_FAILED,
            detail = mapOf("errorCode" to errorCode, "errorMsg" to envelope.errorMsg),
        )
    }

    // startPoint(1-based row offset)/displayCount만 다르고 나머지는 동일하므로 sync/probe가 같은
    // 쿼리 형태를 공유한다. displayCount는 PSIS 제약상 최대 50이라 여기서 clamp한다.
    private fun pageQuery(startPoint: Int, displayCount: Int): Map<String, String> = mapOf(
        "serviceCode" to SERVICE_CODE,
        "serviceType" to SERVICE_TYPE,
        "displayCount" to displayCount.coerceAtMost(MAX_PAGE_SIZE).toString(),
        "startPoint" to startPoint.toString(),
    )

    private fun upsertRows(rows: List<PsisPesticideRow>): Int {
        var created = 0
        rows.forEach { row ->
            val pesticide = upsertPesticide(row)
            val pest = upsertPest(row.pestName)
            if (upsertApplication(pesticide, pest, row)) {
                created += 1
            }
        }
        return created
    }

    private fun upsertPesticide(row: PsisPesticideRow): Pesticide {
        val existing = pesticideRepository.findByItemNameAndBrandName(row.itemName, row.brandName)
        val pesticide = existing ?: Pesticide(itemName = row.itemName, brandName = row.brandName)
        pesticide.updateCatalogData(
            activeIngredient = row.activeIngredient,
            formulation = row.formulation,
            usageCategory = row.usageCategory,
            humanToxicity = row.humanToxicity,
            fishToxicity = row.fishToxicity,
            manufacturer = row.manufacturer,
        )
        return pesticideRepository.save(pesticide)
    }

    private fun upsertPest(name: String): Pest {
        return pestRepository.findByName(name) ?: pestRepository.save(Pest(name = name))
    }

    private fun upsertApplication(pesticide: Pesticide, pest: Pest, row: PsisPesticideRow): Boolean {
        val pesticideId = requireNotNull(pesticide.id) { "Persisted pesticide id is required" }
        val pestId = requireNotNull(pest.id) { "Persisted pest id is required" }
        val existing = pesticideApplicationRepository
            .findByPesticide_IdAndPest_IdAndCropName(pesticideId, pestId, row.cropName)

        if (existing != null) {
            existing.updateUsageDetails(
                dilutionRate = row.dilutionRate,
                usageAmount = row.usageAmount,
                usageTiming = row.usageTiming,
                maxUsageCount = row.maxUsageCount,
            )
            return false
        }

        pesticideApplicationRepository.save(
            PesticideApplication(
                pesticide = pesticide,
                pest = pest,
                cropName = row.cropName,
                dilutionRate = row.dilutionRate,
                usageAmount = row.usageAmount,
                usageTiming = row.usageTiming,
                maxUsageCount = row.maxUsageCount,
            )
        )
        return true
    }

    private fun succeedJob(jobId: UUID, totalCount: Int, fetchedRowCount: Int, createdApplicationCount: Int) {
        transactionTemplate.executeWithoutResult {
            findJob(jobId).succeed(
                totalCount = totalCount,
                fetchedRowCount = fetchedRowCount,
                createdApplicationCount = createdApplicationCount,
            )
        }
    }

    private fun failJob(jobId: UUID, exception: Exception) {
        transactionTemplate.executeWithoutResult {
            findJob(jobId).fail(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun findJob(jobId: UUID): PesticideSyncJob =
        pesticideSyncJobRepository.findById(jobId).orElseThrow {
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND, detail = jobId)
        }

    private companion object {
        const val SERVICE_CODE = "SVC01"
        const val SERVICE_TYPE = "AA001"
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 50
        const val DEFAULT_PROBE_ROWS = 10
    }
}
