# Swagger SwiftUI API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the iOS SwiftUI app's networking DTOs, repositories, and tests with the deployed Swagger contract at `https://chamchamcham.jaehyuns.com/v3/api-docs`.

**Architecture:** Keep the existing feature-first SwiftUI structure and the current `APIClient`/`Endpoint` envelope-based networking layer. Move DTOs that are shared by more than one feature into `Core/Networking/DTOs`, keep feature-specific DTOs inside `Features/<Feature>/Data/DTOs`, and add repository methods only where the app has a concrete consumer or contract drift would break existing flows.

**Tech Stack:** SwiftUI, Swift 6 strict concurrency, Observation, SwiftData, native `URLSession` with async/await, Swift Testing, OpenAPI/Swagger JSON captured on 2026-07-08.

## Global Constraints

- Preserve the domain term `member`; do not reintroduce project-owned `user` naming such as `userId` or `users`.
- iOS minimum deployment target remains iOS 17+.
- Swift 6 strict concurrency remains enabled; respect actor isolation.
- Use `@Observable` for view models, not Combine or `ObservableObject`.
- Use SwiftData for local persistence, not CoreData or Realm.
- Use native `URLSession` + async/await; do not add Alamofire or OpenAPI codegen for this MVP pass.
- Swift Package Manager only; do not introduce local SPM packages at MVP stage.
- Every new Swift file must include the standard Xcode header comment.
- Keep offline-first behavior for syncable product features; onboarding/auth are online-only exceptions.
- Do not hand-edit `frontend/docs/API 명세서/**` because those files are generated from Notion by `scripts/sync_api_spec.py`.
- Treat deployed Swagger JSON as the new API contract source for frontend integration; the Notion import remains historical until the team explicitly repairs that pipeline.

---

## Swagger Snapshot

Captured from `https://chamchamcham.jaehyuns.com/v3/api-docs` on 2026-07-08.

| Area | Operations in Swagger | Current iOS status |
| --- | --- | --- |
| Auth | Kakao, Apple, Naver social login; email/password signup/login; email code send/verify; logout; reissue; onboarding complete | Social login, logout, reissue, onboarding complete are wired. Email/password APIs are not surfaced in product UI. |
| Crops | List all crops, list categories, list crops by category | All crops and categories are wired. Category-specific crop list is not wired. |
| Media | JSON Base64 image upload | Profile and community post/comment upload are wired. `FARMING_RECORD` usage is not exposed yet. |
| Community | Boards, post list/search, create/detail/update/delete, like toggle, comment list/create/delete | Most endpoints are wired. `CommentResponse.replies` differs between generated docs and current Swagger. |
| Members | My profile, public profile, update my profile | Not wired as a dedicated repository. Login/onboarding cache only stores the compact member profile. |
| Test | Ping, authenticated member test | Keep out of production app code. Use only as optional debug diagnostics. |

Current Swagger exposes 25 paths, 29 operations, and 59 schemas including `ApiResponse*` envelope schemas. It does not expose Voice, Farming Record, Report, Policy, Notification, Search, Farm CRUD, or standalone MemberCrop APIs.

## Swagger Update Detection Strategy

The deployed `/v3/api-docs` response currently has no `ETag` or `Last-Modified` header and sets `cache-control: no-cache, no-store`. Use content hashing, not conditional HTTP requests.

Recommended setup:

1. Commit a canonical Swagger snapshot under `frontend/docs/swagger/openapi.json`.
2. Add a small script that fetches `/v3/api-docs`, sorts JSON keys recursively, writes the snapshot, and emits a SHA-256 hash plus a human-readable summary.
3. Add a GitHub Actions workflow that runs on:
   - backend deployment completion via `repository_dispatch` with type `swagger-updated` for near-immediate detection,
   - `workflow_dispatch` for manual checks,
   - a scheduled fallback such as every 30 minutes.
4. When the canonical snapshot changes, the workflow opens a PR named `docs(api): Swagger contract snapshot update` with the JSON diff and summary. That PR is the team's alert.
5. The Swift implementation tasks in this plan run after reviewing the PR diff, not blindly on every Swagger change.

Why PR instead of only Slack/email: the diff becomes reviewable, searchable, and tied to the exact contract version that broke or changed the app.

## Contract Drift To Resolve

1. `CommentResponse.replies`
   - Generated Notion docs and the existing Swift test expect `replies: []`.
   - Current Swagger `CommentResponse` has no `replies` property.
   - iOS should decode both shapes and map missing `replies` to `[]` so the app survives either backend shape.

2. `FarmRequest.dataSource`
   - Generated Notion docs say `dataSource` is optional.
   - Current Swagger marks `FarmRequest.required` as including `dataSource`.
   - iOS currently omits `dataSource`.
   - iOS should include a deterministic `dataSource` object from the existing JUSO/V-World onboarding flow.

3. `FarmResponse.dataSource`
   - Current Swift DTO intentionally ignores `dataSource`.
   - Swagger includes it and marks it required on `FarmResponse`.
   - Decode it as a DTO field even if the app does not render it yet.

