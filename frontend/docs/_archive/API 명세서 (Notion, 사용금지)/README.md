<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# API 명세서

---

> 단축키:
`[[ <페이지 이름>`: DTO(페이지 불러오기)


| 이름 | Index | Response | Method | API Path | Request |
| --- | --- | --- | --- | --- | --- |
| [카카오 로그인](카카오-로그인.md) | AUTH-001 | LoginResponse ([LoginResponse](LoginResponse.md)) | POST | /api/v1/auth/kakao/login | KakaoLoginRequest ([KakaoLoginRequest](KakaoLoginRequest.md)) |
| [애플 로그인](애플-로그인.md) | AUTH-002 | LoginResponse ([LoginResponse](LoginResponse.md)) | POST | /api/v1/auth/apple/login | AppleLoginRequest ([AppleLoginRequest](AppleLoginRequest.md)) |
| [네이버 로그인](네이버-로그인.md) | AUTH-003 | LoginResponse ([LoginResponse](LoginResponse.md)) | POST | /api/v1/auth/naver/login | NaverLoginRequest ([NaverLoginRequest](NaverLoginRequest.md)) |
| [온보딩 완료](온보딩-완료.md) | AUTH-004 | OnboardingCompleteResponse([OnboardingCompleteResponse](OnboardingCompleteResponse.md)) | POST | /api/v1/auth/onboarding/complete | OnboardingCompleteRequest([OnboardingCompleteRequest](OnboardingCompleteRequest.md)) |
| [로그아웃](로그아웃.md) | AUTH-005 | Empty success response | POST | /api/v1/auth/logout | None |
| [토큰 재발급](토큰-재발급.md) | AUTH-006 | TokenResponse ([TokenResponse](TokenResponse.md)) | POST | /api/v1/auth/reissue | ReissueRequest ([ReissueRequest](ReissueRequest.md)) or refreshToken cookie |
| [작물 전체 목록 조회](작물-전체-목록-조회.md) | CROP-001 | [CropListResponse](CropListResponse.md)  | GET | /api/v1/crops | None |
| [작물 이용부위 카테고리 목록 조회](작물-이용부위-카테고리-목록-조회.md) | CROP-002 | [CropCategoryListResponse](CropCategoryListResponse.md)  | GET | /api/v1/crops/categories | None |
| [작물 카테고리별 목록 조회](작물-카테고리별-목록-조회.md) | CROP-003 | [CropListResponse](CropListResponse.md) | GET | /api/v1/crops/categories/{category}/crops |  |
| [내 프로필 조회](내-프로필-조회.md) | MEMBER-001 | MyMemberProfileResponse([MyMemberProfileResponse](MyMemberProfileResponse.md)) | GET | /api/v1/members/me | None |
| [회원 공개 프로필 조회](회원-공개-프로필-조회.md) | MEMBER-002 | PublicMemberProfileResponse([PublicMemberProfileResponse](PublicMemberProfileResponse.md)) | GET | /api/v1/members/{memberId}/profile | None |
| [커뮤니티 게시글 목록/검색](커뮤니티-게시글-목록／검색.md) | COMMUNITY-002 | CommunityPostListResponse([CommunityPostListResponse](CommunityPostListResponse.md)) | GET | /api/v1/community/posts | CommunityPostSearchRequest([CommunityPostSearchRequest](CommunityPostSearchRequest.md)) |
| [커뮤니티 게시글 좋아요 토글](커뮤니티-게시글-좋아요-토글.md) | COMMUNITY-010 | CommunityPostLikeToggleResponse([CommunityPostLikeToggleResponse](CommunityPostLikeToggleResponse.md)) | POST | /api/v1/community/posts/{postId}/like-toggle | None |
| [커뮤니티 댓글 삭제](커뮤니티-댓글-삭제.md) | COMMUNITY-009 | None | DELETE | /api/v1/community/comments/{commentId} | None |
| [커뮤니티 게시판 목록 조회](커뮤니티-게시판-목록-조회.md) | COMMUNITY-001 | CommunityBoardListResponse([CommunityBoardListResponse](CommunityBoardListResponse.md)) | GET | /api/v1/community/boards | None |
| [커뮤니티 게시글 삭제](커뮤니티-게시글-삭제.md) | COMMUNITY-006 | None | DELETE | /api/v1/community/posts/{postId} | None |
| [이미지 업로드](이미지-업로드.md) | MEDIA-001 | MediaImageUploadResponse([MediaImageUploadResponse](MediaImageUploadResponse.md)) | POST | /api/v1/media/images | MediaImageUploadRequest([MediaImageUploadRequest](MediaImageUploadRequest.md)) |
| [커뮤니티 게시글 수정](커뮤니티-게시글-수정.md) | COMMUNITY-005 | CommunityPostIdResponse([CommunityPostIdResponse](CommunityPostIdResponse.md) ) | PATCH | /api/v1/community/posts/{postId} | CommunityPostUpdateRequest([CommunityPostUpdateRequest](CommunityPostUpdateRequest.md)) |
| [커뮤니티 댓글 목록 조회](커뮤니티-댓글-목록-조회.md) | COMMUNITY-007 | CommunityCommentListResponse([CommunityCommentListResponse](CommunityCommentListResponse.md)) | GET | /api/v1/community/posts/{postId}/comments | None |
| [커뮤니티 댓글 작성](커뮤니티-댓글-작성.md) | COMMUNITY-008 | CommunityCommentIdResponse([CommunityCommentIdResponse](CommunityCommentIdResponse.md) ) | POST | /api/v1/community/posts/{postId}/comments | CommunityCommentCreateRequest([CommunityCommentCreateRequest](CommunityCommentCreateRequest.md)) |
| [커뮤니티 게시글 작성](커뮤니티-게시글-작성.md) | COMMUNITY-003 | CommunityPostIdResponse([CommunityPostIdResponse](CommunityPostIdResponse.md) ) | POST | /api/v1/community/posts | CommunityPostCreateRequest([CommunityPostCreateRequest](CommunityPostCreateRequest.md)) |
| [커뮤니티 게시글 상세 조회](커뮤니티-게시글-상세-조회.md) | COMMUNITY-004 | CommunityPostResponse([CommunityPostResponse](CommunityPostResponse.md)) | GET | /api/v1/community/posts/{postId} | None |

