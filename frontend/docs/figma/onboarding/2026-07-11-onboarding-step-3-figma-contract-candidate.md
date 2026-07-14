# Step 3 Figma-Contract Candidate: Crop Selection

- 작성일: 2026-07-11
- Figma evidence: [Step 3 crop selection capture](2026-07-11-onboarding-step-3-crop-selection.md)
- Backend commit checked: `b75ba0e`
- Swagger snapshot SHA-256: `3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`
- Status: backend 오류로 확정하지 않는다. Figma 문구 또는 제품 규칙 확인 후 backend 요청 여부를 결정한다.

## Candidate 1: category count says 10, backend exposes 9

Figma note says:

`[전초, 뿌리·껍질, 뿌리줄기, 잎, 꽃, 열매·과실, 종자, 줄기·가지, 기타] 총 10개 입니다.`

Visible labels are 9. Latest Swagger and backend enum also expose 9 category codes.

Current backend enum:

| Code | Label |
| --- | --- |
| `WHOLE_HERB` | 전초 |
| `ROOT_BARK` | 뿌리·껍질 |
| `RHIZOME` | 뿌리줄기 |
| `LEAF` | 잎 |
| `FLOWER` | 꽃 |
| `FRUIT` | 열매/과실 |
| `SEED` | 종자 |
| `STEM_BRANCH` | 줄기/가지 |
| `UNKNOWN` | 기타 |

If product confirms that there must be 10 categories, backend needs a new
`CropUsePartCategory` enum value, seed mapping, category endpoint update,
Swagger update, and frontend category model update. The missing category label
and code are not identifiable from the current screenshot.

## Candidate 2: category label punctuation differs

Figma uses:

- `열매·과실`
- `줄기·가지`

Backend currently returns:

- `열매/과실`
- `줄기/가지`

If API labels are expected to be display-ready, backend should update labels to
match Figma. If labels are only semantic data, frontend can display a Figma
label override while keeping backend codes stable. The safer frontend design is
to keep `code` and `label` separately instead of filtering by label text.

## Candidate 3: maximum 5 crop selections is not in backend contract

Product decision on 2026-07-11: crop selection maximum is 5.

Figma evidence:

- TalkToFigma-selected frame `631:12568`: `작물은 최대 5개까지 선택 가능합니다.`
- TalkToFigma-selected frame `631:13342`: same `최대 5개` copy plus five selected chips

Earlier user-provided design-note screenshot said `작물명 3개 이하 선택 시`, but this
is superseded by the confirmed product decision above.

Current frontend has no maximum selection limit. Latest backend request only
requires `cropIds` to be non-empty.

Backend evidence:

- `AuthRequests.CompleteOnboardingRequest.cropIds`: `@field:NotEmpty`
- `OnboardingService.complete`: checks empty, deduplicates ids, loads crops
- Swagger `CompleteOnboardingRequest.cropIds`: array of UUID, no `maxItems`

Backend should add max-size validation and expose it in Swagger, for example
`@field:Size(max = 5)` and OpenAPI `maxItems: 5`. Frontend should enforce the
same rule before submission.

## Candidate 4: Step 3 progress fill matches Step 2

TalkToFigma-selected Step 3 frames `631:12568` and `631:13342` both have progress
active fill `176/350`, the same as Step 2. If the visible onboarding steps are
four total, Step 3 should likely show about `264/350` (75%).

This is probably a Figma frame state issue rather than backend contract work, but
it affects implementation because current frontend progress is already known to
be misaligned with Figma.

## Non-conflict: 가나다순 crop list

Figma asks for 가나다 order. Backend already returns crops ordered by
`name ASC, externalNo ASC` for both all-crop and category-crop lists. If runtime
Korean collation is visibly off, frontend can add local sort as a presentation
guard.