4. `/api/v1/crops/categories/{category}/crops`
   - Swagger exposes category-specific filtering by `WHOLE_HERB`, `ROOT_BARK`, `RHIZOME`, `LEAF`, `FLOWER`, `FRUIT`, `SEED`, `STEM_BRANCH`, `UNKNOWN`.
   - Current iOS fetches all crops and filters locally.
   - Add the endpoint and repository method without changing the onboarding UI flow unless a later product task chooses server-side filtering.

5. Member profile endpoints
   - Swagger exposes `GET /members/me`, `GET /members/{memberId}/profile`, and `PUT /members/me/profile`.
   - Current iOS has no `MyPage` repository and only caches the compact login/onboarding `MemberProfileResponse`.
   - Add a small `MyPage` data/domain layer so the app has one canonical place to call profile APIs.

6. `ApiResponse<Unit>` handling
   - Swagger wraps empty success responses as `ApiResponseUnit`.
   - Current `APIClient` returns `EmptyDTO()` for any 2xx before checking the envelope.
   - Decode the envelope first for empty responses so `success=false` and `error` are still respected.

## File Structure

Create:

- `frontend/ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift`
  - Shared `FarmRequestDTO`, `FarmDataSourceDTO`, `FarmBoundaryCoordinateDTO`, `FarmResponseDTO`.
- `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Domain/MemberProfile.swift`
  - Domain models for my profile, public profile, compact farms, and crops.
- `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/DTOs/MemberProfileDTOs.swift`
  - DTOs for `MyProfileResponse`, `PublicProfileResponse`, `MyFarmResponse`, `PublicFarmResponse`, `CropProfileResponse`, and `UpdateMyProfileRequest`.
- `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/MemberEndpoint.swift`
  - Typed endpoints for member profile API paths.
- `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/MemberProfileRepository.swift`
  - Repository protocol and remote implementation for profile reads and updates.
- `frontend/ChamChamCham/ChamChamChamTests/APIClientEnvelopeTests.swift`
  - Stubbed URL protocol tests for envelope success/error behavior, including `EmptyDTO`.
- `frontend/ChamChamCham/ChamChamChamTests/MemberProfileDTOTests.swift`
  - DTO decoding/encoding tests for member profile APIs.
- `frontend/ChamChamCham/ChamChamChamTests/CropCategoryEndpointTests.swift`
  - Path/query tests for category-specific crop fetch.
- `frontend/docs/swagger/README.md`
  - Explains that Swagger is the active frontend API contract source.
- `frontend/docs/swagger/openapi.json`
  - Canonical sorted Swagger JSON snapshot.
- `frontend/docs/swagger/openapi.sha256`
  - Hash for quick local comparison.
- `frontend/docs/swagger/summary.md`
  - Generated endpoint/schema summary for human review.
- `frontend/scripts/sync_swagger_spec.py`
  - Fetches, canonicalizes, summarizes, and optionally checks Swagger drift.
- `.github/workflows/swagger-contract-watch.yml`
  - Scheduled/manual/deployment-triggered Swagger drift PR workflow.

Modify:

- `frontend/ChamChamCham/ChamChamCham/Core/Networking/APIClient.swift`
  - Decode `APIEnvelope<EmptyDTO>` before returning empty success.
- `frontend/ChamChamCham/ChamChamCham/Core/Networking/APIEnvelope.swift`
  - Keep `APIEnvelope<T>` shape; optionally decode `ApiError.detail` as ignored raw data only when required by tests.
- `frontend/ChamChamCham/ChamChamCham/App/DIContainer.swift`
  - Add `makeMemberProfileRepository()`.
- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`
  - Remove local farm DTO declarations after moving them to Core; include `dataSource`.
- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteResponseDTO.swift`
  - Use shared `FarmResponseDTO`.
- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropEndpoint.swift`
  - Add category-specific crop endpoint.
- `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropCatalogService.swift`
  - Add `fetchCrops(categoryCode:)`.
- `frontend/ChamChamCham/ChamChamCham/Core/Media/MediaImageDTOs.swift`
  - Add `farmingRecord = "FARMING_RECORD"` to `MediaImageUsage`.
- `frontend/ChamChamCham/ChamChamCham/Core/Media/MediaUploadRepository.swift`
  - Add `uploadFarmingRecordImage` only as a repository method; do not add UI usage in this plan.
- `frontend/ChamChamCham/ChamChamCham/Features/Community/Data/DTOs/CommunityResponseDTOs.swift`
  - Decode missing `replies` as an empty array.
- `frontend/ChamChamCham/ChamChamChamTests/CommunityResponseDecodingTests.swift`
  - Add Swagger-shaped comment JSON without `replies`.
- `frontend/ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift`
  - Assert `farm.dataSource` is encoded.
- `frontend/ChamChamCham/ChamChamChamTests/ImageDownscalerTests.swift`
  - No required change unless media upload tests need fixtures.

Do not modify:

- `frontend/docs/API 명세서/**`
  - These files are generated. Record Swagger drift in this plan and update the Notion source or generator separately.
- `frontend/AGENTS.md`
  - It contains stale backend-controller notes, but changing agent instructions should be a separate docs cleanup commit.

---

### Task 0: Swagger Contract Snapshot And Change Alerts

**Files:**
- Create: `frontend/docs/swagger/README.md`
- Create: `frontend/docs/swagger/openapi.json`
- Create: `frontend/docs/swagger/openapi.sha256`
- Create: `frontend/docs/swagger/summary.md`
- Create: `frontend/scripts/sync_swagger_spec.py`
- Create: `.github/workflows/swagger-contract-watch.yml`

**Interfaces:**
- Consumes: deployed Swagger JSON from `https://chamchamcham.jaehyuns.com/v3/api-docs`.
- Produces: committed canonical Swagger snapshot and PR-based alerts whenever the contract changes.

- [ ] **Step 1: Create the Swagger docs folder**

Create `frontend/docs/swagger/README.md`:

```markdown
# Swagger API Contract

This folder stores the frontend-facing API contract generated from the deployed Swagger endpoint.

- Source: `https://chamchamcham.jaehyuns.com/v3/api-docs`
- Canonical snapshot: `openapi.json`
- Snapshot hash: `openapi.sha256`
- Human summary: `summary.md`

The Notion-generated `frontend/docs/API 명세서/` folder is historical until that import pipeline is repaired.
Do not hand-edit `openapi.json`; run `python3 frontend/scripts/sync_swagger_spec.py --write`.
```

- [ ] **Step 2: Add the sync script**

Create `frontend/scripts/sync_swagger_spec.py`:

```python
#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen


DEFAULT_URL = "https://chamchamcham.jaehyuns.com/v3/api-docs"


def sort_json(value):
    if isinstance(value, dict):
        return {key: sort_json(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        return [sort_json(item) for item in value]
    return value


def load_remote(url):
    with urlopen(url, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def render_summary(spec, digest):
    paths = spec.get("paths", {})
    schemas = spec.get("components", {}).get("schemas", {})
    operations = []
    for path in sorted(paths):
        for method in sorted(paths[path]):
            operation = paths[path][method]
            operations.append((method.upper(), path, operation.get("operationId", "")))

    lines = [
        "# Swagger API Summary",
        "",
        f"- Source title: `{spec.get('info', {}).get('title', '')}`",
        f"- Version: `{spec.get('info', {}).get('version', '')}`",
        f"- SHA-256: `{digest}`",
        f"- Paths: `{len(paths)}`",
        f"- Operations: `{len(operations)}`",
        f"- Schemas: `{len(schemas)}`",
        "",
        "## Operations",
        "",
        "| Method | Path | operationId |",
        "| --- | --- | --- |",
    ]
    lines.extend(f"| {method} | `{path}` | `{operation_id}` |" for method, path, operation_id in operations)
    lines.extend(["", "## Schemas", ""])
    lines.extend(f"- `{name}`" for name in sorted(schemas))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--out", default="frontend/docs/swagger")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    out_dir = Path(args.out)
    spec = sort_json(load_remote(args.url))
    body = json.dumps(spec, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
    summary = render_summary(spec, digest)

    openapi_path = out_dir / "openapi.json"
    old_body = openapi_path.read_text(encoding="utf-8") if openapi_path.exists() else ""
    changed = old_body != body

    if args.write or args.check:
        out_dir.mkdir(parents=True, exist_ok=True)
        openapi_path.write_text(body, encoding="utf-8")
        (out_dir / "openapi.sha256").write_text(digest + "\n", encoding="utf-8")
        (out_dir / "summary.md").write_text(summary, encoding="utf-8")

    if args.check and changed:
        raise SystemExit(2)

    print(digest)


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Generate the first committed snapshot**

Run:

```bash
python3 frontend/scripts/sync_swagger_spec.py --write
```

Expected:

- `frontend/docs/swagger/openapi.json` exists.
- `frontend/docs/swagger/openapi.sha256` contains one SHA-256 line.
- `frontend/docs/swagger/summary.md` lists 25 paths, 29 operations, and 59 schemas for the 2026-07-08 Swagger state.

- [ ] **Step 4: Add the GitHub Actions watcher**

Create `.github/workflows/swagger-contract-watch.yml`:

```yaml
name: Swagger Contract Watch

on:
  workflow_dispatch:
  repository_dispatch:
    types: [swagger-updated]
  schedule:
    - cron: "*/30 * * * *"

permissions:
  contents: write
  pull-requests: write

jobs:
  check-swagger:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: "3.x"

      - name: Refresh Swagger snapshot
        run: python3 frontend/scripts/sync_swagger_spec.py --write

      - name: Detect changes
        id: diff
        run: |
          if git diff --quiet -- frontend/docs/swagger; then
            echo "changed=false" >> "$GITHUB_OUTPUT"
          else
            echo "changed=true" >> "$GITHUB_OUTPUT"
          fi

      - name: Open pull request
        if: steps.diff.outputs.changed == 'true'
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          branch="bot/swagger-contract-$(date -u +%Y%m%d%H%M%S)"
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git checkout -b "$branch"
          git add frontend/docs/swagger
          git commit -m "docs(api): Swagger contract snapshot update"
          git push origin "$branch"
          gh pr create \
            --title "docs(api): Swagger contract snapshot update" \
            --body "Swagger contract changed. Review \`frontend/docs/swagger/summary.md\` and the \`openapi.json\` diff, then update Swift DTOs/endpoints if needed." \
            --base dev \
            --head "$branch"
```

- [ ] **Step 5: Wire backend deployment to near-immediate detection**

In the backend deploy workflow, after the production deploy step succeeds, call:

```bash
gh api repos/${GITHUB_REPOSITORY}/dispatches \
  -f event_type=swagger-updated \
  -f client_payload[environment]=production
