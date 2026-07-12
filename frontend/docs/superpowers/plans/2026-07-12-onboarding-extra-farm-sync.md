# Onboarding Extra Farm Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist every additional farm collected by the existing onboarding loop through `POST /api/v1/farms`, while completing onboarding with the representative farm exactly once and retaining failed extra farms for retry.

**Architecture:** Keep the existing representative-farm onboarding request unchanged in responsibility, add a focused farm API repository, and hand additional drafts to a small persistent FIFO worker after onboarding succeeds. The worker removes each request only after server acknowledgement and is retriggered from authenticated app bootstrap, so extra-farm failure never repeats onboarding completion or blocks entry to the main app.

**Tech Stack:** SwiftUI, Swift 6 strict concurrency, Observation, Foundation `Codable`/`UserDefaults`, native `URLSession` through `APIClient`, Swift Testing.

## Global Constraints

- iOS 17+ minimum deployment target.
- Swift 6 language mode; respect actor isolation.
- Observation framework for view models, not Combine.
- Native URLSession + async/await networking; no new dependency.
- Preserve the existing local-first onboarding draft before every server write.
- Preserve `재배지 추가하기 -> FarmLocationView -> CropSelectionView -> OnboardingCompleteView`.
- `POST /api/v1/auth/onboarding/complete` receives only the first representative farm.
- `POST /api/v1/farms` receives every farm after index zero.
- Do not modify design-system foundations or onboarding visuals.
- Do not use archived Notion API artifacts; deployed Swagger is authoritative.
- New Swift files use the standard Xcode header dated `7/12/26`.
- Work sequentially in the current app target; do not fan out shared `App/`, `Core/`, or onboarding files.

---

### Task 1: Align Farm Request And Response DTOs With Swagger

**Files:**
- Create: `ChamChamCham/ChamChamChamTests/FarmDTOTests.swift`
- Modify: `ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/MyPage/Data/DTOs/MemberProfileDTOs.swift`
- Modify: `ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift`
- Modify: `ChamChamCham/ChamChamChamTests/MemberProfileDTOTests.swift`
- Modify: `ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift`

**Interfaces:**
- Produces: `FarmDraftRequestDTO.init(farm:) throws`
- Produces: `SaveFarmRequestDTO.init(farm:) throws`
- Produces: `StandaloneFarmResponseDTO.farmId: UUID` and `StandaloneFarmResponseDTO.crops: [CropResponseDTO]`
- Preserves: onboarding `FarmResponseDTO.id: UUID` because the backend payload still uses the auth-specific shape.
- Consumes: `OnboardingFarmDraft` and existing shared coordinate/data-source DTOs.

- [ ] **Step 1: Write failing farm DTO contract tests**

Create `FarmDTOTests.swift` with tests that encode a standalone request and decode the deployed response shape:

```swift
//
//  FarmDTOTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Farm DTO contract")
struct FarmDTOTests {
    @Test("standalone save includes cropIds and omits farmId")
    func saveRequestShape() throws {
        let cropId = UUID()
        let farm = OnboardingFarmDraft(
            cropIDs: [cropId],
            farmName: "두번째농장",
            farmRoadAddress: "전북 전주시 둘길 2",
            farmJibunAddress: "전북 전주시 둘동 2",
            farmLatitude: 35.2,
            farmLongitude: 127.2
        )

        let data = try JSONEncoder().encode(SaveFarmRequestDTO(farm: farm))
        let json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(json["farmId"] == nil)
        #expect(json["name"] as? String == "두번째농장")
        #expect((json["cropIds"] as? [String]) == [cropId.uuidString])
    }

    @Test("response decodes farmId and crops")
    func responseShape() throws {
        let farmId = UUID()
        let cropId = UUID()
        let json = """
        {
          "farmId":"\(farmId)",
          "name":"두번째농장",
          "roadAddress":"전북 전주시 둘길 2",
          "jibunAddress":null,
          "latitude":35.2,
          "longitude":127.2,
          "pnu":null,
          "landCategory":null,
          "areaSqm":null,
          "areaIsManualEntry":false,
          "boundaryCoordinates":[],
          "dataSource":{},
          "crops":[{
            "id":"\(cropId)",
            "externalNo":422,
            "name":"황기",
            "usePartCategory":"ROOT_BARK",
            "usePartCategoryLabel":"뿌리·껍질"
          }]
        }
        """

        let response = try JSONDecoder().decode(StandaloneFarmResponseDTO.self, from: Data(json.utf8))

        #expect(response.farmId == farmId)
        #expect(response.crops.map(\.id) == [cropId])
    }
}
```

