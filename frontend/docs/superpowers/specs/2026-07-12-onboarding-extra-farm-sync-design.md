# Onboarding Extra Farm Sync Design

## Goal

Connect the standalone farm API to the existing multi-farm onboarding flow.
The first representative farm continues to be created by
`POST /api/v1/auth/onboarding/complete`; every additional farm collected during
onboarding is created with `POST /api/v1/farms` after onboarding completion.

This design extends
`docs/superpowers/plans/2026-07-11-onboarding-figma-implementation.md`. It does
not change the approved screen order or visual design.

## Sources Of Truth

- Deployed Swagger snapshot under `docs/swagger/`.
- `Business Rule.md` BR-USER-002 for representative-farm onboarding creation.
- `Business Rule.md` BR-USER-005 and BR-PERMISSION-002 for farm ownership.
- `Business Rule.md` BR-DATA-002 for deletion restrictions.
- The existing onboarding plan and implemented SwiftUI navigation.

## Navigation Contract

The completion-screen loop remains unchanged:

1. The user taps `재배지 추가하기` on `OnboardingCompleteView`.
2. `OnboardingViewModel.addFarmFromCompletion()` appends an empty
   `OnboardingFarmDraft`, selects it, and routes to `.farmLocation`.
3. The user completes `FarmLocationView`, continues through
   `CropSelectionView`, and returns to `OnboardingCompleteView`.
4. The loop may repeat for more farms.
5. No server write occurs until the user taps `시작하기`.

The existing navigation test that verifies completion-to-farm-location routing
remains a regression requirement.

## API Contract

The new farm API exposes authenticated CRUD operations:

- `GET /api/v1/farms`
- `POST /api/v1/farms`
- `PUT /api/v1/farms/{farmId}`
- `DELETE /api/v1/farms/{farmId}`

This onboarding change consumes only `POST /api/v1/farms`. The frontend data
layer may model all four endpoints because they share one focused endpoint type,
but it must not add farm-management UI before that UI has a concrete product
requirement.

`SaveFarmRequest` contains the farm draft fields plus `cropIds`. It does not
contain `farmId`. `FarmResponse` identifies the farm with `farmId` and includes
the resolved `crops` array. This differs from the previous frontend DTO, which
expected `id` and did not decode crops.

The frontend validates the constraints it already enforces in onboarding:

- farm name and road address are nonblank;
- latitude is between -90 and 90;
- longitude is between -180 and 180;
- area, when present, is greater than zero;
- one to five unique crop IDs are required.

The server remains authoritative and server validation errors continue through
the existing `APIError` mapping.

## Data-Layer Boundaries

`FarmDTOs.swift` separates the two request shapes instead of retaining one
ambiguous request type:

- `FarmDraftRequestDTO` maps the representative farm embedded in onboarding.
- `SaveFarmRequestDTO` maps standalone farm create/replace and owns `cropIds`.
- shared coordinate and data-source DTOs remain reusable.
- `FarmResponseDTO` decodes `farmId` and `crops`.

`FarmEndpoint` models the farm paths, methods, authentication requirement, and
request bodies. `FarmRepository` provides typed list/create/replace/delete
operations over `APIClient`. Onboarding depends on the repository protocol,
which permits deterministic submission tests without a real network session.

## Submission Flow

Tapping `시작하기` preserves the existing profile-photo behavior, then performs
these operations in order:

1. Upload the optional profile photo if necessary.
2. Build the onboarding-complete request from the first representative farm.
3. Call `POST /api/v1/auth/onboarding/complete` exactly once.
4. Save the returned member/onboarding state to the local profile cache.
5. Mark the member locally as onboarded so the completed server state is never
   treated as an incomplete onboarding on retry.
6. Enqueue farms at indexes `1...` as pending standalone farm writes.
7. Create pending farms sequentially with `POST /api/v1/farms`.
8. Remove each farm from the pending store immediately after its successful
   response.
9. Clear the onboarding draft after the server completion has been recorded;
   pending extra farms remain independently persisted until created.

Sequential creation is intentional. It gives every draft an unambiguous success
boundary and avoids losing which farms succeeded when only part of a parallel
batch fails.

## Offline And Partial-Failure Behavior

The representative farm and every additional farm are written to local draft
storage before any network request, preserving the existing offline-first input
flow. Pending extra farms move to a dedicated Codable pending store when the
representative onboarding request succeeds. The pending store removes items one
at a time only after server acknowledgement.

If an additional farm fails to upload:

- onboarding stays complete;
- the app may enter the main experience;
- already-created additional farms are not submitted again;
- failed and not-yet-attempted farms remain pending locally;
- a later authenticated app bootstrap retries the pending farms sequentially;
- network failure does not sign the member out or discard pending data.

This scope does not add a general-purpose sync engine. The pending-farm worker is
focused on the concrete onboarding handoff and uses the existing app bootstrap
as its retry trigger.

## Error Handling

Representative onboarding failure keeps the current completion-screen failure
state and retains the entire onboarding draft. No extra-farm request starts.

Extra-farm failure does not call onboarding completion again and does not roll
back successful server writes. The worker stops after the first failure so the
pending queue remains ordered and can be retried later. Because the member has
already completed onboarding, this background failure must not replace the main
screen with the onboarding flow.

## Testing

Swift Testing coverage will verify:

- standalone farm request JSON matches `SaveFarmRequest`;
- farm response JSON decodes `farmId`, coordinates, data sources, and crops;
- farm endpoint paths, methods, authentication, and bodies are correct;
- `재배지 추가하기` still creates a new active draft and routes to
  `FarmLocationView`;
- the first farm is sent only through onboarding completion;
- additional farms are created in draft order after onboarding completion;
- successful pending farms are removed individually;
- a partial failure retains only failed and unattempted farms;
- retry does not repeat onboarding completion or already-created farms;
- one farm preserves the existing onboarding behavior without a farm-create
  request.

The final verification runs the targeted onboarding/network tests, the full app
test or build command available for the installed simulator, Swagger drift
check, and `git diff --check`.

## Non-Goals

- Farm management screens for listing, editing, or deleting farms.
- A general offline synchronization framework.
- Changes to onboarding screen visuals or design-system foundations.
- Expanding the backend onboarding request from one `farm` to `farms[]`.