```

This requires the deploy workflow token to have permission to dispatch events in the same repository. If backend and frontend are split into separate repositories later, replace `${GITHUB_REPOSITORY}` with the frontend repository and use a fine-scoped PAT stored as a GitHub Actions secret.

- [ ] **Step 6: Verify the watcher locally**

Run:

```bash
python3 frontend/scripts/sync_swagger_spec.py --check
```

Expected: exit code `0` when the committed snapshot matches deployed Swagger. After manually changing `frontend/docs/swagger/openapi.json`, the same command exits with code `2`.

- [ ] **Step 7: Commit**

```bash
git add frontend/docs/swagger frontend/scripts/sync_swagger_spec.py .github/workflows/swagger-contract-watch.yml
git commit -m "chore(api): Swagger 계약 변경 감시 추가"
```

---

### Task 1: Envelope And Empty Response Contract

**Files:**
- Modify: `frontend/ChamChamCham/ChamChamCham/Core/Networking/APIClient.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/APIClientEnvelopeTests.swift`

**Interfaces:**
- Consumes: existing `APIClient.send<T>(_:)`, `APIEnvelope<T>`, `EmptyDTO`, `Endpoint`.
- Produces: `APIClient` behavior that honors `success` and `error` for `ApiResponseUnit` responses.

- [ ] **Step 1: Add a failing test for `ApiResponseUnit` error envelopes**

Create `APIClientEnvelopeTests.swift` with a local stub endpoint and URL protocol. The key assertion is that a 200 response with `success=false` throws `APIError.apiError`, not `EmptyDTO()`.

```swift
//
//  APIClientEnvelopeTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("APIClient envelope decoding")
struct APIClientEnvelopeTests {
    @Test("EmptyDTO still decodes the ApiResponse envelope")
    func emptyDTOHonorsEnvelopeError() async throws {
        let session = URLSession.stubbed(
            statusCode: 200,
            body: #"{"success":false,"data":null,"error":{"code":"AUTH_REQUIRED","message":"로그인이 필요합니다"}}"#
        )
        let tokenStore = AuthTokenStore()
        let client = APIClient(
            authTokenStore: tokenStore,
            tokenRefreshCoordinator: TokenRefreshCoordinator(authTokenStore: tokenStore, session: session),
            session: session
        )

        await #expect(throws: APIError.self) {
            let _: EmptyDTO = try await client.send(StubEndpoint(path: "api/v1/auth/logout", method: .post))
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/APIClientEnvelopeTests
```

Expected: fail because `APIClient` returns `EmptyDTO()` before decoding `APIEnvelope<EmptyDTO>`.

- [ ] **Step 3: Update `APIClient` empty response handling**

Change the `EmptyDTO` branch so it decodes the envelope first:

```swift
if T.self == EmptyDTO.self, (200...299).contains(http.statusCode) {
    let envelope = try JSONDecoder().decode(APIEnvelope<EmptyDTO>.self, from: data)
    guard envelope.success else {
        throw APIError.apiError(
            code: envelope.error?.code ?? "UNKNOWN",
            message: envelope.error?.message ?? ""
        )
    }
    return EmptyDTO() as! T
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same `xcodebuild ... -only-testing:ChamChamChamTests/APIClientEnvelopeTests` command.

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Core/Networking/APIClient.swift frontend/ChamChamCham/ChamChamChamTests/APIClientEnvelopeTests.swift
git commit -m "fix(networking): 빈 응답 envelope 처리 보정"
```

---

### Task 2: Shared Farm DTOs And Onboarding Data Source

**Files:**
- Create: `frontend/ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteResponseDTO.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift`

**Interfaces:**
- Consumes: `OnboardingDraft` farm fields populated by JUSO/V-World flow.
- Produces: `FarmRequestDTO(draft:)` encoding a Swagger-required `dataSource` object.

- [ ] **Step 1: Add a failing test for `farm.dataSource`**

Extend `mapsFieldsToWireKeys()` in `OnboardingCompleteRequestDTOTests.swift`:

```swift
let dataSource = try #require(farm["dataSource"] as? [String: String])
#expect(dataSource["address"] == "JUSO")
#expect(dataSource["coordinate"] == "V_WORLD")
#expect(dataSource["parcel"] == "V_WORLD")
#expect(dataSource["landCharacteristic"] == "V_WORLD")
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/OnboardingCompleteRequestDTOTests
```

Expected: fail because `FarmRequestDTO` currently omits `dataSource`.

- [ ] **Step 3: Create shared farm DTOs**

Create `Core/Networking/DTOs/FarmDTOs.swift`:

```swift
//
//  FarmDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct FarmRequestDTO: Encodable, Sendable {
    let farmId: UUID?
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let latitude: Double
    let longitude: Double
    let pnu: String?
    let landCategory: String?
    let areaSqm: Double?
    let areaIsManualEntry: Bool
    let boundaryCoordinates: [FarmBoundaryCoordinateDTO]
    let dataSource: FarmDataSourceDTO
    let cropIds: [UUID]
}

struct FarmDataSourceDTO: Codable, Sendable {
    let address: String?
    let coordinate: String?
    let parcel: String?
    let landCharacteristic: String?

    static let onboardingJusoVWorld = FarmDataSourceDTO(
        address: "JUSO",
        coordinate: "V_WORLD",
        parcel: "V_WORLD",
        landCharacteristic: "V_WORLD"
    )
}

struct FarmBoundaryCoordinateDTO: Codable, Sendable {
    let latitude: Double
    let longitude: Double
}

struct FarmResponseDTO: Decodable, Sendable {
    let id: UUID
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let latitude: Double?
    let longitude: Double?
    let pnu: String?
    let landCategory: String?
    let areaSqm: Double?
    let areaIsManualEntry: Bool
    let boundaryCoordinates: [FarmBoundaryCoordinateDTO]
    let dataSource: FarmDataSourceDTO?
}
```

- [ ] **Step 4: Move onboarding draft initializer into an extension**

In `OnboardingCompleteRequestDTO.swift`, remove the local `FarmRequestDTO`/`FarmBoundaryCoordinateDTO` declarations and add:

```swift
extension FarmRequestDTO {
    init(draft: OnboardingDraft) throws {
        guard !draft.farmName.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmName")
        }
        guard !draft.farmRoadAddress.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw OnboardingSubmissionError.missingRequiredField("farmRoadAddress")
        }
        guard let latitude = draft.farmLatitude, let longitude = draft.farmLongitude else {
            throw OnboardingSubmissionError.missingRequiredField("farmCoordinate")
        }

        self.farmId = nil
        self.name = draft.farmName
        self.roadAddress = draft.farmRoadAddress
        self.jibunAddress = draft.farmJibunAddress.isEmpty ? nil : draft.farmJibunAddress
        self.latitude = latitude
        self.longitude = longitude
        self.pnu = draft.farmPNU
        self.landCategory = draft.farmLandCategory
        self.areaSqm = draft.farmAreaSqm
        self.areaIsManualEntry = draft.farmAreaIsManualEntry
        self.boundaryCoordinates = []
        self.dataSource = .onboardingJusoVWorld
        self.cropIds = draft.cropIDs
    }
}
```

- [ ] **Step 5: Remove duplicate response DTO declarations**

In `OnboardingCompleteResponseDTO.swift`, keep `OnboardingCompleteResponseDTO` and `CropResponseDTO`, and remove the now-shared `FarmResponseDTO`.

- [ ] **Step 6: Run the onboarding DTO tests**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/OnboardingCompleteRequestDTOTests
```

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteResponseDTO.swift frontend/ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift
git commit -m "fix(onboarding): 농장 dataSource 전송 추가"
```

---

### Task 3: Community Comment DTO Compatibility

**Files:**
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Community/Data/DTOs/CommunityResponseDTOs.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/CommunityResponseDecodingTests.swift`

