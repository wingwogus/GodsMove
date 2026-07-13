package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.rag.common.RagAuditStatus
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContext
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationResult
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationService
import com.chamchamcham.domain.coaching.ReportFeedbackItemDraft
import com.chamchamcham.domain.coaching.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.ReportFeedbackStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReportFeedbackGenerationRequested) {
        val snapshot = feedbackRepository.findByReport_IdAndMember_Id(event.reportId, event.memberId)?.inputSnapshot ?: return
        val context = try {
            objectMapper.convertValue(snapshot, ReportFeedbackContext::class.java)
        } catch (_: RuntimeException) {
            return finishFailed(event, ReportFeedbackFailureCode.INVALID_CONTEXT_SNAPSHOT)
        }
        val result = try {
            generationService.generate(context)
        } catch (failure: ReportFeedbackGenerationFailure) {
            return finishFailed(event, failure.code)
        } catch (_: RuntimeException) {
            return finishFailed(event, ReportFeedbackFailureCode.UNEXPECTED)
        }
        finishReady(event, result)
    }

    private fun finishReady(event: ReportFeedbackGenerationRequested, result: ReportFeedbackGenerationResult) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (feedback.status != ReportFeedbackStatus.PENDING || feedback.report.id != event.reportId) return@executeWithoutResult
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

    private fun finishFailed(event: ReportFeedbackGenerationRequested, code: ReportFeedbackFailureCode) {
        writeTransaction.executeWithoutResult {
            val feedback = feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)
                ?: return@executeWithoutResult
            if (feedback.status == ReportFeedbackStatus.PENDING && feedback.report.id == event.reportId) {
                feedback.markFailed(code.name)
            }
        }
    }
}
