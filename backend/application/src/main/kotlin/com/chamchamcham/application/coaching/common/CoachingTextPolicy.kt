package com.chamchamcham.application.coaching.common

object CoachingTextPolicy {
    val promptInstructions: String = """
        농부에게 보여 줄 모든 문장은 친근한 존댓말로 작성한다.
        영어 알파벳, 내부 enum, 영어 필드명과 영어 단위를 쓰지 않는다.
        관수는 물 주기, 시비는 거름 주기처럼 일상에서 쓰는 말로 풀어 쓴다.
        공식 기술 문서의 어려운 표현을 그대로 복사하지 말고 뜻을 쉬운 말로 설명한다.
        농약 때문에 생긴 피해는 약해라고 줄이지 말고 약 때문에 생긴 피해라고 쓴다.
    """.trimIndent()

    fun containsEnglishLetter(text: String): Boolean = ENGLISH_LETTER.containsMatchIn(text)

    private val ENGLISH_LETTER = Regex("[A-Za-z]")
}
