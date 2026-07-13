package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.common.RagAuditStatus
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContext
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationResult
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationService
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemDraft
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Component
class ReportFeedbackGenerationHandler(
    private val feedbackRepository: ReportFeedbackRepository,
    private val generationService: ReportFeedbackGenerationService,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @EventListener
    fun on(event: ReportFeedbackGenerationRequested) {
        val feedback = feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId) ?: return
        if (
            feedback.status != ReportFeedbackStatus.PENDING ||
            feedback.report.id != event.reportId ||
            feedback.workType != event.workType
        ) return
        val snapshot = feedback.inputSnapshot ?: return
        val context = try {
            objectMapper.convertValue(snapshot, ReportFeedbackContext::class.java)
        } catch (_: RuntimeException) {
            return finishFailed(event, snapshot, ReportFeedbackFailureCode.INVALID_CONTEXT_SNAPSHOT)
        }
        if (context.report.id != event.reportId || context.workType != event.workType) {
            return finishFailed(event, snapshot, ReportFeedbackFailureCode.INVALID_CONTEXT_SNAPSHOT)
        }
        val result = try {
            generationService.generate(context)
        } catch (failure: ReportFeedbackGenerationFailure) {
            return finishFailed(event, snapshot, failure.code)
        } catch (_: RuntimeException) {
            return finishFailed(event, snapshot, ReportFeedbackFailureCode.UNEXPECTED)
        }
        finishReady(event, snapshot, result)
    }

    private fun finishReady(
        event: ReportFeedbackGenerationRequested,
        expectedSnapshot: Map<String, Any?>,
        result: ReportFeedbackGenerationResult,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (
                feedback.status != ReportFeedbackStatus.PENDING ||
                feedback.report.id != event.reportId ||
                feedback.workType != event.workType ||
                feedback.inputSnapshot != expectedSnapshot
            ) return@executeWithoutResult
            feedback.markReady(
                summary = result.content.summary,
                items = result.content.items().map { ReportFeedbackItemDraft(it.section, it.item.basis, it.item.text) },
                citations = result.citations,
                auditStatus = if (result.auditWarnings.isEmpty()) RagAuditStatus.PASS.name else RagAuditStatus.WARN.name,
                auditWarnings = result.auditWarnings,
                modelName = result.modelInfo.chat,
                embeddingModel = result.modelInfo.embedding,
            )
        }
    }

    private fun finishFailed(
        event: ReportFeedbackGenerationRequested,
        expectedSnapshot: Map<String, Any?>,
        code: ReportFeedbackFailureCode,
    ) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (
                feedback.status == ReportFeedbackStatus.PENDING &&
                feedback.report.id == event.reportId &&
                feedback.workType == event.workType &&
                feedback.inputSnapshot == expectedSnapshot
            ) {
                feedback.markFailed(code.name)
            }
        }
    }
}