- [ ] **Step 2: Update existing contract assertions before production code**

In `OnboardingCompleteRequestDTOTests.mapsFieldsToWireKeys`, replace the old nested `farm.cropIds` assertion with:

```swift
#expect(farm["cropIds"] == nil)
#expect(farm["farmId"] == nil)
```

In `MemberProfileDTOTests`, update the profile request test to assert the new Swagger shape no longer includes `farms`:

```swift
let request = UpdateMyProfileRequestDTO(
    name: "홍길동",
    phone: "010-1234-5678",
    birthDate: "1990-01-01",
    nickname: "길동",
    experienceLevel: 7,
    managementType: "AGRICULTURAL_INDIVIDUAL",
    profileMediaId: nil
)
let data = try JSONEncoder().encode(request)
let json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
#expect(json["name"] as? String == "홍길동")
#expect(json["farms"] == nil)
```

- [ ] **Step 3: Run the targeted tests and verify RED**

Run:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/FarmDTOTests \
  -only-testing:ChamChamChamTests/OnboardingCompleteRequestDTOTests \
  -only-testing:ChamChamChamTests/MemberProfileDTOTests
```

Expected: compile failure because `SaveFarmRequestDTO` does not exist, plus existing assertions reveal the stale nested `cropIds`/`farms` shapes.

- [ ] **Step 4: Implement the minimal DTO split**

Replace the ambiguous request in `FarmDTOs.swift` with two explicit wire types while keeping shared value DTOs:

```swift
struct FarmDraftRequestDTO: Encodable, Sendable {
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
}

struct SaveFarmRequestDTO: Codable, Sendable, Equatable {
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

struct FarmDataSourceDTO: Codable, Sendable, Equatable {
    let address: String?
    let coordinate: String?
    let parcel: String?
    let landCharacteristic: String?

