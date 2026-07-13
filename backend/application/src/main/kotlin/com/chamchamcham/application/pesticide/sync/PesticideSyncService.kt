package com.chamchamcham.application.pesticide.sync

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideApplication
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * PSIS 원본 데이터(작물 x 병해충 x 약제 사실 테이블, 약 137,877건)를 전량 페이지네이션 순회하며
 * Pesticide/Pest/PesticideApplication으로 dedup·upsert한다.
 *
 * 매 서버 기동마다 자동 실행되지 않는다 — 관리자 트리거(AdminPesticideSyncController)로만 호출된다.
 * 행 단위 findBy 쿼리를 쓰므로 137k건 전체 동기화는 느릴 수 있다(1회성 관리 작업이라 우선 정확성을
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
    private val transactionTemplate: TransactionTemplate,
) {
    fun sync(pageSize: Int = DEFAULT_PAGE_SIZE): PesticideSyncResult {
        var pageNo = 1
        var pagesFetched = 0
        var fetchedRowCount = 0
        var createdApplicationCount = 0

        while (true) {
            val body = transport.get(
                mapOf(
                    "pageNo" to pageNo.toString(),
                    "numOfRows" to pageSize.toString(),
                    "type" to "xml",
                )
            )
            val envelope = responseParser.parseEnvelope(body)
            failOnUpstreamError(envelope)
            val rawRows = envelope.items
            if (rawRows.isEmpty()) {
                break
            }
            pagesFetched += 1

            val rows = rawRows.mapNotNull(rowMapper::map)
            fetchedRowCount += rawRows.size
            createdApplicationCount += transactionTemplate.execute { upsertRows(rows) } ?: 0

            if (rawRows.size < pageSize) {
                break
            }
            pageNo += 1
        }

        return PesticideSyncResult(
            fetchedRowCount = fetchedRowCount,
            createdApplicationCount = createdApplicationCount,
            pageCount = pagesFetched,
        )
    }

    fun probe(rows: Int = DEFAULT_PROBE_ROWS): PesticideProbeResult {
        val body = transport.get(
            mapOf(
                "pageNo" to "1",
                "numOfRows" to rows.toString(),
                "type" to "xml",
            )
        )
        val envelope = responseParser.parseEnvelope(body)
        failOnUpstreamError(envelope)

        val sampleRawItem = envelope.items.firstOrNull()
        return PesticideProbeResult(
            resultCode = envelope.resultCode,
            resultMsg = envelope.resultMsg,
            totalCount = envelope.totalCount,
            itemCount = envelope.items.size,
            distinctTagNames = envelope.items.flatMap { it.keys }.distinct().sorted(),
            sampleRawItem = sampleRawItem,
            requiredKeyResolution = sampleRawItem?.let(rowMapper::diagnoseRequired) ?: emptyMap(),
            mapped = sampleRawItem?.let(rowMapper::map),
        )
    }

    private fun failOnUpstreamError(envelope: PsisPesticideEnvelope) {
        val resultCode = envelope.resultCode?.trim() ?: return
        if (resultCode in SUCCESS_RESULT_CODES) {
            return
        }
        throw BusinessException(
            ErrorCode.PESTICIDE_SYNC_FAILED,
            detail = mapOf("resultCode" to resultCode, "resultMsg" to envelope.resultMsg),
        )
    }

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

    private companion object {
        const val DEFAULT_PAGE_SIZE = 1000
        const val DEFAULT_PROBE_ROWS = 10
        val SUCCESS_RESULT_CODES = setOf("00", "0")
    }
}
