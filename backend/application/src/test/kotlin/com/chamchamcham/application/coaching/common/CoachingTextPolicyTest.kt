package com.chamchamcham.application.coaching.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingTextPolicyTest {
    @Test
    fun `allows friendly Korean text with Korean units`() {
        assertThat(CoachingTextPolicy.containsEnglishLetter("뿌리 쪽 흙을 살펴보고 물을 조금 주세요.")).isFalse()
        assertThat(CoachingTextPolicy.containsEnglishLetter("수확량은 96킬로그램이었어요.")).isFalse()
    }

    @Test
    fun `rejects any English letter`() {
        listOf(
            "WATERING으로 했어요.",
            "10kg을 썼어요.",
            "pH를 확인하세요.",
        ).forEach { text ->
            assertThat(CoachingTextPolicy.containsEnglishLetter(text)).isTrue()
        }
    }

    @Test
    fun `allows Korean text including farming terms`() {
        listOf(
            "관수",
            "시비",
            "방제",
            "병해충",
            "생육",
            "정식",
            "파종",
            "제초",
            "전정",
            "과습",
            "배수",
            "추대",
            "하엽",
            "토양",
            "수분",
            "살포",
            "살균제",
            "유기질",
        ).forEach { term ->
            assertThat(CoachingTextPolicy.containsEnglishLetter("${term}을 확인하세요.")).isFalse()
        }
    }

    @Test
    fun `does not reject ambiguous everyday weak expression`() {
        assertThat(CoachingTextPolicy.containsEnglishLetter("잎이 약해 보이면 먼저 살펴보세요.")).isFalse()
    }
}
