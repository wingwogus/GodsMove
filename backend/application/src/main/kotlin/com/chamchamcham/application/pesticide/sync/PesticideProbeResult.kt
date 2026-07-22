package com.chamchamcham.application.pesticide.sync

data class PesticideProbeResult(
    val errorCode: String?,
    val errorMsg: String?,
    val totalCount: Int?,
    val itemCount: Int,
    val distinctTagNames: List<String>,
    val sampleRawItem: Map<String, String>?,
    val requiredKeyResolution: Map<String, Boolean>,
    val mapped: PsisPesticideRow?,
)
