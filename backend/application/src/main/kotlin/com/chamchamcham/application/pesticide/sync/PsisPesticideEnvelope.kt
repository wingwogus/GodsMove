package com.chamchamcham.application.pesticide.sync

data class PsisPesticideEnvelope(
    val resultCode: String?,
    val resultMsg: String?,
    val totalCount: Int?,
    val items: List<Map<String, String>>,
)
