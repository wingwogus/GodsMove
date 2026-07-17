package com.chamchamcham.application.voice

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.pesticide.PesticideQueryRepository
import com.chamchamcham.domain.voice.VoiceRecordSession
import com.chamchamcham.domain.voice.VoiceRecordTurn
import com.chamchamcham.domain.voice.VoiceRecordSessionRepository
import com.chamchamcham.domain.voice.VoiceRecordTurnRepository
import com.chamchamcham.domain.voice.VoiceSessionStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class VoiceSessionService(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val farmingRecordService: FarmingRecordService,
    private val voiceRecordSessionRepository: VoiceRecordSessionRepository,
    private val voiceRecordTurnRepository: VoiceRecordTurnRepository,
    private val realtimeSessionProvider: RealtimeSessionProvider,
    private val voiceSessionProperties: VoiceSessionProperties,
    private val pesticideQueryRepository: PesticideQueryRepository,
) {
    fun create(command: VoiceSessionCommand.Create): VoiceSessionResult.Created {
        val member = findMember(command.memberId)

        val farmOptions = farmRepository.findByOwnerId(command.memberId)
            .map { FarmOption(farmId = requireNotNull(it.id), name = it.name) }

        val cropsByFarm: Map<String, List<CropOption>> = memberCropRepository.findByMemberId(command.memberId)
            .groupBy { it.farm.id.toString() }
            .mapValues { (_, memberCrops) ->
                memberCrops.map { CropOption(cropId = requireNotNull(it.crop.id), name = it.crop.name) }
                    .distinctBy { it.cropId }
            }

        val tool = FarmingRecordVoiceToolSchema.build(farmOptions, cropsByFarm)
        val instructions = VoiceSessionInstructions.build(
            farms = farmOptions,
            cropsByFarm = cropsByFarm,
            pesticides = loadPesticideOptions(cropsByFarm),
            now = LocalDateTime.now(),
            maxRounds = voiceSessionProperties.maxRounds,
            maxDurationSeconds = voiceSessionProperties.maxDurationSeconds,
        )

        val providerResult = realtimeSessionProvider.createEphemeralSession(
            RealtimeSessionRequest(
                instructions = instructions,
                tools = listOf(tool),
                expiresAfterSeconds = voiceSessionProperties.maxDurationSeconds + EXPIRY_BUFFER_SECONDS,
            )
        )

        val session = voiceRecordSessionRepository.save(
            VoiceRecordSession(member = member, status = VoiceSessionStatus.CREATED)
        )

        return VoiceSessionResult.Created(
            sessionId = requireNotNull(session.id),
            clientSecret = providerResult.clientSecret,
            expiresAt = providerResult.expiresAt,
            model = providerResult.model,
            farms = farmOptions,
            cropsByFarm = cropsByFarm,
            maxRounds = voiceSessionProperties.maxRounds,
            maxDurationSeconds = voiceSessionProperties.maxDurationSeconds,
        )
    }

    fun submitTurns(command: VoiceSessionCommand.SubmitTurns): VoiceSessionResult.Processed {
        val session = findSession(command.sessionId, command.memberId)
        if (session.status != VoiceSessionStatus.CREATED) {
            throw BusinessException(ErrorCode.VOICE_SESSION_INVALID_STATE)
        }

        command.turns.forEach { turn ->
            voiceRecordTurnRepository.save(
                VoiceRecordTurn(
                    session = session,
                    role = turn.role,
                    content = turn.content,
                    extractedFields = turn.extractedFields,
                )
            )
        }

        val transcript = command.turns.joinToString("\n") { "${it.role}: ${it.content}" }
        session.markWaitingConfirmation(transcript)

        return VoiceSessionResult.Processed(
            sessionId = requireNotNull(session.id),
            status = session.status,
            candidate = command.candidate,
            missingFields = VoiceRecordCandidateAnalyzer.missingFields(command.candidate),
        )
    }

    fun confirm(command: VoiceSessionCommand.Confirm): VoiceSessionResult.Confirmed {
        val session = findSession(command.sessionId, command.memberId)
        if (session.status != VoiceSessionStatus.WAITING_CONFIRMATION) {
            throw BusinessException(ErrorCode.VOICE_SESSION_INVALID_STATE)
        }

        val createResult = farmingRecordService.create(command.record.copy(entryMode = EntryMode.VOICE))
        val record = farmingRecordRepository.findById(createResult.id)
            .orElseThrow { BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND) }

        session.confirm(record = record, confirmedAt = LocalDateTime.now())

        return VoiceSessionResult.Confirmed(
            sessionId = requireNotNull(session.id),
            recordId = createResult.id,
            workType = createResult.workType,
        )
    }

    fun cancel(command: VoiceSessionCommand.Cancel): VoiceSessionResult.Cancelled {
        val session = findSession(command.sessionId, command.memberId)
        if (session.status == VoiceSessionStatus.COMPLETED) {
            throw BusinessException(ErrorCode.VOICE_SESSION_INVALID_STATE)
        }

        session.cancel()

        return VoiceSessionResult.Cancelled(sessionId = requireNotNull(session.id), status = session.status)
    }

    /**
     * 대화 지침에 넣을 농약 목록. 회원 작물에 등록된 농약(작물 필터)을 우선하고, 매칭이 없으면
     * 전체 카탈로그가 충분히 작을 때만(로컬 시드 등) 이름만 주입한다. 둘 다 아니면 생략한다.
     */
    private fun loadPesticideOptions(cropsByFarm: Map<String, List<CropOption>>): List<VoicePesticideOption> {
        val cropNames = cropsByFarm.values.flatten().map { it.name }.distinct()
        val rows = if (cropNames.isEmpty()) emptyList() else {
            pesticideQueryRepository.findByCropNames(cropNames, MAX_PESTICIDE_CATALOG_ROWS)
        }
        if (rows.isNotEmpty()) {
            return rows.groupBy { it.itemName to it.brandName }
                .entries.take(MAX_PESTICIDE_OPTIONS)
                .map { (key, grouped) ->
                    VoicePesticideOption(
                        name = "${key.first}(${key.second})",
                        pests = grouped.map { it.pestName }.distinct().take(MAX_PESTS_PER_PESTICIDE),
                    )
                }
        }

        val smallCatalog = pesticideQueryRepository.search(
            PesticideQueryRepository.SearchCondition(keyword = null, cursor = null, size = MAX_PESTICIDE_OPTIONS + 1)
        )
        if (smallCatalog.isEmpty() || smallCatalog.size > MAX_PESTICIDE_OPTIONS) return emptyList()
        return smallCatalog.map { VoicePesticideOption(name = "${it.itemName}(${it.brandName})", pests = emptyList()) }
    }

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

    private fun findSession(sessionId: UUID, memberId: UUID): VoiceRecordSession =
        voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)
            ?: throw BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND)

    companion object {
        private const val MAX_PESTICIDE_CATALOG_ROWS = 200
        private const val MAX_PESTICIDE_OPTIONS = 50
        private const val MAX_PESTS_PER_PESTICIDE = 5

        /**
         * OpenAI client_secret 만료를 대화 시간 한도보다 살짝 길게(30초) 파생시켜, 두 값이
         * 서로 다른 설정으로 따로 관리되며 어긋나는 것(예: 대화시간>토큰수명)을 막는다.
         */
        private const val EXPIRY_BUFFER_SECONDS = 30
    }
}
