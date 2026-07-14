package com.chamchamcham.application.coaching.common

object CoachingTextPolicy {
    val promptInstructions: String = """
        농부에게 보여 줄 summary와 text는 친근한 존댓말로 작성한다.
        영어 알파벳, 내부 enum, 영어 필드명과 영어 단위를 쓰지 않는다.
        관수는 물 주기, 시비는 거름 주기처럼 일상에서 쓰는 말로 풀어 쓴다.
        공식 기술 문서의 어려운 표현을 그대로 복사하지 말고 뜻을 쉬운 말로 설명한다.
        농약 때문에 생긴 피해는 약해라고 줄이지 말고 약 때문에 생긴 피해라고 쓴다.
    """.trimIndent()

    fun hasDisallowedLanguage(text: String): Boolean {
        return ENGLISH_LETTER.containsMatchIn(text) || DISALLOWED_TERMS.any(text::contains)
    }

    private val ENGLISH_LETTER = Regex("[A-Za-z]")

    private val DISALLOWED_TERMS = setOf(
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
    )
}
