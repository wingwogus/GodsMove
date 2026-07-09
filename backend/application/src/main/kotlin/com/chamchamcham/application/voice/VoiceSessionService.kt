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
        val instructions = VoiceSessionInstructions.build(farmOptions, cropsByFarm, LocalDateTime.now())

        val providerResult = realtimeSessionProvider.createEphemeralSession(
            RealtimeSessionRequest(instructions = instructions, tools = listOf(tool))
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
        )
    }

    fun submitTurns(command: VoiceSessionCommand.SubmitTurns): VoiceSessionResult.Processed {
        val session = findSession(command.sessionId, command.memberId)

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

    private fun findMember(memberId: UUID): Member =
        memberRepository.findById(memberId).orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

    private fun findSession(sessionId: UUID, memberId: UUID): VoiceRecordSession =
        voiceRecordSessionRepository.findByIdAndMemberId(sessionId, memberId)
            ?: throw BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND)
}