    static let onboardingJusoVWorld = FarmDataSourceDTO(
        address: "JUSO",
        coordinate: "V_WORLD_ADDRESS",
        parcel: "V_WORLD_CADASTRAL",
        landCharacteristic: "V_WORLD_LAND_CHARACTERISTIC"
    )
}

struct FarmBoundaryCoordinateDTO: Codable, Sendable, Equatable {
    let latitude: Double
    let longitude: Double
}

struct StandaloneFarmResponseDTO: Decodable, Sendable {
    let farmId: UUID
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
    let dataSource: FarmDataSourceDTO
    let crops: [CropResponseDTO]
}
```

Move the shared validation/mapping in `OnboardingCompleteRequestDTO.swift` to `FarmDraftRequestDTO.init(farm:)`, and implement `SaveFarmRequestDTO.init(farm:)` by copying the validated draft fields and adding `cropIds: farm.cropIDs`. Add unique-crop validation:

```swift
guard !farm.cropIDs.isEmpty, farm.cropIDs.count <= 5,
      Set(farm.cropIDs).count == farm.cropIDs.count else {
    throw OnboardingSubmissionError.missingRequiredField("cropIDs")
}
```

Change `OnboardingCompleteRequestDTO.farm` to `FarmDraftRequestDTO`. Remove
`farms` from `UpdateMyProfileRequestDTO`, matching current Swagger. Keep the
existing onboarding `FarmResponseDTO(id: ...)` factory construction unchanged.
Document in `FarmDTOTests` that the endpoint-specific response split is required
by the backend/OpenAPI name collision recorded in the design spec.

- [ ] **Step 5: Re-run targeted tests and verify GREEN**

Run the command from Step 3.

Expected: all three suites pass.

- [ ] **Step 6: Commit the DTO contract**

```bash
git add ChamChamCham/ChamChamCham/Core/Networking/DTOs/FarmDTOs.swift \
  ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift \
  ChamChamCham/ChamChamCham/Features/MyPage/Data/DTOs/MemberProfileDTOs.swift \
  ChamChamCham/ChamChamChamTests/FarmDTOTests.swift \
  ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift \
  ChamChamCham/ChamChamChamTests/MemberProfileDTOTests.swift \
  ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift
git commit -m "feat(farm): Swagger DTO 계약 반영"
```

### Task 2: Add The Authenticated Farm Create Data Layer

**Files:**
- Create: `ChamChamCham/ChamChamCham/Core/Networking/FarmEndpoint.swift`
- Create: `ChamChamCham/ChamChamCham/Core/Networking/FarmRepository.swift`
- Create: `ChamChamCham/ChamChamChamTests/FarmEndpointTests.swift`

**Interfaces:**
- Produces: `FarmEndpoint.list` and `FarmEndpoint.create(SaveFarmRequestDTO)`.
- Produces: `FarmRepository.listFarms() async throws -> [StandaloneFarmResponseDTO]`.
- Produces: `FarmRepository.createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO`.
- Consumes: `APIClient`, `SaveFarmRequestDTO`, and `StandaloneFarmResponseDTO` from Task 1.

- [ ] **Step 1: Write failing endpoint tests**

Create `FarmEndpointTests.swift`:

```swift
//
//  FarmEndpointTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("FarmEndpoint")
struct FarmEndpointTests {
    @Test("list and create match deployed paths")
    func pathsAndMethods() throws {
        let request = try SaveFarmRequestDTO(farm: OnboardingTestFactory.validDraft().representativeFarm)
        #expect(FarmEndpoint.list.path == "api/v1/farms")
        #expect(FarmEndpoint.list.method == .get)
        #expect(FarmEndpoint.list.body == nil)
        #expect(FarmEndpoint.create(request).path == "api/v1/farms")
        #expect(FarmEndpoint.create(request).method == .post)
        #expect(FarmEndpoint.create(request).body != nil)
        #expect(FarmEndpoint.create(request).requiresAuth)
    }
}
```

- [ ] **Step 2: Run endpoint tests and verify RED**

Run:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/FarmEndpointTests
```

Expected: compile failure because `FarmEndpoint` does not exist.

- [ ] **Step 3: Implement endpoint and repository**

Create `FarmEndpoint.swift`:

```swift
//
//  FarmEndpoint.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

enum FarmEndpoint: Endpoint {
    case list
    case create(SaveFarmRequestDTO)

    var path: String { "api/v1/farms" }

    var method: HTTPMethod {
        switch self {
        case .list: .get
        case .create: .post
        }
    }

    var body: (any Encodable & Sendable)? {
        switch self {
        case .list: nil
        case let .create(request): request
        }
    }

    var requiresAuth: Bool { true }
}
```

Create `FarmRepository.swift`:

```swift
//
//  FarmRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

protocol FarmRepository: Sendable {
    func listFarms() async throws -> [StandaloneFarmResponseDTO]
    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO
}

struct RemoteFarmRepository: FarmRepository {
    let apiClient: APIClient

    func listFarms() async throws -> [StandaloneFarmResponseDTO] {
        try await apiClient.send(FarmEndpoint.list)
    }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        try await apiClient.send(FarmEndpoint.create(request))
    }
}
```

The remote repository is exercised by the sync-service tests in Task 3 through
its protocol boundary. `APIClient` envelope behavior already has dedicated
coverage, so do not introduce a second networking stack solely to mock
`RemoteFarmRepository`.

- [ ] **Step 4: Run farm data-layer tests and verify GREEN**

Run:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/FarmEndpointTests
```

Expected: the endpoint suite passes and the app target compiles the repository.

- [ ] **Step 5: Commit the data layer**

```bash
git add ChamChamCham/ChamChamCham/Core/Networking/FarmEndpoint.swift \
  ChamChamCham/ChamChamCham/Core/Networking/FarmRepository.swift \
  ChamChamCham/ChamChamChamTests/FarmEndpointTests.swift
git commit -m "feat(farm): 농장 생성 API 추가"
```

### Task 3: Persist And Drain Extra Farms As A FIFO Queue

**Files:**
- Create: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/PendingFarmStore.swift`
- Create: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/PendingFarmSyncService.swift`
- Create: `ChamChamCham/ChamChamChamTests/PendingFarmSyncServiceTests.swift`
- Modify: `ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift`

**Interfaces:**
- Produces: actor `PendingFarmStore` with `replace(with:)`, `load()`, and `removeFirst()`.
- Produces: actor `PendingFarmSyncService` with `enqueue(_:)` and `syncPending()`.
- Consumes: `FarmRepository` and `[SaveFarmRequestDTO]`.

- [ ] **Step 1: Write failing queue tests**

Create tests covering ordered success and partial failure:

```swift
@Test("sync removes successful requests in FIFO order")
func successfulFIFO() async throws {
    let store = PendingFarmStore(defaults: isolatedDefaults())
    let repository = RecordingFarmRepository()
    let requests = try twoFarmRequests()
    let service = PendingFarmSyncService(store: store, repository: repository)

    await service.enqueue(requests)
    await service.syncPending()

    #expect(await repository.createdNames == requests.map(\.name))
    #expect(await store.load().isEmpty)
}

@Test("sync keeps failed and unattempted requests")
func partialFailure() async throws {
    let store = PendingFarmStore(defaults: isolatedDefaults())
    let repository = RecordingFarmRepository(failAtCall: 2)
    let requests = try threeFarmRequests()
    let service = PendingFarmSyncService(store: store, repository: repository)

    await service.enqueue(requests)
    await service.syncPending()

    #expect(await repository.createdNames == Array(requests.prefix(2)).map(\.name))
    #expect(await store.load().map(\.name) == Array(requests.dropFirst()).map(\.name))
}
```

The fake counts the failed call as attempted; the queue removes only the first successful item and stops on the second failure.

Define the test helpers in the same file so the suite is self-contained:

```swift
private func isolatedDefaults() -> UserDefaults {
    UserDefaults(suiteName: "pending-farms-\(UUID().uuidString)")!
}

private func request(named name: String) throws -> SaveFarmRequestDTO {
    var farm = OnboardingTestFactory.validDraft().representativeFarm
    farm.farmName = name
    return try SaveFarmRequestDTO(farm: farm)
}

private func twoFarmRequests() throws -> [SaveFarmRequestDTO] {
    [try request(named: "추가농장1"), try request(named: "추가농장2")]
}

private func threeFarmRequests() throws -> [SaveFarmRequestDTO] {
    [
        try request(named: "추가농장1"),
        try request(named: "추가농장2"),
        try request(named: "추가농장3")
    ]
}

private actor RecordingFarmRepository: FarmRepository {
    private(set) var createdNames: [String] = []
    private var callCount = 0
    private let failAtCall: Int?

    init(failAtCall: Int? = nil) {
        self.failAtCall = failAtCall
    }

    func listFarms() async throws -> [StandaloneFarmResponseDTO] { [] }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        callCount += 1
        createdNames.append(request.name)
        if callCount == failAtCall { throw FakeUploadError() }
        return OnboardingTestFactory.standaloneFarmResponse(name: request.name)
    }
}
```

Add this factory in test support:

```swift
static func standaloneFarmResponse(name: String) -> StandaloneFarmResponseDTO {
    StandaloneFarmResponseDTO(
        farmId: UUID(),
        name: name,
        roadAddress: "전북 전주시 예시로 1",
        jibunAddress: nil,
        latitude: 35.8,
        longitude: 127.1,
        pnu: nil,
        landCategory: nil,
        areaSqm: nil,
        areaIsManualEntry: false,
        boundaryCoordinates: [],
        dataSource: .onboardingJusoVWorld,
        crops: []
    )
}
```

- [ ] **Step 2: Run queue tests and verify RED**

Run:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/PendingFarmSyncServiceTests
```