**Interfaces:**
- Consumes: Swagger-shaped `CommentResponse` without `replies`, and generated-doc-shaped `CommentResponse` with `replies`.
- Produces: `CommunityComment.replies == []` when the key is absent.

- [ ] **Step 1: Add a failing Swagger-shaped comment decoding test**

Add this test to `CommunityResponseDecodingTests.swift`:

```swift
@Test("decodes Swagger comment page when replies key is absent")
func commentPageWithoutReplies() throws {
    let json = """
    {
      "success": true,
      "data": {
        "items": [
          {
            "id": "aaaaaaaa-0000-0000-0000-000000000001",
            "parentCommentId": null,
            "author": { "memberId": "33333333-3333-3333-3333-333333333333", "nickname": "질문자", "profileImageUrl": null },
            "body": "댓글입니다",
            "deleted": false,
            "createdAt": "2026-07-07T09:00:00"
          }
        ],
        "nextCursor": null
      },
      "error": null
    }
    """

    let page = try decodeEnvelope(CommentPageResponseDTO.self, from: json).toDomain()

    let comment = try #require(page.items.first)
    #expect(comment.replies.isEmpty)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CommunityResponseDecodingTests
```

Expected: fail because `CommentResponseDTO.replies` is currently required.

- [ ] **Step 3: Make `replies` optional at the wire boundary**

Change `CommentResponseDTO` to:

```swift
struct CommentResponseDTO: Decodable, Sendable {
    let id: UUID
    let parentCommentId: UUID?
    let author: AuthorResponseDTO
    let body: String
    let imageUrl: String?
    let deleted: Bool
    let createdAt: String
    private let decodedReplies: [CommentResponseDTO]?

    var replies: [CommentResponseDTO] { decodedReplies ?? [] }

    private enum CodingKeys: String, CodingKey {
        case id
        case parentCommentId
        case author
        case body
        case imageUrl
        case deleted
        case createdAt
        case decodedReplies = "replies"
    }
}
```

The existing `toDomain()` implementation can continue using `replies.map { $0.toDomain() }`.

- [ ] **Step 4: Run the focused test and verify both comment shapes pass**

Run the same `xcodebuild ... -only-testing:ChamChamChamTests/CommunityResponseDecodingTests` command.

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Features/Community/Data/DTOs/CommunityResponseDTOs.swift frontend/ChamChamCham/ChamChamChamTests/CommunityResponseDecodingTests.swift
git commit -m "fix(community): 댓글 replies 누락 응답 허용"
```

---

### Task 4: Crop Category Endpoint Coverage

**Files:**
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropEndpoint.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropCatalogService.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/CropCategoryEndpointTests.swift`

