package com.chamchamcham.application.report

import com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackLifecycleService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class FarmingCycleReportProjectionService(
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val sourceLoader: FarmingCycleReportSourceLoader,
    private val partitioner: FarmingCyclePartitioner,
    private val statisticsCalculator: CycleReportStatisticsCalculator,
    private val reportRepository: FarmingCycleReportRepository,
    private val reportFeedbackLifecycleService: ReportFeedbackLifecycleService,
) {
    fun rebuild(scope: ReportScope) {
        val farm = farmRepository.findOwnedByIdForReportUpdate(
            farmId = scope.farmId,
            memberId = scope.memberId,
        ) ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        val crop = cropRepository.findById(scope.cropId).orElseThrow {
            BusinessException(ErrorCode.CROP_NOT_FOUND)
        }
        val existing = reportRepository.findAllCurrent(
            memberId = scope.memberId,
            farmId = scope.farmId,
            cropId = scope.cropId,
        )
        val slices = partitioner.partition(sourceLoader.load(scope))
        val unmatched = existing.associateBy { requireNotNull(it.id) }.toMutableMap()

        slices.forEachIndexed { index, slice ->
            val matched = takeMatchingReport(
                unmatched = unmatched,
                slice = slice,
                allowActiveCompletion = index == slices.lastIndex &&
                    slice.status == FarmingCycleReportStatus.COMPLETED,
            )
            val projection = toProjection(slice)
            val report = matched?.also { it.applyProjection(projection) }
                ?: FarmingCycleReport.create(farm.owner, farm, crop, projection)
            reportRepository.save(report)
            if (report.status == FarmingCycleReportStatus.COMPLETED) {
                reportFeedbackLifecycleService.reconcile(
                    report = report,
                    workTypes = slice.records.map(CycleReportSourceRecord::workType).toSet(),
                )
            }
        }

        val obsolete = unmatched.values.filter { it.supersede() }
        if (obsolete.isNotEmpty()) {
            reportRepository.saveAll(obsolete)
        }
    }

    fun rebuildAll(scopes: Collection<ReportScope>) {
        scopes.distinct()
            .sortedWith(
                compareBy(
                    ReportScope::memberId,
                    ReportScope::farmId,
                    ReportScope::cropId,
                ),
            )
            .forEach(::rebuild)
    }

    private fun takeMatchingReport(
        unmatched: MutableMap<UUID, FarmingCycleReport>,
        slice: CycleSlice,
        allowActiveCompletion: Boolean,
    ): FarmingCycleReport? {
        val exactCompleted = slice.finalHarvestRecordId?.let { finalId ->
            unmatched.values.firstOrNull {
                it.status == FarmingCycleReportStatus.COMPLETED &&
                    it.finalHarvestRecord?.id == finalId
            }
        }
        val active = unmatched.values.singleOrNull {
            it.status == FarmingCycleReportStatus.ACTIVE
        }
        val selected = when {
            exactCompleted != null -> exactCompleted
            slice.status == FarmingCycleReportStatus.ACTIVE -> active
            allowActiveCompletion -> active
            else -> null
        }
        selected?.let { unmatched.remove(requireNotNull(it.id)) }
        return selected
    }

    private fun toProjection(slice: CycleSlice): FarmingCycleReportProjection {
        val completed = slice.status == FarmingCycleReportStatus.COMPLETED
        return FarmingCycleReportProjection(
            status = slice.status,
            startsAt = slice.records.first().workedAt,
            endsAt = slice.records.last().workedAt.takeIf { completed },
            startBasis = slice.startBasis,
            finalHarvestRecord = slice.finalHarvestRecordId
                ?.let(farmingRecordRepository::getReferenceById),
            statisticsSchemaVersion = STATISTICS_SCHEMA_VERSION,
            statistics = statisticsCalculator.calculate(slice.records),
        )
    }

    private companion object {
        const val STATISTICS_SCHEMA_VERSION = 3
    }
}
