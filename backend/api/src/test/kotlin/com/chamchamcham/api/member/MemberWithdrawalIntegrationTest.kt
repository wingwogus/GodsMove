package com.chamchamcham.api.member

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.media.ImageUploader
import com.chamchamcham.application.member.MemberProfileService
import com.chamchamcham.application.redis.RefreshTokenRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:member-withdrawal-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
class MemberWithdrawalIntegrationTest @Autowired constructor(
    private val memberProfileService: MemberProfileService,
    private val memberRepository: MemberRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
) {
    @MockBean
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockBean
    private lateinit var imageUploader: ImageUploader

    private lateinit var member: Member

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }

    @BeforeEach
    fun setUp() {
        uploadedMediaRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()

        member = memberRepository.save(Member(email = "withdrawal-member@example.com", passwordHash = null))
        val profileMedia = uploadedMediaRepository.save(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = UploadedMediaUsageType.PROFILE,
                fileUrl = "https://example.test/profile.jpg",
                cloudinaryPublicId = "profile/withdrawal-member",
            ),
        )
        member.updateProfileMedia(profileMedia)
        memberRepository.save(member)
    }

    @Test
    fun `withdraw removes a member with profile media`() {
        memberProfileService.withdraw(memberId)

        assertThat(memberRepository.existsById(memberId)).isFalse()
        assertThat(uploadedMediaRepository.count()).isZero()
    }
}
