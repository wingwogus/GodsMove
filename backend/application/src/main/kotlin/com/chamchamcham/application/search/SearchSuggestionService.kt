package com.chamchamcham.application.search

import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.pesticide.PesticideQueryRepository
import com.chamchamcham.domain.policy.PolicyProgramRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchSuggestionService(
    private val cropRepository: CropRepository,
    private val pesticideQueryRepository: PesticideQueryRepository,
    private val policyProgramRepository: PolicyProgramRepository,
) {
    fun suggest(keyword: String?): SearchSuggestionResult.Suggestions {
        val trimmed = keyword?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return SearchSuggestionResult.Suggestions(emptyList())
        }

        val sources = listOf(
            cropRepository.findTop9ByNameContainingIgnoreCaseOrderByNameAsc(trimmed)
                .map { it.name },
            pesticideQueryRepository.search(
                PesticideQueryRepository.SearchCondition(keyword = trimmed, cursor = null, size = MAX_RELATED)
            ).map { it.brandName },
            policyProgramRepository.findTitleSuggestions(trimmed, PageRequest.of(0, MAX_RELATED))
                .map { it.title },
            policyProgramRepository.findAgencySuggestions(trimmed, PageRequest.of(0, MAX_RELATED))
                .map { it.agencyName },
        )

        val related = collectRoundRobin(sources, normalize(trimmed))
        return SearchSuggestionResult.Suggestions(listOf(trimmed) + related)
    }

    private fun collectRoundRobin(sources: List<List<String>>, originalKey: String): List<String> {
        val cursors = IntArray(sources.size)
        val seen = hashSetOf(originalKey)
        val result = ArrayList<String>(MAX_RELATED)

        while (result.size < MAX_RELATED) {
            var advanced = false
            for (i in sources.indices) {
                if (result.size >= MAX_RELATED) break
                val source = sources[i]
                while (cursors[i] < source.size) {
                    val candidate = source[cursors[i]]
                    cursors[i]++
                    if (seen.add(normalize(candidate))) {
                        result.add(candidate)
                        advanced = true
                        break
                    }
                }
            }
            if (!advanced) break
        }
        return result
    }

    private fun normalize(value: String): String = value.trim().lowercase()

    companion object {
        private const val MAX_RELATED = 9
    }
}
