package com.chamchamcham.application.pesticide

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideApplicationRepository
import com.chamchamcham.domain.pesticide.PesticideQueryRepository
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PesticideCatalogService(
    private val pesticideRepository: PesticideRepository,
    private val pesticideQueryRepository: PesticideQueryRepository,
    private val pesticideApplicationRepository: PesticideApplicationRepository,
    private val cursorCodec: OpaqueCursorCodec,
) {
    fun search(keyword: String?, cursor: String?, size: Int): PesticideResult.Page {
        if (size <= 0 || size == Int.MAX_VALUE) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val decodedCursor = decodeCursor(cursor)
        val rows = pesticideQueryRepository.search(
            PesticideQueryRepository.SearchCondition(
                keyword = keyword,
                cursor = decodedCursor,
                size = size + 1
            )
        )
        val visibleRows = rows.take(size)
        val nextCursor = if (rows.size > size) {
            visibleRows.lastOrNull()?.let(::encodeCursor)
        } else {
            null
        }
        return PesticideResult.Page(
            items = visibleRows.map(PesticideResult.Summary::from),
            nextCursor = nextCursor
        )
    }

    fun listPestsByPesticide(pesticideId: UUID): List<PesticideResult.PestSummary> {
        if (!pesticideRepository.existsById(pesticideId)) {
            throw BusinessException(ErrorCode.PESTICIDE_NOT_FOUND)
        }
        return pesticideApplicationRepository.findDistinctPestsByPesticideId(pesticideId)
            .map(PesticideResult.PestSummary::from)
    }

    private fun decodeCursor(cursor: String?): PesticideQueryRepository.Cursor? {
        if (cursor.isNullOrBlank()) {
            return null
        }
        val payload = cursorCodec.decode(cursor, PesticideCursorPayload::class.java)
        return PesticideQueryRepository.Cursor(brandName = payload.brandName, id = payload.id)
    }

    private fun encodeCursor(pesticide: Pesticide): String =
        cursorCodec.encode(
            PesticideCursorPayload(
                brandName = pesticide.brandName,
                id = requireNotNull(pesticide.id) { "Persisted pesticide id is required" }
            )
        )
}
