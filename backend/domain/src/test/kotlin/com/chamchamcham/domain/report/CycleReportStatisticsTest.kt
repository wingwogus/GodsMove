package com.chamchamcham.domain.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class CycleReportStatisticsTest {
    private val commonPropertyNames = listOf(
        "recordCount",
        "firstWorkedOn",
        "lastWorkedOn",
        "workedDayCount",
        "averageIntervalDays",
        "photoAttachedRecordCount",
        "photoAttachmentRatePct",
        "weatherDistribution",
        "averageTemperatureC",
    )

    @Test
    fun `empty statistics contain every work type with direct common fields`() {
        val result = CycleReportStatistics.empty()

        assertThat(result.planting.recordCount).isZero()
        assertThat(result.watering.firstWorkedOn).isNull()
        assertThat(result.fertilizing.photoAttachmentRatePct).isNull()
        assertThat(result.pestControl.weatherDistribution).isEmpty()
        assertThat(result.weeding.methodDistribution).isEmpty()
        assertThat(result.pruning.recordCount).isZero()
        assertThat(result.harvest.totalAmountKg).isNull()
        assertThat(result.etc.recordCount).isZero()
        assertThat(CycleReportStatistics::class.memberProperties.map { it.name })
            .doesNotContain("common")
    }

    @Test
    fun `every work statistics type directly exposes exact common property names`() {
        val result = CycleReportStatistics.empty()
        val workStatistics = listOf(
            result.planting,
            result.watering,
            result.fertilizing,
            result.pestControl,
            result.weeding,
            result.pruning,
            result.harvest,
            result.etc,
        )

        workStatistics.forEach { statistics ->
            val propertyNames = statistics::class.memberProperties.map { it.name }

            assertThat(propertyNames)
                .containsAll(commonPropertyNames)
            assertThat(propertyNames.filter { it in commonPropertyNames })
                .containsExactlyInAnyOrderElementsOf(commonPropertyNames)
        }
    }
}
