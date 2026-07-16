package com.chamchamcham.application.voice

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.voice.VoiceRecordSession
import com.chamchamcham.domain.voice.VoiceRecordSessionRepository
import com.chamchamcham.domain.voice.VoiceRecordTurnRepository
import com.chamchamcham.domain.voice.VoiceSessionStatus
import com.chamchamcham.domain.voice.VoiceTurnRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class VoiceSessionServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val sessionId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000401")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var farmingRecordService: FarmingRecordService
    @Mock private lateinit var voiceRecordSessionRepository: VoiceRecordSessionRepository
    @Mock private lateinit var voiceRecordTurnRepository: VoiceRecordTurnRepository

    // Mockito's any()/any(Class)는 non-null 파라미터를 가진 Kotlin 인터페이스 메서드 호출 시
    // 컴파일러가 삽입하는 null 체크에 걸려 NPE가 나므로, 검증이 필요 없는 이 협력자는 목 대신
    // 간단한 fake로 대체한다.
    private lateinit var realtimeSessionProvider: RealtimeSessionProvider

    private lateinit var service: VoiceSessionService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    @BeforeEach
    fun setUp() {
        realtimeSessionProvider = object : RealtimeSessionProvider {
            override fun createEphemeralSession(request: RealtimeSessionRequest): RealtimeSessionResult =
                RealtimeSessionResult(clientSecret = "secret", expiresAt = LocalDateTime.now(), model = "gpt-realtime")
        }
        service = VoiceSessionService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            memberCropRepository = memberCropRepository,
            farmingRecordRepository = farmingRecordRepository,
            farmingRecordService = farmingRecordService,
            voiceRecordSessionRepository = voiceRecordSessionRepository,
            voiceRecordTurnRepository = voiceRecordTurnRepository,
            realtimeSessionProvider = realtimeSessionProvider,
            voiceSessionProperties = VoiceSessionProperties(maxRounds = 10, maxDurationSeconds = 300),
        )
        member = Member(id = memberId, email = "$memberId@example.com", passwordHash = null)
        farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
        crop = Crop(id = cropId, externalNo = cropId.hashCode(), name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
    }

    @Test
    fun `세션 생성 시 회원의 농지-작물 정보를 함께 반환한다`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(listOf(MemberCrop(member = member, farm = farm, crop = crop)))
        `when`(voiceRecordSessionRepository.save(any(VoiceRecordSession::class.java))).thenAnswer { invocation ->
            val toSave = invocation.arguments[0] as VoiceRecordSession
            VoiceRecordSession(id = sessionId, member = toSave.member, status = toSave.status)
        }

        val result = service.create(VoiceSessionCommand.Create(memberId = memberId))

        assertThat(result.sessionId).isEqualTo(sessionId)
        assertThat(result.clientSecret).isEqualTo("secret")
        assertThat(result.farms).extracting("farmId").containsExactly(farmId)
        assertThat(result.cropsByFarm[farmId.toString()]).extracting("cropId").containsExactly(cropId)
        assertThat(result.maxRounds).isEqualTo(10)
        assertThat(result.maxDurationSeconds).isEqualTo(300)
    }

    @Test
    fun `대화 턴 제출 시 세션은 확인대기 상태가 되고 누락필드를 계산한다`() {
        val session = VoiceRecordSession(id = sessionId, member = member, status = VoiceSessionStatus.CREATED)
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(session)

        val result = service.submitTurns(
            VoiceSessionCommand.SubmitTurns(
                memberId = memberId,
                sessionId = sessionId,
                turns = listOf(VoiceSessionCommand.TurnInput(role = VoiceTurnRole.USER, content = "어제 물 줬어요")),
                candidate = VoiceRecordCandidate(workType = WorkType.WATERING),
            )
        )

        assertThat(result.status).isEqualTo(VoiceSessionStatus.WAITING_CONFIRMATION)
        assertThat(result.missingFields).containsExactlyInAnyOrder("farmId", "cropId", "workedAt")
        assertThat(session.status).isEqualTo(VoiceSessionStatus.WAITING_CONFIRMATION)
    }

    @Test
    fun `확인대기 상태의 세션을 승인하면 영농일지를 생성하고 세션을 완료 처리한다`() {
        val session = VoiceRecordSession(id = sessionId, member = member, status = VoiceSessionStatus.WAITING_CONFIRMATION)
        val record = FarmingRecord(
            id = recordId,
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.WATERING,
            workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "물 줬어요",
            entryMode = EntryMode.VOICE,
        )
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(session)
        `when`(farmingRecordService.create(createCommand()))
            .thenReturn(FarmingRecordResult.RecordId(id = recordId, workType = WorkType.WATERING))
        `when`(farmingRecordRepository.findById(recordId)).thenReturn(Optional.of(record))

        val result = service.confirm(
            VoiceSessionCommand.Confirm(
                memberId = memberId,
                sessionId = sessionId,
                record = createCommand(),
            )
        )

        assertThat(result.recordId).isEqualTo(recordId)
        assertThat(session.status).isEqualTo(VoiceSessionStatus.COMPLETED)
        assertThat(session.draftRecord).isEqualTo(record)
    }

    @Test
    fun `생성 상태가 아닌 세션에 대화 턴을 제출하려 하면 예외를 던진다`() {
        val session = VoiceRecordSession(id = sessionId, member = member, status = VoiceSessionStatus.WAITING_CONFIRMATION)
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(session)

        val exception = assertThrows(BusinessException::class.java) {
            service.submitTurns(
                VoiceSessionCommand.SubmitTurns(
                    memberId = memberId,
                    sessionId = sessionId,
                    turns = listOf(VoiceSessionCommand.TurnInput(role = VoiceTurnRole.USER, content = "어제 물 줬어요")),
                    candidate = VoiceRecordCandidate(workType = WorkType.WATERING),
                )
            )
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_INVALID_STATE)
    }

    @Test
    fun `확인대기 상태가 아닌 세션을 승인하려 하면 예외를 던진다`() {
        val session = VoiceRecordSession(id = sessionId, member = member, status = VoiceSessionStatus.CREATED)
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(session)

        val exception = assertThrows(BusinessException::class.java) {
            service.confirm(VoiceSessionCommand.Confirm(memberId = memberId, sessionId = sessionId, record = createCommand()))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_INVALID_STATE)
    }

    @Test
    fun `완료된 세션은 취소할 수 없다`() {
        val session = VoiceRecordSession(id = sessionId, member = member, status = VoiceSessionStatus.COMPLETED)
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(session)

        val exception = assertThrows(BusinessException::class.java) {
            service.cancel(VoiceSessionCommand.Cancel(memberId = memberId, sessionId = sessionId))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_INVALID_STATE)
    }

    @Test
    fun `존재하지 않거나 남의 세션이면 조회 시 예외를 던진다`() {
        `when`(voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.cancel(VoiceSessionCommand.Cancel(memberId = memberId, sessionId = sessionId))
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.VOICE_SESSION_NOT_FOUND)
    }

    private fun createCommand() = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 20,
        memo = "물 줬어요",
        entryMode = EntryMode.VOICE,
    )
}