Expected: compile failure because the store and service do not exist.

- [ ] **Step 3: Implement the persistent actor store**

Create `PendingFarmStore.swift`:

```swift
//
//  PendingFarmStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

actor PendingFarmStore {
    private let defaults: UserDefaults
    private let key = "onboarding.pending-extra-farms"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func replace(with requests: [SaveFarmRequestDTO]) {
        guard let data = try? JSONEncoder().encode(requests) else { return }
        defaults.set(data, forKey: key)
    }

    func load() -> [SaveFarmRequestDTO] {
        guard let data = defaults.data(forKey: key),
              let requests = try? JSONDecoder().decode([SaveFarmRequestDTO].self, from: data) else { return [] }
        return requests
    }

    func removeFirst() {
        var requests = load()
        guard !requests.isEmpty else { return }
        requests.removeFirst()
        replace(with: requests)
    }
}
```

- [ ] **Step 4: Implement the focused sync actor**

Create `PendingFarmSyncService.swift`:

```swift
//
//  PendingFarmSyncService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

actor PendingFarmSyncService {
    private let store: PendingFarmStore
    private let repository: any FarmRepository
    private var isSyncing = false

    init(store: PendingFarmStore, repository: any FarmRepository) {
        self.store = store
        self.repository = repository
    }

    func enqueue(_ requests: [SaveFarmRequestDTO]) async {
        await store.replace(with: requests)
    }

    func syncPending() async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        while let request = await store.load().first {
            do {
                _ = try await repository.createFarm(request)
                await store.removeFirst()
            } catch {
                return
            }
        }
    }
}
```

