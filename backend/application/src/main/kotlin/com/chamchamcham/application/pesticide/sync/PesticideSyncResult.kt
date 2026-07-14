package com.chamchamcham.application.pesticide.sync

data class PesticideSyncResult(
    val fetchedRowCount: Int,
    val createdApplicationCount: Int,
    val pageCount: Int,
)
