package com.godsmove.application.exception

enum class ErrorCode(
    val code: String,
    val messageKey: String,
    val status: Int
) {

    INVALID_INPUT("COMMON_001", "error.invalid_input", 400),
    INVALID_JSON("COMMON_002", "error.invalid_json", 400),
    INTERNAL_ERROR("COMMON_999", "error.internal_error", 500),
    RESOURCE_NOT_FOUND("RESOURCE_001","error.resource_not_found", 404),
    MEMBER_NOT_FOUND("MEMBER_001", "error.member_not_found", 404),
    USER_ALREADY_EXISTS("USER_002", "error.user_already_exists", 409),
    DUPLICATE_EMAIL("AUTH_003", "error.duplicate_email", 409),
    EMAIL_NOT_VERIFIED("AUTH_004", "error.email_not_verified", 400),
    AUTH_CODE_NOT_FOUND("AUTH_005", "error.auth_code_not_found", 404),
    AUTH_CODE_MISMATCH("AUTH_006", "error.auth_code_mismatch", 400),
    ALREADY_LOGGED_OUT("AUTH_007", "error.already_logged_out", 400),
    MALFORMED_JWT("AUTH_008", "error.malformed_jwt", 400),
    INVALID_KAKAO_TOKEN("AUTH_009", "error.invalid_kakao_token", 401),
    KAKAO_NONCE_MISMATCH("AUTH_010", "error.kakao_nonce_mismatch", 401),
    KAKAO_NONCE_REPLAY("AUTH_011", "error.kakao_nonce_replay", 401),
    KAKAO_VERIFIED_EMAIL_REQUIRED("AUTH_012", "error.kakao_verified_email_required", 422),
    SOCIAL_ONLY_MEMBER_LOCAL_LOGIN_FORBIDDEN("AUTH_013", "error.social_only_member_local_login_forbidden", 400),
    KAKAO_OIDC_UNAVAILABLE("AUTH_014", "error.kakao_oidc_unavailable", 503),
    RAG_INVALID_REQUEST("RAG_001", "error.rag_invalid_request", 400),
    RAG_EMBEDDING_UNAVAILABLE("RAG_002", "error.rag_embedding_unavailable", 503),
    RAG_CHAT_UNAVAILABLE("RAG_003", "error.rag_chat_unavailable", 503),
    RAG_INDEX_UNAVAILABLE("RAG_004", "error.rag_index_unavailable", 503),
    RAG_EMBEDDING_DIMENSION_MISMATCH("RAG_005", "error.rag_embedding_dimension_mismatch", 500),
    UNAUTHORIZED("AUTH_001", "error.unauthorized", 401),
    FORBIDDEN("AUTH_002", "error.forbidden", 403),

}
