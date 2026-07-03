package com.chamchamcham.config

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
@Profile("local", "dev", "prod")
class MedicinalCropSeedRunner(
    private val cropRepository: CropRepository,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val seedRows = ClassPathResource(SEED_RESOURCE).inputStream.use { input ->
            objectMapper.readValue(input, Array<MedicinalCropSeedRow>::class.java).toList()
        }
        val existingByExternalNo = cropRepository.findByExternalNoIn(seedRows.map { it.externalNo })
            .associateBy { it.externalNo }

        val crops = seedRows.map { row ->
            existingByExternalNo[row.externalNo]?.apply {
                updateCatalogData(row.name, row.usePartCategory)
            } ?: Crop(
                externalNo = row.externalNo,
                name = row.name,
                usePartCategory = row.usePartCategory
            )
        }

        cropRepository.saveAll(crops)
        logger.info { "Medicinal crop seed synchronized: ${crops.size}" }
    }

    private companion object {
        const val SEED_RESOURCE = "seed/medicinal-crops.json"
    }
}

data class MedicinalCropSeedRow(
    val externalNo: Int,
    val name: String,
    val usePartCategory: CropUsePartCategory
)
