package com.godsmove.application.coaching.rag.seed

import com.godsmove.application.coaching.rag.FarmingRecordDocumentFactory
import com.godsmove.application.coaching.rag.IndexedFarmingRecord
import com.godsmove.application.coaching.rag.RagProperties
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Profile("local")
@Service
class DevRagSeedService(
    private val jdbcTemplate: JdbcTemplate,
    private val ragProperties: RagProperties,
    private val vectorStore: VectorStore,
    private val farmingRecordDocumentFactory: FarmingRecordDocumentFactory,
    @Value("\${app.dev.rag-seed-dir:}")
    private val seedDirectory: String = ""
) {
    @Transactional
    fun seed(command: DevRagSeedCommand): DevRagSeedResult {
        val pdfSeed = if (command.includePdf) {
            val rawPath = requireNotNull(command.pdfPath?.takeIf { it.isNotBlank() }) {
                "pdfPath is required when includePdf is true"
            }
            val path = resolveSeedPdfPath(rawPath)
            ExtractedPdfSeed(path = path.toString(), text = extractPdfText(path))
        } else {
            null
        }

        if (command.resetIndex) {
            resetSeedIndex()
        }

        seedRelationalData()

        val farmingRecordChunksIndexed = if (command.includeFarmingRecords) {
            seedFarmingRecordChunks()
        } else {
            0
        }

        val pdfChunksIndexed = if (command.includePdf) {
            seedPdfChunks(requireNotNull(pdfSeed), command.maxPdfChunks)
        } else {
            0
        }

        return DevRagSeedResult(
            memberId = SeedIds.MEMBER_ID,
            farmId = SeedIds.FARM_ID,
            cropId = SeedIds.CROP_ID,
            workTypeIds = SeedRecords.workTypes.mapValues { it.value.id },
            recordIds = SeedRecords.records.map { it.id },
            pdfChunksIndexed = pdfChunksIndexed,
            farmingRecordChunksIndexed = farmingRecordChunksIndexed,
            embeddingModel = ragProperties.embedding.model
        )
    }

    private fun resetSeedIndex() {
        jdbcTemplate.update("delete from vector_store where metadata ->> 'seedName' = ?", SEED_NAME)
    }

    private fun seedRelationalData() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        jdbcTemplate.update(
            """
                insert into member (
                  id, email, phone, status, name, nickname, region, experience_level,
                  management_type, password_hash, role, withdrawn_at, created_at, updated_at
                )
                values (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, null, 'ROLE_USER', null, ?, ?)
                on conflict (id) do update set
                  email = excluded.email,
                  phone = excluded.phone,
                  name = excluded.name,
                  nickname = excluded.nickname,
                  region = excluded.region,
                  experience_level = excluded.experience_level,
                  management_type = excluded.management_type,
                  updated_at = excluded.updated_at
            """.trimIndent(),
            SeedIds.MEMBER_ID,
            SeedPersona.email,
            SeedPersona.phone,
            SeedPersona.name,
            SeedPersona.nickname,
            SeedPersona.region,
            SeedPersona.experienceLevel,
            SeedPersona.managementType,
            now,
            now
        )

        jdbcTemplate.update(
            """
                insert into farm (id, owner_member_id, name, region, city, street, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                  owner_member_id = excluded.owner_member_id,
                  name = excluded.name,
                  region = excluded.region,
                  city = excluded.city,
                  street = excluded.street,
                  updated_at = excluded.updated_at
            """.trimIndent(),
            SeedIds.FARM_ID,
            SeedIds.MEMBER_ID,
            SeedFarm.name,
            SeedFarm.region,
            SeedFarm.city,
            SeedFarm.street,
            now,
            now
        )

        jdbcTemplate.update(
            """
                insert into crop (id, name, category, lifecycle_type, default_unit, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                  name = excluded.name,
                  category = excluded.category,
                  lifecycle_type = excluded.lifecycle_type,
                  default_unit = excluded.default_unit,
                  updated_at = excluded.updated_at
            """.trimIndent(),
            SeedIds.CROP_ID,
            SeedCrop.name,
            SeedCrop.category,
            SeedCrop.lifecycleType,
            SeedCrop.defaultUnit,
            now,
            now
        )

        SeedRecords.workTypes.values.forEach { workType ->
            jdbcTemplate.update(
                """
                    insert into work_type (id, name, created_at, updated_at)
                    values (?, ?, ?, ?)
                    on conflict (id) do update set
                      name = excluded.name,
                      updated_at = excluded.updated_at
                """.trimIndent(),
                workType.id,
                workType.name,
                now,
                now
            )
        }

        SeedRecords.records.forEach { record ->
            jdbcTemplate.update(
                """
                    insert into farming_record (
                      id, member_id, farm_id, crop_id, work_type_id, worked_at, memo,
                      entry_mode, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)
                    on conflict (id) do update set
                      member_id = excluded.member_id,
                      farm_id = excluded.farm_id,
                      crop_id = excluded.crop_id,
                      work_type_id = excluded.work_type_id,
                      worked_at = excluded.worked_at,
                      memo = excluded.memo,
                      entry_mode = excluded.entry_mode,
                      updated_at = excluded.updated_at
                """.trimIndent(),
                record.id,
                SeedIds.MEMBER_ID,
                SeedIds.FARM_ID,
                SeedIds.CROP_ID,
                record.workTypeId,
                record.workedAt,
                record.memo,
                now,
                now
            )
        }
    }

    private fun seedFarmingRecordChunks(): Int {
        val documents = SeedRecords.records.map { record ->
            val document = farmingRecordDocumentFactory.build(
                IndexedFarmingRecord(
                    recordId = record.id,
                    memberId = SeedIds.MEMBER_ID,
                    farmId = SeedIds.FARM_ID,
                    cropId = SeedIds.CROP_ID,
                    workTypeId = record.workTypeId,
                    memberName = SeedPersona.name,
                    memberRegion = SeedPersona.region,
                    farmName = SeedFarm.name,
                    cropName = SeedCrop.name,
                    workTypeName = record.workTypeName,
                    workedAt = record.workedAt,
                    memo = record.memo,
                    fieldValues = emptyList()
                )
            )
            document.withSeedName()
        }
        if (documents.isEmpty()) {
            return 0
        }
        vectorStore.add(documents)
        return documents.size
    }

    private fun seedPdfChunks(pdfSeed: ExtractedPdfSeed, maxPdfChunks: Int): Int {
        val chunks = chunkText(pdfSeed.text, maxChunks = maxPdfChunks)
        val documents = chunks.mapIndexed { index, content ->
            Document(
                seedDocumentId(TECH_DOC_SOURCE_ID, index),
                content,
                mapOf(
                    "sourceType" to "TECH_DOCUMENT",
                    "sourceId" to TECH_DOC_SOURCE_ID,
                    "label" to "농업기술길잡이7 약용작물 ${index + 1}",
                    "documentTitle" to "농업기술길잡이7 약용작물",
                    "pdfPath" to pdfSeed.path,
                    "seedName" to SEED_NAME,
                    "chunkIndex" to index
                )
            )
        }
        if (documents.isEmpty()) {
            return 0
        }
        vectorStore.add(documents)
        return documents.size
    }

    private fun resolveSeedPdfPath(pdfPath: String): Path {
        require(seedDirectory.isNotBlank()) {
            "app.dev.rag-seed-dir must be configured when includePdf is true"
        }

        val seedRoot = resolveRealPath(Path.of(seedDirectory).toAbsolutePath().normalize())
        require(Files.isDirectory(seedRoot)) {
            "Seed PDF directory does not exist: $seedRoot"
        }

        val requestedPath = Path.of(pdfPath)
        val normalizedPath = if (requestedPath.isAbsolute) {
            requestedPath.normalize()
        } else {
            seedRoot.resolve(requestedPath).normalize()
        }
        val realPath = resolveRealPath(normalizedPath)
        require(realPath.startsWith(seedRoot)) {
            "PDF file must be under configured seed directory"
        }
        require(Files.isRegularFile(realPath)) {
            "PDF file does not exist: $realPath"
        }
        return realPath
    }

    private fun resolveRealPath(path: Path): Path {
        return try {
            path.toRealPath()
        } catch (exception: IOException) {
            throw IllegalArgumentException("Path does not exist: $path", exception)
        }
    }

    private fun extractPdfText(path: Path): String {
        val fileSize = Files.size(path)
        require(fileSize <= MAX_SEED_PDF_BYTES) {
            "PDF file is too large for local seed: $fileSize bytes (max $MAX_SEED_PDF_BYTES)"
        }

        val process = ProcessBuilder(
            "pdftotext",
            "-layout",
            "-enc",
            "UTF-8",
            path.toString(),
            "-"
        )
            .redirectErrorStream(true)
            .start()

        val outputFuture = CompletableFuture.supplyAsync {
            readProcessOutput(process.inputStream, MAX_EXTRACTED_TEXT_BYTES)
        }
        val finished = try {
            process.waitFor(PDF_TEXT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            outputFuture.cancel(true)
            throw IllegalArgumentException("pdftotext was interrupted", exception)
        }
        if (!finished) {
            process.destroyForcibly()
            outputFuture.cancel(true)
            throw IllegalArgumentException("pdftotext timed out after $PDF_TEXT_TIMEOUT_SECONDS seconds")
        }

        val output = awaitProcessOutput(outputFuture)
        val exitCode = process.exitValue()
        require(exitCode == 0) {
            "pdftotext failed with exit code $exitCode: ${output.take(400)}"
        }

        return output
    }

    private fun awaitProcessOutput(outputFuture: CompletableFuture<String>): String {
        return try {
            outputFuture.get(1, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalArgumentException("pdftotext output read was interrupted", exception)
        } catch (exception: TimeoutException) {
            throw IllegalArgumentException("pdftotext output read timed out", exception)
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause is IllegalArgumentException) {
                throw cause
            }
            throw IllegalArgumentException("pdftotext output read failed", cause)
        }
    }

    private fun readProcessOutput(input: InputStream, maxBytes: Int): String {
        input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0

            while (true) {
                val read = source.read(buffer)
                if (read < 0) {
                    break
                }
                totalBytes += read
                require(totalBytes <= maxBytes) {
                    "pdftotext output exceeds $maxBytes bytes"
                }
                output.write(buffer, 0, read)
            }

            return String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    private fun chunkText(
        rawText: String,
        maxChunkChars: Int = 1_200,
        overlapChars: Int = 160,
        maxChunks: Int
    ): List<String> {
        val paragraphs = rawText
            .replace('\u0000', ' ')
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .split(Regex("\\n{2,}"))
            .map { it.lines().joinToString(" ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 80 }

        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (paragraph in paragraphs) {
            if (current.isNotEmpty() && current.length + paragraph.length + 1 > maxChunkChars) {
                chunks += current.toString().trim()
                if (chunks.size >= maxChunks) {
                    return chunks
                }
                val overlap = current.takeLast(overlapChars)
                current = StringBuilder(overlap.trim())
            }
            if (current.isNotEmpty()) {
                current.append('\n')
            }
            current.append(paragraph)
        }
        if (current.isNotBlank() && chunks.size < maxChunks) {
            chunks += current.toString().trim()
        }
        return chunks.take(maxChunks)
    }

    private fun Document.withSeedName(): Document {
        return Document(
            id,
            requireNotNull(text),
            metadata + ("seedName" to SEED_NAME)
        )
    }

    private fun seedDocumentId(sourceId: String, chunkIndex: Int): String {
        return UUID.nameUUIDFromBytes("$sourceId:$chunkIndex".toByteArray(StandardCharsets.UTF_8)).toString()
    }

    companion object {
        internal const val MAX_SEED_PDF_BYTES = 30L * 1024 * 1024
        private const val MAX_EXTRACTED_TEXT_BYTES = 5 * 1024 * 1024
        private const val PDF_TEXT_TIMEOUT_SECONDS = 20L
        private const val SEED_NAME = "local-rag-demo"
        private const val TECH_DOC_SOURCE_ID = "agri-tech-guide-7-medicinal-crops"
    }
}

data class DevRagSeedCommand(
    val pdfPath: String?,
    val resetIndex: Boolean,
    val includePdf: Boolean,
    val includeFarmingRecords: Boolean,
    val maxPdfChunks: Int
)

data class DevRagSeedResult(
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
    val workTypeIds: Map<String, UUID>,
    val recordIds: List<UUID>,
    val pdfChunksIndexed: Int,
    val farmingRecordChunksIndexed: Int,
    val embeddingModel: String
)

private data class ExtractedPdfSeed(
    val path: String,
    val text: String
)

private object SeedIds {
    val MEMBER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000042")
    val FARM_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000042")
    val CROP_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000042")
}

private object SeedPersona {
    const val email: String = "minseo.park@godsmove.local"
    const val phone: String = "010-4821-7390"
    const val name: String = "박민서"
    const val nickname: String = "평창민서"
    const val region: String = "강원특별자치도 평창군 진부면"
    const val experienceLevel: String = "3년차"
    const val managementType: String = "REGISTERED"
}

private object SeedFarm {
    const val name: String = "하늘들 약초농장"
    const val region: String = "강원특별자치도"
    const val city: String = "평창군"
    const val street: String = "진부면 솔바람길 24"
}

private object SeedCrop {
    const val name: String = "참당귀"
    const val category: String = "약용작물"
    const val lifecycleType: String = "PERENNIAL"
    const val defaultUnit: String = "주"
}

private object SeedRecords {
    val workTypes: Map<String, SeedWorkType> = listOf(
        SeedWorkType("transplant", UUID.fromString("30000000-0000-0000-0000-000000000001"), "정식"),
        SeedWorkType("irrigation", UUID.fromString("30000000-0000-0000-0000-000000000002"), "관수"),
        SeedWorkType("scouting", UUID.fromString("30000000-0000-0000-0000-000000000003"), "예찰"),
        SeedWorkType("pest_control", UUID.fromString("30000000-0000-0000-0000-000000000004"), "방제"),
        SeedWorkType("fertilizing", UUID.fromString("30000000-0000-0000-0000-000000000005"), "추비")
    ).associateBy { it.key }

    val records: List<SeedFarmingRecord> = listOf(
        SeedFarmingRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000001"),
            workedAt = LocalDateTime.of(2026, 6, 18, 8, 30),
            workType = workTypes.getValue("transplant"),
            memo = "참당귀 모종 1,200주를 두둑 간격 90cm, 포기 간격 25cm 기준으로 정식했다. 전날 만든 배수 고랑을 다시 정리했고, 정식 후에는 활착을 돕기 위해 점적호스로 40분간 물을 줬다."
        ),
        SeedFarmingRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000002"),
            workedAt = LocalDateTime.of(2026, 6, 22, 7, 50),
            workType = workTypes.getValue("irrigation"),
            memo = "사흘째 비가 없어 아침에 점적 관수 55분을 진행했다. 낮은 이랑 끝부분은 지난번 물 고임 흔적이 남아 있어 관수량을 줄였고, 북쪽 배수로에 낙엽이 쌓인 부분을 걷어냈다."
        ),
        SeedFarmingRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000003"),
            workedAt = LocalDateTime.of(2026, 6, 25, 16, 10),
            workType = workTypes.getValue("scouting"),
            memo = "동쪽 구역 하엽을 중심으로 황화와 작은 갈색 반점을 확인했다. 병반이 있는 17주는 표시끈으로 구분했고, 통풍이 나쁜 고랑 주변 잡초가 빠르게 올라와 다음 작업에서 제초가 필요하다."
        ),
        SeedFarmingRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000004"),
            workedAt = LocalDateTime.of(2026, 6, 28, 17, 20),
            workType = workTypes.getValue("pest_control"),
            memo = "예찰 때 표시한 17주 중 6주에서 반점이 조금 넓어져 등록 약제 라벨을 확인한 뒤 해질 무렵 부분 방제를 했다. 비 예보가 밤늦게로 밀려 살포 시간을 조정했고, 작업자는 장갑과 보안경을 착용했다."
        ),
        SeedFarmingRecord(
            id = UUID.fromString("40000000-0000-0000-0000-000000000005"),
            workedAt = LocalDateTime.of(2026, 6, 30, 9, 0),
            workType = workTypes.getValue("fertilizing"),
            memo = "잎색이 연한 중앙 구역에 완효성 추비를 기준량보다 적게 보충했다. 장마 전 과습을 줄이려고 남쪽 배수로를 깊게 정리했고, 방제한 구역은 새 잎 상태를 이틀 뒤 다시 보기로 했다."
        )
    )
}

private data class SeedWorkType(
    val key: String,
    val id: UUID,
    val name: String
)

private data class SeedFarmingRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    private val workType: SeedWorkType,
    val memo: String
) {
    val workTypeId: UUID = workType.id
    val workTypeName: String = workType.name
}
