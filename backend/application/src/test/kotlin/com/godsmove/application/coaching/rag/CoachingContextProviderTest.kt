package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.crop.Crop
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.Farm
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecord
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.farming.WorkType
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CoachingContextProviderTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val farmId = UUID.fromString("10000000-0000-0000-0000-000000000042")
    private val cropId = UUID.fromString("20000000-0000-0000-0000-000000000042")
    private val recordId = UUID.fromString("40000000-0000-0000-0000-000000000001")

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var farmRepository: FarmRepository

    @Mock
    private lateinit var cropRepository: CropRepository

    @Mock
    private lateinit var farmingRecordRepository: FarmingRecordRepository

    private lateinit var provider: CoachingContextProvider

    @BeforeEach
    fun setUp() {
        provider = CoachingContextProvider(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository
        )
    }

    @Test
    fun `build rejects farm id outside current member ownership`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            provider.build(CoachingRagCommand(memberId = memberId, question = "농장 상태?", farmId = farmId))
        }

        assertEquals(ErrorCode.RAG_INVALID_REQUEST, exception.errorCode)
    }

    @Test
    fun `build rejects record id outside current member ownership`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            provider.build(CoachingRagCommand(memberId = memberId, question = "일지 상태?", recordId = recordId))
        }

        assertEquals(ErrorCode.RAG_INVALID_REQUEST, exception.errorCode)
    }

    @Test
    fun `build uses owner scoped farm and member scoped record context`() {
        val member = member()
        val farm = farm(member)
        val crop = crop()
        val record = record(member, farm, crop)

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(record)

        val context = provider.build(
            CoachingRagCommand(
                memberId = memberId,
                question = "관수 요약?",
                farmId = farmId,
                cropId = cropId,
                recordId = recordId,
                periodStart = LocalDate.parse("2026-06-01"),
                periodEnd = LocalDate.parse("2026-06-30")
            )
        )

        assertThat(context.text).contains("- 농장: 하늘들 약초농장 (강원특별자치도 평창군)")
        assertThat(context.text).contains("- 작물: 참당귀 / 약초 / 기본 단위 kg")
        assertThat(context.text).contains("- 기준 영농일지: 2026-06-22T07:50 관수")
        verify(farmRepository).findByIdAndOwner_Id(farmId, memberId)
        verify(farmingRecordRepository).findByIdAndMember_Id(recordId, memberId)
    }

    private fun member() = Member(
        id = memberId,
        email = "farmer@example.com",
        name = "박민서",
        region = "강원특별자치도 평창군 진부면",
        experienceLevel = "5년",
        passwordHash = null
    )

    private fun farm(owner: Member) = Farm(
        id = farmId,
        owner = owner,
        name = "하늘들 약초농장",
        region = "강원특별자치도",
        city = "평창군",
        street = "진부면"
    )

    private fun crop() = Crop(
        id = cropId,
        name = "참당귀",
        category = "약초",
        lifecycleType = "PERENNIAL",
        defaultUnit = "kg"
    )

    private fun record(member: Member, farm: Farm, crop: Crop) = FarmingRecord(
        id = recordId,
        member = member,
        farm = farm,
        crop = crop,
        workType = WorkType(
            id = UUID.fromString("30000000-0000-0000-0000-000000000002"),
            name = "관수"
        ),
        workedAt = LocalDateTime.of(2026, 6, 22, 7, 50),
        memo = "점적 관수를 진행했다.",
        entryMode = "MANUAL"
    )
}
