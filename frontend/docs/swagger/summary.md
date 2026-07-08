# Swagger API Summary

- Source title: `ChamChamCham API`
- Version: `v1`
- SHA-256: `4ff125c8b559e9453bea31f17ecf14d647d603ce1d3d983cbca66db35307ccd2`
- Paths: `25`
- Operations: `29`
- Schemas: `59`

## Operations

| Method | Path | operationId |
| --- | --- | --- |
| POST | `/api/v1/auth/apple/login` | `appleLogin` |
| POST | `/api/v1/auth/email/send-code` | `sendVerificationCode` |
| POST | `/api/v1/auth/email/verify-code` | `verifyEmailCode` |
| POST | `/api/v1/auth/kakao/login` | `kakaoLogin` |
| POST | `/api/v1/auth/login` | `login` |
| POST | `/api/v1/auth/logout` | `logout` |
| POST | `/api/v1/auth/naver/login` | `naverLogin` |
| POST | `/api/v1/auth/onboarding/complete` | `completeOnboarding` |
| POST | `/api/v1/auth/reissue` | `reissue` |
| POST | `/api/v1/auth/signup` | `signUp` |
| GET | `/api/v1/community/boards` | `listBoards` |
| DELETE | `/api/v1/community/comments/{commentId}` | `deleteComment` |
| GET | `/api/v1/community/posts` | `listPosts` |
| POST | `/api/v1/community/posts` | `createPost` |
| DELETE | `/api/v1/community/posts/{postId}` | `deletePost` |
| GET | `/api/v1/community/posts/{postId}` | `getPost` |
| PATCH | `/api/v1/community/posts/{postId}` | `updatePost` |
| GET | `/api/v1/community/posts/{postId}/comments` | `listComments` |
| POST | `/api/v1/community/posts/{postId}/comments` | `createComment` |
| POST | `/api/v1/community/posts/{postId}/like-toggle` | `toggleLike` |
| GET | `/api/v1/crops` | `listCrops` |
| GET | `/api/v1/crops/categories` | `listCategories` |
| GET | `/api/v1/crops/categories/{category}/crops` | `listCropsByCategory` |
| POST | `/api/v1/media/images` | `uploadImage` |
| GET | `/api/v1/members/me` | `getMyProfile` |
| PUT | `/api/v1/members/me/profile` | `updateMyProfile` |
| GET | `/api/v1/members/{memberId}/profile` | `getPublicProfile` |
| GET | `/api/v1/test/me` | `getMyInfo` |
| GET | `/api/v1/test/ping` | `ping` |

## Schemas

- `ApiError`
- `ApiResponseCommentIdResponse`
- `ApiResponseCommentPageResponse`
- `ApiResponseLikeToggleResponse`
- `ApiResponseListBoardResponse`
- `ApiResponseListCategoryResponse`
- `ApiResponseListCropResponse`
- `ApiResponseLoginResponse`
- `ApiResponseMyProfileResponse`
- `ApiResponseOnboardingCompleteResponse`
- `ApiResponsePostDetailResponse`
- `ApiResponsePostIdResponse`
- `ApiResponsePostPageResponse`
- `ApiResponsePublicProfileResponse`
- `ApiResponseTokenResponse`
- `ApiResponseUnit`
- `ApiResponseUploadedImageResponse`
- `AppleLoginRequest`
- `AuthorResponse`
- `BoardResponse`
- `BoundaryCoordinateRequest`
- `CategoryResponse`
- `CommentIdResponse`
- `CommentPageResponse`
- `CommentResponse`
- `CompleteOnboardingRequest`
- `CreateCommentRequest`
- `CropProfileResponse`
- `CropResponse`
- `DataSourceRequest`
- `FarmBoundaryCoordinateResponse`
- `FarmDataSourceResponse`
- `FarmRequest`
- `FarmResponse`
- `KakaoLoginRequest`
- `LikeToggleResponse`
- `LoginRequest`
- `LoginResponse`
- `MemberProfileResponse`
- `MyFarmResponse`
- `MyProfileResponse`
- `NaverLoginRequest`
- `OnboardingCompleteResponse`
- `OnboardingResponse`
- `PostDetailResponse`
- `PostIdResponse`
- `PostPageResponse`
- `PostSummaryResponse`
- `PublicFarmResponse`
- `PublicProfileResponse`
- `ReissueRequest`
- `SavePostRequest`
- `SendVerificationCodeRequest`
- `SignUpRequest`
- `TokenResponse`
- `UpdateMyProfileRequest`
- `UploadImageRequest`
- `UploadedImageResponse`
- `VerifyEmailCodeRequest`