Do not erase an existing queue on a cold-launch retry. `enqueue` is called only at the one-time onboarding handoff; `syncPending` only drains.

- [ ] **Step 5: Run queue tests and verify GREEN**

Run the command from Step 2.

Expected: all FIFO and partial-failure tests pass.

- [ ] **Step 6: Commit the queue**

```bash
git add ChamChamCham/ChamChamCham/Features/Onboarding/Data/PendingFarmStore.swift \
  ChamChamCham/ChamChamCham/Features/Onboarding/Data/PendingFarmSyncService.swift \
  ChamChamCham/ChamChamChamTests/PendingFarmSyncServiceTests.swift \
  ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift
git commit -m "feat(onboarding): 추가 농장 재시도 큐 추가"
```

### Task 4: Hand Off Additional Drafts After Onboarding And Retry At Bootstrap

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/ViewModels/OnboardingViewModel.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/AuthFlowView.swift`
- Modify: `ChamChamCham/ChamChamCham/App/DIContainer.swift`
- Modify: `ChamChamCham/ChamChamCham/App/RootView.swift`
- Modify: `ChamChamCham/ChamChamChamTests/OnboardingViewModelSubmitTests.swift`
- Modify: `ChamChamCham/ChamChamChamTests/OnboardingViewModelNavigationTests.swift`
- Modify: `ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift`

**Interfaces:**
- `OnboardingViewModel` consumes `PendingFarmSyncService`.
- `DIContainer` owns exactly one `PendingFarmStore` and one `PendingFarmSyncService` for the process.
- `RootView` triggers `syncPending()` only when authenticated and onboarded.

- [ ] **Step 1: Add failing submission-order tests**

Extend the submit harness with a `PendingFarmStore` and `PendingFarmSyncService`. Add these cases:

```swift
@Test("one farm completes onboarding without standalone farm creation")
func representativeOnly() async {
    let harness = makeHarness(withPhoto: false, farmCount: 1)
    await harness.viewModel.submit(appState: harness.appState)
    #expect(await harness.onboarding.callCount == 1)
    #expect(await harness.farm.createdNames.isEmpty)
}

