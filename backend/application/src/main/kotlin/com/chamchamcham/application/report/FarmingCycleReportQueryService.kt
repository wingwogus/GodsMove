package com.chamchamcham.application.report

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FarmingCycleReportQueryService(
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository,
    private val reportRepository: FarmingCycleReportRepository,
    private val queryRepository: FarmingCycleReportQueryRepository,
    private val cursorCodec: OpaqueCursorCodec,
) {
    fun getCurrent(
        memberId: UUID,
        farmId: UUID,
        cropId: UUID,
    ): FarmingCycleReportResult.Current {
        validateScope(memberId, farmId, cropId)
        val current = reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
            memberId,
            farmId,
            cropId,
            FarmingCycleReportStatus.ACTIVE,
        )
        val previous = queryRepository.findLatestCompleted(memberId, farmId, cropId)

        return FarmingCycleReportResult.Current(
            current = current?.let(::toSnapshot),
            previous = previous?.let(::toSnapshot),
        )
    }

    fun listCompleted(
        condition: FarmingCycleReportSearchCondition,
    ): FarmingCycleReportResult.Page {
        validatePageSize(condition.size)
        validateScope(condition.memberId, condition.farmId, condition.cropId)
        val cursor = decodeCursor(condition.cursor)
        val result = queryRepository.searchCompleted(
            FarmingCycleReportQueryRepository.SearchCondition(
                memberId = condition.memberId,
                farmId = condition.farmId,
                cropId = condition.cropId,
                cursor = cursor,
                size = condition.size + 1,
            ),
        )
        val visibleRows = result.rows.take(condition.size)
        val nextCursor = if (result.rows.size > condition.size) {
            visibleRows.lastOrNull()?.let(::encodeCursor)
        } else {
            null
        }

        return FarmingCycleReportResult.Page(
            items = visibleRows.map(::toMetadata),
            nextCursor = nextCursor,
        )
    }

    fun getDetail(
        memberId: UUID,
        reportId: UUID,
    ): FarmingCycleReportResult.Detail {
        val selected = reportRepository.findByIdAndMember_Id(reportId, memberId)
            ?.takeUnless { it.status == FarmingCycleReportStatus.SUPERSEDED }
            ?: throw BusinessException(ErrorCode.REPORT_NOT_FOUND)

        val farmId = requireNotNull(selected.farm.id) { "Persisted farm id is required" }
        val cropId = requireNotNull(selected.crop.id) { "Persisted crop id is required" }
        val previous = when (selected.status) {
            FarmingCycleReportStatus.ACTIVE ->
                queryRepository.findLatestCompleted(memberId, farmId, cropId)
            FarmingCycleReportStatus.COMPLETED ->
                queryRepository.findPreviousCompleted(
                    memberId = memberId,
                    farmId = farmId,
                    cropId = cropId,
                    endsAt = requireNotNull(selected.endsAt) { "Completed report endsAt is required" },
                    finalHarvestRecordId = requireNotNull(selected.finalHarvestRecord?.id) {
                        "Completed report final harvest record id is required"
                    },
                )
            FarmingCycleReportStatus.SUPERSEDED -> null
        }

        return FarmingCycleReportResult.Detail(
            selected = toSnapshot(selected),
            previous = previous?.let(::toSnapshot),
        )
    }

    private fun validateScope(memberId: UUID, farmId: UUID, cropId: UUID) {
        farmRepository.findByIdAndOwnerId(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        if (!memberCropRepository.existsByMemberIdAndFarmIdAndCropId(memberId, farmId, cropId)) {
            throw BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
    }

    private fun validatePageSize(size: Int) {
        if (size !in 1..100) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun decodeCursor(cursor: String?): FarmingCycleReportQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        val payload = cursorCodec.decode(
            cursor,
            FarmingCycleReportCursorPayload::class.java,
        )
        return FarmingCycleReportQueryRepository.Cursor(
            endsAt = payload.endsAt,
            finalHarvestRecordId = payload.finalHarvestRecordId,
        )
    }

    private fun encodeCursor(report: FarmingCycleReport): String =
        cursorCodec.encode(
            FarmingCycleReportCursorPayload(
                endsAt = requireNotNull(report.endsAt) { "Completed report endsAt is required" },
                finalHarvestRecordId = requireNotNull(report.finalHarvestRecord?.id) {
                    "Completed report final harvest record id is required"
                },
            ),
        )

    private fun toMetadata(report: FarmingCycleReport): FarmingCycleReportResult.Metadata =
        FarmingCycleReportResult.Metadata(
            id = requireNotNull(report.id) { "Persisted farming cycle report id is required" },
            farmId = requireNotNull(report.farm.id) { "Persisted farm id is required" },
            farmName = report.farm.name,
            cropId = requireNotNull(report.crop.id) { "Persisted crop id is required" },
            cropName = report.crop.name,
            status = report.status,
            startsAt = report.startsAt,
            endsAt = report.endsAt,
            startBasis = report.startBasis,
            sourceRevision = report.sourceRevision,
        )

    private fun toSnapshot(report: FarmingCycleReport): FarmingCycleReportResult.Snapshot =
        FarmingCycleReportResult.Snapshot(
            id = requireNotNull(report.id) { "Persisted farming cycle report id is required" },
            farmId = requireNotNull(report.farm.id) { "Persisted farm id is required" },
            farmName = report.farm.name,
            cropId = requireNotNull(report.crop.id) { "Persisted crop id is required" },
            cropName = report.crop.name,
            status = report.status,
            startsAt = report.startsAt,
            endsAt = report.endsAt,
            startBasis = report.startBasis,
            finalHarvestRecordId = report.finalHarvestRecord?.id,
            statisticsSchemaVersion = report.statisticsSchemaVersion,
            sourceRevision = report.sourceRevision,
            statistics = report.statistics,
        )
}
