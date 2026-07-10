package com.chamchamcham.domain.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class CycleReportStatisticsTest {
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
}