@Test("additional farms are created after onboarding in draft order")
func createsAdditionalFarms() async {
    let harness = makeHarness(withPhoto: false, farmCount: 3)
    await harness.viewModel.submit(appState: harness.appState)
    #expect(await harness.onboarding.callCount == 1)
    #expect(await harness.farm.createdNames == ["추가농장1", "추가농장2"])
    #expect(harness.appState.isOnboarded)
}

@Test("extra farm failure still completes onboarding and retains pending requests")
func extraFarmFailureIsRecoverable() async {
    let harness = makeHarness(withPhoto: false, farmCount: 3, farmFailAtCall: 1)
    await harness.viewModel.submit(appState: harness.appState)
    #expect(await harness.onboarding.callCount == 1)
    #expect(harness.appState.isOnboarded)
    #expect(await harness.pendingStore.load().map(\.name) == ["추가농장1", "추가농장2"])
}
```

Keep the existing navigation regression unchanged:

```swift
#expect(viewModel.currentStep == .farmLocation)
#expect(viewModel.draft.farms.count == 2)
#expect(viewModel.draft.activeFarmIndex == 1)
```

- [ ] **Step 2: Run onboarding submit/navigation tests and verify RED**

Run:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/OnboardingViewModelSubmitTests \
  -only-testing:ChamChamChamTests/OnboardingViewModelNavigationTests
```

Expected: compile failures for the missing sync-service dependency and harness fields.

- [ ] **Step 3: Inject the sync service and perform the one-time handoff**

Add `pendingFarmSyncService` to `OnboardingViewModel.init`. Build all standalone
requests before calling onboarding completion, so a defensive mapping failure
cannot occur after the backend has already marked onboarding complete. After
the call succeeds, perform local handoff before clearing the draft:

```swift
let extraFarmRequests = try draft.farms.dropFirst().map(SaveFarmRequestDTO.init(farm:))
let response = try await onboardingRepository.completeOnboarding(draft)
memberProfileCache.save(member: response.member, onboarding: response.onboarding)
await pendingFarmSyncService.enqueue(extraFarmRequests)
store.clear()
appState.isOnboarded = true
submissionState = .idle
await pendingFarmSyncService.syncPending()
```

The cache save and pending enqueue precede `appState.isOnboarded = true`. A DTO mapping failure remains in the completion error path and does not discard the onboarding draft.

- [ ] **Step 4: Wire one shared service through DI and bootstrap**

In `DIContainer`, construct and retain:

```swift
let pendingFarmStore: PendingFarmStore
let pendingFarmSyncService: PendingFarmSyncService

let pendingFarmStore = PendingFarmStore()
self.pendingFarmStore = pendingFarmStore
self.pendingFarmSyncService = PendingFarmSyncService(
    store: pendingFarmStore,
    repository: RemoteFarmRepository(apiClient: apiClient)
)
```

Pass `container.pendingFarmSyncService` from `AuthFlowView` into `OnboardingViewModel`.

In `RootView`, add a state-dependent retry trigger:

```swift
.task(id: appState.isAuthenticated && appState.isOnboarded) {
    guard appState.isAuthenticated, appState.isOnboarded else { return }
    await container.pendingFarmSyncService.syncPending()
}
```

This retries after cold-launch cache routing and after the onboarding view transitions to the main app. The actor's `isSyncing` guard prevents duplicate concurrent drain loops.

