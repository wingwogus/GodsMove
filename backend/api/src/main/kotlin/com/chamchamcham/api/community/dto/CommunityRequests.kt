package com.chamchamcham.api.community.dto

import com.chamchamcham.domain.community.CommunityPostType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

object CommunityRequests {
    data class SavePostRequest(
        @field:NotNull(message = "작물을 선택해주세요")
        val cropId: UUID?,

        @field:NotNull(message = "게시글 유형을 선택해주세요")
        val postType: CommunityPostType?,

        @field:NotBlank(message = "제목을 입력해주세요")
        @field:Size(max = 50, message = "제목은 50자 이하여야 합니다")
        val title: String,

        @field:NotBlank(message = "본문을 입력해주세요")
        val body: String,

        val farmingRecordId: UUID? = null,

        @field:Size(max = 5, message = "사진은 최대 5장까지 첨부할 수 있습니다")
        val mediaIds: List<UUID> = emptyList()
    )

    data class CreateCommentRequest(
        val parentCommentId: UUID? = null,

        @field:NotBlank(message = "댓글 내용을 입력해주세요")
        val body: String
    )
}
