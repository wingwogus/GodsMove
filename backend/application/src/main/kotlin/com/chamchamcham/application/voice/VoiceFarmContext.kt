package com.chamchamcham.application.voice

import java.util.UUID

data class FarmOption(val farmId: UUID, val name: String)

data class CropOption(val cropId: UUID, val name: String)

data class VoicePesticideOption(val name: String, val pests: List<String>)
