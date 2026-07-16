package com.chamchamcham.domain.member

import com.chamchamcham.domain.community.CommunityComment
import com.chamchamcham.domain.community.CommunityPost
import com.chamchamcham.domain.community.CommunityPostLike
import com.chamchamcham.domain.community.CommunityPostMedia
import com.chamchamcham.domain.community.CommunityPostType
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordMedia
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.legal.LegalDocument
import com.chamchamcham.domain.legal.MemberConsent
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.notification.NotificationPreference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.DatabaseMetaData
import java.time.LocalDateTime
import javax.sql.DataSource

@DataJpaTest
@ActiveProfiles("test")
class MemberHardDeleteSchemaTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    dataSource: DataSource
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val now = LocalDateTime.of(2026, 7, 16, 12, 0)

    @Test
    fun `member owned foreign keys declare required delete actions`() {
        val deleteRules = importedDeleteRules()

        expectedCascade.forEach { key ->
            assertThat(deleteRules[key])
                .describedAs("$key delete action")
                .isEqualTo(DatabaseMetaData.importedKeyCascade.toShort())
        }
        expectedSetNull.forEach { key ->
            assertThat(deleteRules[key])
                .describedAs("$key delete action")
                .isEqualTo(DatabaseMetaData.importedKeySetNull.toShort())
        }
        sharedReferences.forEach { key ->
            assertThat(deleteRules[key])
                .describedAs("$key must preserve shared rows")
                .isNotEqualTo(DatabaseMetaData.importedKeyCascade.toShort())
        }
    }

    @Test
    fun `physical member delete removes owned graph and preserves shared rows`() {
        val crop = entityManager.persist(
            Crop(
                externalNo = 422,
                name = "황기",
                usePartCategory = CropUsePartCategory.ROOT_BARK
            )
        )
        val legalDocument = entityManager.persist(
            LegalDocument(
                documentType = "TERMS",
                title = "이용약관",
                version = "1.0",
                body = "약관",
                publishedAt = now
            )
        )
        val member = entityManager.persist(
            Member(email = "withdraw@example.com", passwordHash = null)
        )
        val farm = entityManager.persist(
            Farm(
                owner = member,
                name = "탈퇴 테스트 농장",
                roadAddress = "충북 제천시 테스트로 1",
                boundaryCoordinates = mutableListOf(FarmBoundaryCoordinate(37.0, 128.0))
            )
        )
        entityManager.persist(MemberCrop(member = member, farm = farm, crop = crop))

        val record = entityManager.persist(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = WorkType.PLANTING,
                workedAt = now,
                weatherCondition = "맑음",
                weatherTemperature = 25,
                memo = "파종",
                entryMode = EntryMode.MANUAL
            )
        )
        entityManager.persist(
            PlantingRecord(
                record = record,
                plantingMethod = PlantingMethod.SEED
            )
        )

        val media = entityManager.persist(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = UploadedMediaUsageType.COMMUNITY_POST,
                fileUrl = "https://example.test/member-image.jpg",
                cloudinaryPublicId = "community/member-image",
                status = UploadedMediaStatus.ATTACHED
            )
        )
        member.updateProfileMedia(media)
        entityManager.persist(FarmingRecordMedia(record = record, uploadedMedia = media, displayOrder = 0))

        val post = entityManager.persist(
            CommunityPost(
                author = member,
                crop = crop,
                farmingRecord = record,
                postType = CommunityPostType.GENERAL,
                title = "탈퇴할 글",
                body = "본문"
            )
        )
        entityManager.persist(CommunityPostMedia(post = post, uploadedMedia = media, displayOrder = 0))
        val comment = entityManager.persist(
            CommunityComment(post = post, author = member, body = "댓글", media = media)
        )
        entityManager.persist(
            CommunityComment(post = post, parentComment = comment, author = member, body = "대댓글")
        )
        entityManager.persist(CommunityPostLike(post = post, member = member))
        entityManager.persist(
            ExternalIdentity(
                member = member,
                provider = AuthProvider.KAKAO,
                providerSubject = "withdraw-subject"
            )
        )
        entityManager.persist(
            MemberConsent(
                member = member,
                legalDocument = legalDocument,
                agreed = true,
                agreedAt = now
            )
        )
        entityManager.persist(
            NotificationPreference(
                member = member,
                channel = "PUSH",
                topic = "WEATHER"
            )
        )
        entityManager.flush()
        val memberId = requireNotNull(member.id)

        jdbcTemplate.update("delete from member where id = ?", memberId)
        entityManager.clear()

        ownedTables.forEach { table ->
            assertThat(rowCount(table))
                .describedAs("$table rows after member delete")
                .isZero()
        }
        assertThat(rowCount("crop")).isEqualTo(1)
        assertThat(rowCount("legal_document")).isEqualTo(1)
    }

    private fun importedDeleteRules(): Map<String, Short> {
        val tables = (expectedCascade + expectedSetNull + sharedReferences)
            .map { it.substringBefore('.') }
            .toSet()

        return jdbcTemplate.dataSource!!.connection.use { connection ->
            buildMap {
                tables.forEach { table ->
                    connection.metaData.getImportedKeys(
                        connection.catalog,
                        connection.schema,
                        table.lowercase()
                    ).use { keys ->
                        while (keys.next()) {
                            val key = "${keys.getString("FKTABLE_NAME")}.${keys.getString("FKCOLUMN_NAME")}".uppercase()
                            put(key, keys.getShort("DELETE_RULE"))
                        }
                    }
                }
            }
        }
    }

    private fun rowCount(table: String): Long =
        requireNotNull(jdbcTemplate.queryForObject("select count(*) from $table", Long::class.java))

    private companion object {
        val expectedCascade = setOf(
            "EXTERNAL_IDENTITY.MEMBER_ID",
            "MEMBER_CONSENT.MEMBER_ID",
            "NOTIFICATION_PREFERENCE.MEMBER_ID",
            "FARM.OWNER_MEMBER_ID",
            "MEMBER_CROP.MEMBER_ID",
            "FARMING_RECORD.MEMBER_ID",
            "FARMING_RECORD.FARM_ID",
            "FARMING_CYCLE_REPORT.MEMBER_ID",
            "FARMING_CYCLE_REPORT.FARM_ID",
            "RECORD_FEEDBACK.MEMBER_ID",
            "REPORT_FEEDBACK.MEMBER_ID",
            "POLICY_RECOMMENDATION.MEMBER_ID",
            "VOICE_RECORD_SESSION.MEMBER_ID",
            "COMMUNITY_POST.AUTHOR_MEMBER_ID",
            "COMMUNITY_COMMENT.AUTHOR_MEMBER_ID",
            "COMMUNITY_POST_LIKE.MEMBER_ID",
            "UPLOADED_MEDIA.OWNER_MEMBER_ID",
            "FARM_BOUNDARY_COORDINATE.FARM_ID",
            "MEMBER_CROP.FARM_ID",
            "PLANTING_RECORD.RECORD_ID",
            "WATERING_RECORD.RECORD_ID",
            "WEEDING_RECORD.RECORD_ID",
            "FERTILIZING_RECORD.RECORD_ID",
            "PEST_CONTROL_RECORD.RECORD_ID",
            "HARVEST_RECORD.RECORD_ID",
            "FARMING_RECORD_MEDIA.RECORD_ID",
            "RECORD_FEEDBACK.RECORD_ID",
            "RECORD_FEEDBACK_NEXT_ACTION.RECORD_FEEDBACK_ID",
            "REPORT_FEEDBACK.REPORT_ID",
            "REPORT_FEEDBACK_ITEM.REPORT_FEEDBACK_ID",
            "VOICE_RECORD_TURN.SESSION_ID",
            "COMMUNITY_POST_MEDIA.POST_ID",
            "COMMUNITY_COMMENT.POST_ID",
            "COMMUNITY_POST_LIKE.POST_ID",
            "COMMUNITY_COMMENT.PARENT_COMMENT_ID",
            "COMMUNITY_POST_MEDIA.UPLOADED_MEDIA_ID",
            "FARMING_RECORD_MEDIA.UPLOADED_MEDIA_ID"
        )

        val expectedSetNull = setOf(
            "MEMBER.PROFILE_MEDIA_ID",
            "COMMUNITY_COMMENT.MEDIA_ID",
            "COMMUNITY_POST.FARMING_RECORD_ID",
            "VOICE_RECORD_SESSION.DRAFT_RECORD_ID",
            "FARMING_CYCLE_REPORT.FINAL_HARVEST_RECORD_ID"
        )

        val sharedReferences = setOf(
            "COMMUNITY_POST.CROP_ID",
            "MEMBER_CONSENT.LEGAL_DOCUMENT_ID",
            "POLICY_RECOMMENDATION.POLICY_PROGRAM_ID"
        )

        val ownedTables = setOf(
            "member",
            "external_identity",
            "member_consent",
            "notification_preference",
            "farm",
            "farm_boundary_coordinate",
            "member_crop",
            "farming_record",
            "planting_record",
            "farming_record_media",
            "uploaded_media",
            "community_post",
            "community_post_media",
            "community_comment",
            "community_post_like"
        )
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class MemberHardDeleteSchemaTestApplication