**Interfaces:**
- Consumes: Swagger path `GET /api/v1/crops/categories/{category}/crops`.
- Produces: `CropCatalogService.fetchCrops(categoryCode:) async throws -> [Crop]`.

- [ ] **Step 1: Add endpoint tests**

Create `CropCategoryEndpointTests.swift`:

```swift
//
//  CropCategoryEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Testing
@testable import ChamChamCham

@Suite("CropEndpoint category paths")
struct CropCategoryEndpointTests {
    @Test("category crop endpoint matches Swagger")
    func categoryPath() {
        let endpoint = CropEndpoint.categoryCrops("ROOT_BARK")

        #expect(endpoint.path == "api/v1/crops/categories/ROOT_BARK/crops")
        #expect(endpoint.method == .get)
        #expect(endpoint.requiresAuth)
        #expect(endpoint.body == nil)
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CropCategoryEndpointTests
```

Expected: fail because `CropEndpoint.categoryCrops` does not exist.

- [ ] **Step 3: Add the endpoint case**

Update `CropEndpoint`:

```swift
enum CropEndpoint: Endpoint {
    case list
    case categories
    case categoryCrops(String)

    var path: String {
        switch self {
        case .list: "api/v1/crops"
        case .categories: "api/v1/crops/categories"
        case let .categoryCrops(category): "api/v1/crops/categories/\(category)/crops"
        }
    }
}
```

- [ ] **Step 4: Add the service method**

Update `CropCatalogService`:

```swift
protocol CropCatalogService: Sendable {
    func fetchCrops() async throws -> [Crop]
    func fetchCrops(categoryCode: String) async throws -> [Crop]
    func fetchCategoryLabels() async throws -> [String]
}
```

Add the remote implementation:

```swift
func fetchCrops(categoryCode: String) async throws -> [Crop] {
    let dtos: [CropResponseDTO] = try await apiClient.send(CropEndpoint.categoryCrops(categoryCode))
    return dtos.map { Crop(id: $0.id, name: $0.name, category: $0.usePartCategoryLabel) }
}
```