- [ ] **Step 5: Update previews and test factories**

Pass an isolated sync service backed by an empty fake repository to `OnboardingViewModel.preview()` and every direct test initializer. Do not add default fake production dependencies.

- [ ] **Step 6: Run onboarding tests and verify GREEN**

Run the command from Step 2, then:

```bash
cd ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:ChamChamChamTests/PendingFarmSyncServiceTests \
  -only-testing:ChamChamChamTests/FarmDTOTests \
  -only-testing:ChamChamChamTests/FarmEndpointTests
```

Expected: all targeted suites pass, including the existing `재배지 추가하기` navigation regression.

- [ ] **Step 7: Commit the onboarding integration**

```bash
git add ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/ViewModels/OnboardingViewModel.swift \
  ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/AuthFlowView.swift \
  ChamChamCham/ChamChamCham/App/DIContainer.swift \
  ChamChamCham/ChamChamCham/App/RootView.swift \
  ChamChamCham/ChamChamChamTests/OnboardingViewModelSubmitTests.swift \
  ChamChamCham/ChamChamChamTests/OnboardingViewModelNavigationTests.swift \
  ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift
git commit -m "feat(onboarding): 추가 농장 생성 API 연결"
```

### Task 5: Reconcile Plans And Verify The App

**Files:**
- Modify: `docs/superpowers/plans/2026-07-11-onboarding-figma-implementation.md`
- Modify: `docs/superpowers/plans/2026-07-12-onboarding-extra-farm-sync.md`
- Verify: `docs/swagger/openapi.json`
- Verify: all Swift diffs.

**Interfaces:**
- Records that the previously future standalone farm endpoint is now integrated.
- Produces final build/test evidence without changing product scope.

- [ ] **Step 1: Update the original onboarding plan status**

Replace “future standalone farm-add endpoint” wording with a link to this plan and mark the backend limitation resolved by `POST /api/v1/farms`. Do not change already-completed Figma task history.

- [ ] **Step 2: Verify Swagger has not drifted**

Run from `frontend/`:

```bash
python3 scripts/sync_swagger_spec.py --check
```

Expected: exit 0 and hash `c92cfa6e518ae661d386fd29fb94dbf9ec7e04eee3bae5dd55d9040ebc8643b3`, unless the deployed backend changed again. If it changed, run `--write`, inspect the new farm diff, and update DTO tests before proceeding.

- [ ] **Step 3: Run all tests**

Run from `frontend/ChamChamCham/`:

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test
```

Expected: `** TEST SUCCEEDED **`. If the named simulator is unavailable, select an installed iOS 17+ iPhone simulator from `xcrun simctl list devices available` and record the exact replacement.

- [ ] **Step 4: Build the app**

Run:

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Inspect the Swift diff for repository rules**

Run from `frontend/`:

```bash
rg -n 'FarmRequestDTO|raw|Color\(|\.font\(' ChamChamCham/ChamChamCham --glob '*.swift'
git diff --check
git status --short
```

Confirm:

- no stale ambiguous `FarmRequestDTO` remains;
- no new raw colors/fonts or duplicate UI components were introduced;
- all new Swift files contain the standard header;
- only intended farm, onboarding, app wiring, tests, and plan files changed;
- `git diff --check` prints nothing.

- [ ] **Step 6: Commit verification documentation**

```bash
git add docs/superpowers/plans/2026-07-11-onboarding-figma-implementation.md \
  docs/superpowers/plans/2026-07-12-onboarding-extra-farm-sync.md
git commit -m "docs(onboarding): 추가 농장 연동 상태 기록"
```

- [ ] **Step 7: Report exact evidence and residual risk**

Record the exact test/build commands and results. Disclose the remaining narrow crash window between a successful farm-create response and removal of that request from local storage; the backend contract currently has no idempotency key, so eliminating that window requires a backend contract change rather than speculative client deduplication.
