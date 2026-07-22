package com.chamchamcham.application.pesticide.sync

data class PsisPesticideEnvelope(
    val errorCode: String?,
    val errorMsg: String?,
    val totalCount: Int?,
    val items: List<Map<String, String>>,
)
