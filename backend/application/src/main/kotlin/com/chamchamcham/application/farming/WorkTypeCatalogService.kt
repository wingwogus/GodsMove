package com.chamchamcham.application.farming

import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class WorkTypeCatalogService {
    fun listWorkTypes(): List<WorkTypeResult.WorkTypeSummary> =
        WorkType.entries.map { workType ->
            WorkTypeResult.WorkTypeSummary(
                code = workType.name,
                label = workType.label,
                detailRequired = workType.detailRequired,
                fields = WorkTypeFieldCatalog.fieldsFor(workType)
            )
        }
}
