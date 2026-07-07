package com.chamchamcham.application.media

interface ImageUploader {
    fun upload(command: UploadCommand): UploadedImage

    data class UploadCommand(
        val bytes: ByteArray,
        val originalFilename: String?,
        val contentType: String?,
        val usageFolder: String
    )

    data class UploadedImage(
        val imageUrl: String,
        val cloudinaryPublicId: String
    )
}