<details><summary>DTO TABLE</summary>

| 이름 | API 분류 | 태그 |
| --- | --- | --- |
| [EmailRequest](EmailRequest.md) | API Response | [인증] 계정 |
| [SignUpRequest](SignUpRequest.md) | API Response | [인증] 계정 |
| [DTO Template](DTO-Template.md) | API Response | [인증] 계정 |
| [MemberProfileFarmResponse](MemberProfileFarmResponse.md) | API Response | [농장] 농지 |
| [MyMemberProfileResponse](MyMemberProfileResponse.md) | API Response | [회원] 유저 정보 |
| [MemberProfileCropResponse](MemberProfileCropResponse.md) | API Response | [작물] 작물 |
| [PublicMemberProfileResponse](PublicMemberProfileResponse.md) | API Response | [회원] 유저 정보 |
| [CommunityCommentIdResponse](CommunityCommentIdResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [CommunityPostIdResponse](CommunityPostIdResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [CommunityPostSearchRequest](CommunityPostSearchRequest.md) | API Request | [커뮤니티] 커뮤니티 |
| [MediaImageUploadResponse](MediaImageUploadResponse.md) | API Response | [공통] 공통 |
| [CommunityCommentListResponse](CommunityCommentListResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [CommunityPostLikeToggleResponse](CommunityPostLikeToggleResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [MediaImageUploadRequest](MediaImageUploadRequest.md) | API Request | [공통] 공통 |
| [CommunityPostListResponse](CommunityPostListResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [CommunityBoardListResponse](CommunityBoardListResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [FarmBoundaryCoordinateResponse](FarmBoundaryCoordinateResponse.md) | API Response | [농장] 농지 |
| [FarmDataSourceRequest](FarmDataSourceRequest.md) | API Request | [농장] 농지 |
| [CropCategoryListResponse](CropCategoryListResponse.md) | API Response | [작물] 작물 |
| [FarmBoundaryCoordinateRequest](FarmBoundaryCoordinateRequest.md) | API Request | [농장] 농지 |
| [CropListResponse](CropListResponse.md) | API Response | [작물] 작물 |
| [FarmDataSourceResponse](FarmDataSourceResponse.md) | API Response | [농장] 농지 |
| [CropCategoryResponse](CropCategoryResponse.md) | API Response | [작물] 작물 |
| [FarmRequest](FarmRequest.md) | API Request | [농장] 농지 |
| [VerifyEmailCodeRequest](VerifyEmailCodeRequest.md) | API Request | [인증] 계정 |
| [ReissueRequest](ReissueRequest.md) | API Request | [인증] 계정 |
| [SendVerificationCodeRequest](SendVerificationCodeRequest.md) | API Request | [인증] 계정 |
| [KakaoLoginRequest](KakaoLoginRequest.md) | API Request | [인증] 계정 |
| [OnboardingCompleteRequest](OnboardingCompleteRequest.md) | API Request | [회원] 유저 정보 |
| [LoginResponse](LoginResponse.md) | API Response | [인증] 계정 |
| [NaverLoginRequest](NaverLoginRequest.md) | API Request | [인증] 계정 |
| [OnboardingResponse](OnboardingResponse.md) | API Response | [인증] 계정 |
| [OnboardingCompleteResponse](OnboardingCompleteResponse.md) | API Response | [회원] 유저 정보 |
| [AppleLoginRequest](AppleLoginRequest.md) | API Request | [인증] 계정 |
| [MemberProfileResponse](MemberProfileResponse.md) | API Response | [회원] 유저 정보 |
| [CommunityPostResponse](CommunityPostResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [NotificationPreferenceResponse](NotificationPreferenceResponse.md) | API Response | [알림] 알림 |
| [PolicyProgramResponse](PolicyProgramResponse.md) | API Response | [정책] 정책 |
| [CommunityCommentResponse](CommunityCommentResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [PolicyRecommendationResponse](PolicyRecommendationResponse.md) | API Response | [정책] 정책 |
| [SearchResponse](SearchResponse.md) | API Response | [검색] 검색 |
| [LegalDocumentResponse](LegalDocumentResponse.md) | API Response | [약관] 약관 |
| [NotificationPreferenceUpdateRequest](NotificationPreferenceUpdateRequest.md) | API Request | [알림] 알림 |
| [CommunityPostUpdateRequest](CommunityPostUpdateRequest.md) | API Request | [커뮤니티] 커뮤니티 |
| [AcceptCommentResponse](AcceptCommentResponse.md) | API Response | [커뮤니티] 커뮤니티 |
| [UserConsentResponse](UserConsentResponse.md) | API Response | [약관] 약관 |
| [UserConsentRequest](UserConsentRequest.md) | API Request | [약관] 약관 |
| [CommunityPostCreateRequest](CommunityPostCreateRequest.md) | API Request | [커뮤니티] 커뮤니티 |
| [CommunityCommentCreateRequest](CommunityCommentCreateRequest.md) | API Request | [커뮤니티] 커뮤니티 |
| [[보류] CommunityCommentUpdateRequest 미사용]([보류]-CommunityCommentUpdateRequest-미사용.md) | API Request | [커뮤니티] 커뮤니티 |
| [RecordFieldValueRequest](RecordFieldValueRequest.md) | API Request | [영농일지] 기록 |
| [WorkTypeFieldResponse](WorkTypeFieldResponse.md) | API Response | [영농일지] 기록 |
| [RecordMediaUploadRequest](RecordMediaUploadRequest.md) | API Request | [영농일지] 기록 |
| [CoachingFeedbackResponse](CoachingFeedbackResponse.md) | API Response | [AI] 분석 |
| [FarmingRecordCreateRequest](FarmingRecordCreateRequest.md) | API Request | [영농일지] 기록 |
| [FarmingRecordResponse](FarmingRecordResponse.md) | API Response | [영농일지] 기록 |
| [FarmingRecordUpdateRequest](FarmingRecordUpdateRequest.md) | API Request | [영농일지] 기록 |
| [WorkTypeResponse](WorkTypeResponse.md) | API Response | [영농일지] 기록 |
| [VoiceSessionTranscriptUpdateRequest](VoiceSessionTranscriptUpdateRequest.md) | API Request | [음성] 세션 |
| [VoiceSessionConfirmRequest](VoiceSessionConfirmRequest.md) | API Request | [음성] 세션 |
| [AiParseCandidateResponse](AiParseCandidateResponse.md) | API Response | [AI] 분석 |
| [VoiceSessionTurnRequest](VoiceSessionTurnRequest.md) | API Request | [음성] 세션 |
| [FarmingReportResponse](FarmingReportResponse.md) | API Response | [리포트] 리포트 |
| [VoiceSessionCreateRequest](VoiceSessionCreateRequest.md) | API Request | [음성] 세션 |
| [RecordFieldValueResponse](RecordFieldValueResponse.md) | API Response | [영농일지] 기록 |
| [VoiceSessionResponse](VoiceSessionResponse.md) | API Response | [음성] 세션 |
| [RecordMediaResponse](RecordMediaResponse.md) | API Response | [영농일지] 기록 |
| [TokenResponse](TokenResponse.md) | API Response | [인증] 계정 |
| [UserProfileUpdateRequest](UserProfileUpdateRequest.md) | API Request | [회원] 유저 정보 |
| [UserProfileResponse](UserProfileResponse.md) | API Response | [회원] 유저 정보 |
| [FarmUpdateRequest](FarmUpdateRequest.md) | API Request | [농장] 농지 |
| [IdResponse](IdResponse.md) | API Response | [공통] 공통 |
| [FarmResponse](FarmResponse.md) | API Response | [농장] 농지 |
| [ApiErrorResponse](ApiErrorResponse.md) | API Response | [공통] 공통 |
| [FarmCreateRequest](FarmCreateRequest.md) | API Request | [농장] 농지 |
| [CropResponse](CropResponse.md) | API Response | [작물] 작물 |
| [PageResponse](PageResponse.md) | API Response | [공통] 공통 |
| [AuthLoginRequest](AuthLoginRequest.md) | API Request | [인증] 계정 |
| [WithdrawUserRequest](WithdrawUserRequest.md) | API Request | [회원] 유저 정보 |
| [UserCropResponse](UserCropResponse.md) | API Response | [작물] 작물 |
| [UserCropUpsertRequest](UserCropUpsertRequest.md) | API Request | [작물] 작물 |



</details>

- 📄 [음성 기록 세션 생성](음성-기록-세션-생성.md)
- 📄 [음성 세션 AI 구조화 실행](음성-세션-AI-구조화-실행.md)
- 📄 [음성 세션 저장 승인](음성-세션-저장-승인.md)
- 📄 [음성 대화 턴 추가](음성-대화-턴-추가.md)
- 📄 [음성 기록 세션 상세 조회](음성-기록-세션-상세-조회.md)
- 📄 [음성 전사문 갱신](음성-전사문-갱신.md)
- 📄 [음성 세션 취소](음성-세션-취소.md)
- 📄 [내 프로필 수정](내-프로필-수정.md)
- 📄 [회원탈퇴](회원탈퇴.md)
- 📄 [작업 종류 목록 조회](작업-종류-목록-조회.md)
- 📄 [작업 종류별 입력 필드 조회](작업-종류별-입력-필드-조회.md)
- 📄 [[보류] 내 재배 작물 수정 API 없음]([보류]-내-재배-작물-수정-API-없음.md)
- 📄 [[보류] 내 재배 작물 삭제 API 없음]([보류]-내-재배-작물-삭제-API-없음.md)
- 📄 [(제목 없음)](a9e9e2d9.md)
- 📄 [기본 DTO 구조](기본-DTO-구조.md)