- [ ] **Step 5: Run crop tests**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CropCategoryEndpointTests
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropEndpoint.swift frontend/ChamChamCham/ChamChamCham/Features/Onboarding/Data/CropCatalogService.swift frontend/ChamChamCham/ChamChamChamTests/CropCategoryEndpointTests.swift
git commit -m "feat(crop): 카테고리별 작물 조회 endpoint 추가"
```

---

### Task 5: Member Profile API Layer

**Files:**
- Create: `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Domain/MemberProfile.swift`
- Create: `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/DTOs/MemberProfileDTOs.swift`
- Create: `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/MemberEndpoint.swift`
- Create: `frontend/ChamChamCham/ChamChamCham/Features/MyPage/Data/MemberProfileRepository.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/App/DIContainer.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/MemberProfileDTOTests.swift`

**Interfaces:**
- Consumes: Swagger `MyProfileResponse`, `PublicProfileResponse`, `UpdateMyProfileRequest`, shared `FarmRequestDTO`.
- Produces: a MyPage repository boundary without adding presentation screens in this plan.

- [ ] **Step 1: Add DTO decoding tests**

Create `MemberProfileDTOTests.swift` with these assertions:

```swift
//
//  MemberProfileDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/8/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Member profile DTOs")
struct MemberProfileDTOTests {
    @Test("decodes my profile response from Swagger shape")
    func myProfile() throws {
        let json = """
        {
          "memberId": "11111111-1111-1111-1111-111111111111",
          "email": "member@example.com",
          "name": "홍길동",
          "phone": "010-1234-5678",
          "birthDate": "1990-01-01",
          "nickname": "길동",
          "experienceLevel": 7,
          "managementType": "AGRICULTURAL_INDIVIDUAL",
          "profileImageUrl": null,
          "farms": [
            {
              "farmId": "22222222-2222-2222-2222-222222222222",
              "name": "참참농장",
              "roadAddress": "전북특별자치도 전주시 덕진구 예시로 12",
              "jibunAddress": null,
              "displayRegion": "전주시 덕진구"
            }
          ],
          "crops": [
            {
              "cropId": "33333333-3333-3333-3333-333333333333",
              "cropName": "황기"
            }
          ]
        }
        """

        let dto = try JSONDecoder().decode(MyProfileResponseDTO.self, from: Data(json.utf8))

        #expect(dto.memberId == UUID(uuidString: "11111111-1111-1111-1111-111111111111"))
        #expect(dto.farms.first?.displayRegion == "전주시 덕진구")
        #expect(dto.crops.first?.cropName == "황기")
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/MemberProfileDTOTests
```

Expected: fail because `MyProfileResponseDTO` does not exist.

- [ ] **Step 3: Create member profile domain models**

Create `Features/MyPage/Domain/MemberProfile.swift`:

```swift
//
//  MemberProfile.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

struct MyMemberProfile: Sendable, Equatable {
    let memberId: UUID
    let email: String?
    let name: String?
    let phone: String?
    let birthDate: String?
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [MyMemberFarm]
    let crops: [MemberCropProfile]
}

struct PublicMemberProfile: Sendable, Equatable {
    let memberId: UUID
    let nickname: String?
    let experienceLevel: Int?
    let managementType: String?
    let profileImageUrl: String?
    let farms: [PublicMemberFarm]
    let crops: [MemberCropProfile]
}

struct MyMemberFarm: Sendable, Equatable {
    let farmId: UUID
    let name: String
    let roadAddress: String
    let jibunAddress: String?
    let displayRegion: String?
}

struct PublicMemberFarm: Sendable, Equatable {
    let farmId: UUID
    let displayRegion: String?
}

struct MemberCropProfile: Sendable, Equatable {
    let cropId: UUID
    let cropName: String
}
```

- [ ] **Step 4: Create member profile DTOs and mappings**

Create `Features/MyPage/Data/DTOs/MemberProfileDTOs.swift` with DTOs matching Swagger field names: `memberId`, `farmId`, `cropId`, and `cropName`.

Required DTO names:

```swift
struct MyProfileResponseDTO: Decodable, Sendable { ... }
struct PublicProfileResponseDTO: Decodable, Sendable { ... }
struct MyFarmResponseDTO: Decodable, Sendable { ... }
struct PublicFarmResponseDTO: Decodable, Sendable { ... }
struct CropProfileResponseDTO: Decodable, Sendable { ... }
struct UpdateMyProfileRequestDTO: Encodable, Sendable { ... }
```

Mapping methods:

```swift
extension MyProfileResponseDTO {
    func toDomain() -> MyMemberProfile { ... }
}

extension PublicProfileResponseDTO {
    func toDomain() -> PublicMemberProfile { ... }
}
```

- [ ] **Step 5: Create member endpoints**

Create `MemberEndpoint.swift`:

```swift
//
//  MemberEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

enum MemberEndpoint: Endpoint {
    case myProfile
    case publicProfile(UUID)
    case updateMyProfile(UpdateMyProfileRequestDTO)

    var path: String {
        switch self {
        case .myProfile:
            "api/v1/members/me"
        case let .publicProfile(memberId):
            "api/v1/members/\(memberId.uuidString)/profile"
        case .updateMyProfile:
            "api/v1/members/me/profile"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .myProfile, .publicProfile:
            .get
        case .updateMyProfile:
            .put
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case let .updateMyProfile(request):
            request
        case .myProfile, .publicProfile:
            nil
        }
    }

    var requiresAuth: Bool { true }
}
```

- [ ] **Step 6: Create repository**

Create `MemberProfileRepository.swift`:

```swift
//
//  MemberProfileRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/8/26.
//

import Foundation

protocol MemberProfileRepository: Sendable {
    func fetchMyProfile() async throws -> MyMemberProfile
    func fetchPublicProfile(memberId: UUID) async throws -> PublicMemberProfile
    func updateMyProfile(_ request: UpdateMyProfileRequestDTO) async throws -> MyMemberProfile
}

struct RemoteMemberProfileRepository: MemberProfileRepository {
    let apiClient: APIClient

    func fetchMyProfile() async throws -> MyMemberProfile {
        let dto: MyProfileResponseDTO = try await apiClient.send(MemberEndpoint.myProfile)
        return dto.toDomain()
    }

    func fetchPublicProfile(memberId: UUID) async throws -> PublicMemberProfile {
        let dto: PublicProfileResponseDTO = try await apiClient.send(MemberEndpoint.publicProfile(memberId))
        return dto.toDomain()
    }

    func updateMyProfile(_ request: UpdateMyProfileRequestDTO) async throws -> MyMemberProfile {
        let dto: MyProfileResponseDTO = try await apiClient.send(MemberEndpoint.updateMyProfile(request))
        return dto.toDomain()
    }
}
```

- [ ] **Step 7: Register repository in DI**

In `DIContainer.swift`:

```swift
func makeMemberProfileRepository() -> some MemberProfileRepository {
    RemoteMemberProfileRepository(apiClient: apiClient)
}
```

- [ ] **Step 8: Run member profile tests**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/MemberProfileDTOTests
```

Expected: pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Features/MyPage frontend/ChamChamCham/ChamChamCham/App/DIContainer.swift frontend/ChamChamCham/ChamChamChamTests/MemberProfileDTOTests.swift
git commit -m "feat(member): 프로필 API repository 추가"
```

---

### Task 6: Media Usage Coverage

**Files:**
- Modify: `frontend/ChamChamCham/ChamChamCham/Core/Media/MediaImageDTOs.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/Core/Media/MediaUploadRepository.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/CommunitySavePostRequestDTOTests.swift`

**Interfaces:**
- Consumes: Swagger `UploadImageRequest.usageType` enum values `PROFILE`, `COMMUNITY_POST`, `FARMING_RECORD`.
- Produces: typed repository support for farming-record image upload without adding a FarmingRecord UI.

- [ ] **Step 1: Add enum case**

In `MediaImageUsage`:

```swift
case farmingRecord = "FARMING_RECORD"
```

- [ ] **Step 2: Add repository method**

In `MediaUploadRepository`:

```swift
func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO
```

In `RemoteMediaUploadRepository`:

```swift
func uploadFarmingRecordImage(_ imageData: Data, originalFilename: String?) async throws -> UploadedImageResponseDTO {
    try await upload(imageData, usage: .farmingRecord, originalFilename: originalFilename)
}
```

- [ ] **Step 3: Add a lightweight encoding assertion**

Add a test beside existing DTO encoding tests that builds `UploadImageRequestDTO(usageType: MediaImageUsage.farmingRecord.rawValue, ...)` and asserts `usageType == "FARMING_RECORD"`.

- [ ] **Step 4: Run media-related tests**

Run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:ChamChamChamTests/CommunitySavePostRequestDTOTests -only-testing:ChamChamChamTests/ImageDownscalerTests
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/ChamChamCham/ChamChamCham/Core/Media/MediaImageDTOs.swift frontend/ChamChamCham/ChamChamCham/Core/Media/MediaUploadRepository.swift frontend/ChamChamCham/ChamChamChamTests/CommunitySavePostRequestDTOTests.swift
git commit -m "feat(media): 영농기록 이미지 업로드 usage 추가"
```

---

### Task 7: Auth Swagger Coverage Decision

**Files:**
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Auth/Data/AuthEndpoint.swift`
- Modify: `frontend/ChamChamCham/ChamChamCham/Features/Auth/Data/AuthRepository.swift`
- Test: `frontend/ChamChamCham/ChamChamChamTests/AuthEndpointTests.swift`

**Interfaces:**
- Consumes: Swagger email/password endpoints.
- Produces: explicit project decision: either no product code until an email auth screen exists, or typed repository methods with no UI.

- [ ] **Step 1: Confirm product direction before editing**

Use this exact decision gate in the implementation thread:

```text
Email/password auth exists in Swagger, but the current onboarding UI is social-login-only. Should this pass add typed repository support without UI, or keep email auth out of app code until the product screen exists?
```

Recommended answer for MVP: keep email auth out of app code until the product screen exists. This follows YAGNI and avoids unused login states.

- [ ] **Step 2: If product chooses "defer", document the decision in the implementation summary**

No Swift code changes are made for `POST /auth/signup`, `POST /auth/login`, `POST /auth/email/send-code`, or `POST /auth/email/verify-code`.

- [ ] **Step 3: If product chooses "typed support", add endpoint cases**

Add cases:

```swift
case login(email: String, password: String)
case signup(email: String, password: String)
case sendVerificationCode(email: String)
case verifyEmailCode(email: String, code: String)
```

Map them to Swagger paths and add repository methods returning `TokenResponseDTO` or `EmptyDTO` as appropriate.

- [ ] **Step 4: Commit only if code changed**

```bash
git add frontend/ChamChamCham/ChamChamCham/Features/Auth/Data/AuthEndpoint.swift frontend/ChamChamCham/ChamChamCham/Features/Auth/Data/AuthRepository.swift frontend/ChamChamCham/ChamChamChamTests/AuthEndpointTests.swift
git commit -m "feat(auth): 이메일 인증 API endpoint 추가"
```

---

### Task 8: Swagger Non-Production Endpoints And Docs Notes

**Files:**
- Modify: implementation summary only unless the team asks for a debug screen.

**Interfaces:**
- Consumes: Swagger `GET /api/v1/test/ping`, `GET /api/v1/test/me`.
- Produces: no production app endpoint.

- [ ] **Step 1: Keep test endpoints out of production app code**

Do not add `TestEndpoint` to the app target. These endpoints are backend diagnostics and should not be reachable from shipping UI.

- [ ] **Step 2: Record generated-doc drift in the implementation summary**

Mention these exact drifts:

- Current Swagger has no Voice/FarmingRecord/Report/Policy endpoints.
- Current Swagger has member and community controllers beyond the older `frontend/AGENTS.md` note.
- Swagger marks `FarmRequest.dataSource` required.
- Swagger omits `CommentResponse.replies`.

- [ ] **Step 3: Do not edit generated API docs by hand**

Leave `frontend/docs/API 명세서/**` untouched. If the team wants the generated docs to match Swagger, update the Notion source or the generator workflow in a separate docs task.

---

## Full Verification

After all chosen implementation tasks are complete, run:

```bash
cd frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test
```

Expected: test suite passes.

If `iPhone 17` is not installed locally, list available simulators and rerun with an installed iOS 17+ simulator:

```bash
xcrun simctl list devices available
```

## Suggested Task Order

1. Task 0: Swagger snapshot and change alerts.
2. Task 1: envelope fix.
3. Task 2: farm DTO/dataSource fix.
4. Task 3: comment decoding compatibility fix.
5. Task 4: crop category endpoint.
6. Task 5: member profile repository.
7. Task 6: media usage coverage.
8. Task 7: auth email endpoint decision.
9. Task 8: docs notes and non-production endpoint decision.

This order fixes existing breakage risks before adding new API surfaces.

## Self-Review

- Spec coverage: all 29 Swagger operations are accounted for as wired, to wire, defer by product decision, or keep out of production app code.
- Update detection coverage: Swagger drift is handled by committed canonical snapshots, backend-deploy-triggered `repository_dispatch`, scheduled fallback polling, and PR-based review.
- Placeholder scan: no open implementation placeholders remain; Task 7 has an explicit decision gate because email auth has no current product UI.
- Type consistency: shared `FarmRequestDTO` and `FarmResponseDTO` move to Core so onboarding and member profile update use one wire shape.
- YAGNI check: no Voice/FarmingRecord/Report/Policy APIs are scaffolded because they are absent from current Swagger.
