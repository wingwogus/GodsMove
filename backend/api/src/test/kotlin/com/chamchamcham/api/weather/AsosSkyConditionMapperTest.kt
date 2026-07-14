package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AsosSkyConditionMapperTest {

    @Test
    fun `일강수량이 0보다 크면 전운량과 무관하게 비를 반환한다`() {
        val result = AsosSkyConditionMapper.of(avgTca = 0.0, sumRn = 5.0)

        assertThat(result).isEqualTo("비")
    }

    @Test
    fun `일강수량이 0이면 강수로 취급하지 않는다`() {
        val result = AsosSkyConditionMapper.of(avgTca = 1.0, sumRn = 0.0)

        assertThat(result).isEqualTo("맑음")
    }

    @Test
    fun `일강수량이 null이면 강수로 취급하지 않는다`() {
        val result = AsosSkyConditionMapper.of(avgTca = 1.0, sumRn = null)

        assertThat(result).isEqualTo("맑음")
    }

    @Test
    fun `평균 전운량이 null이면 정보없음을 반환한다`() {
        val result = AsosSkyConditionMapper.of(avgTca = null, sumRn = null)

        assertThat(result).isEqualTo("정보없음")
    }

    @Test
    fun `평균 전운량이 0에서 2 사이면 맑음을 반환한다`() {
        assertThat(AsosSkyConditionMapper.of(avgTca = 0.0, sumRn = null)).isEqualTo("맑음")
        assertThat(AsosSkyConditionMapper.of(avgTca = 2.0, sumRn = null)).isEqualTo("맑음")
    }

    @Test
    fun `평균 전운량이 2 초과 7 이하면 구름많음을 반환한다`() {
        assertThat(AsosSkyConditionMapper.of(avgTca = 2.1, sumRn = null)).isEqualTo("구름많음")
        assertThat(AsosSkyConditionMapper.of(avgTca = 7.0, sumRn = null)).isEqualTo("구름많음")
    }

    @Test
    fun `평균 전운량이 7 초과 10 이하면 흐림을 반환한다`() {
        assertThat(AsosSkyConditionMapper.of(avgTca = 7.1, sumRn = null)).isEqualTo("흐림")
        assertThat(AsosSkyConditionMapper.of(avgTca = 10.0, sumRn = null)).isEqualTo("흐림")
    }
}
