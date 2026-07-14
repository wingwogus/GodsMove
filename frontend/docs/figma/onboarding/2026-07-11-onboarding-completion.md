# Figma Capture: Onboarding Completion

- Captured at: 2026-07-11
- Source: TalkToFigma MCP `get_selection`, `read_my_design`, `scan_text_nodes`
- Figma node: `648:6728`
- Figma frame name: `onboarding / 가입 완료`
- Size: `390 x 844`
- Product screen name: `가입 완료`
- Purpose: 최소 온보딩 입력을 마친 뒤 추가 재배지를 더 입력하거나, 가입 완료를 확정하고 Home으로 이동한다.

## Captured Structure

TalkToFigma MCP가 읽은 선택 프레임:

- Status bar: `390 x 54`
- Top app bar: `390 x 60`, leading/trailing/title은 비어 있음
- Main content frame: `390 x 194`, 화면 중앙 근처 배치
- Success icon: `icon/check_circle`, `108 x 108`, centered
- Title: `가입 완료`
  - Font: Pretendard Bold `32`
  - Color: `#1a1a1a`
  - Alignment: center
- Subtitle: `참참참에 오신 걸 환영합니다!`
  - Font: Pretendard Medium `20`
  - Color: `#4f4f4f`
  - Alignment: center
- Bottom button area: `390 x 100`, 하단 고정
- Left button: `재배지 추가하기`
  - Size: `169 x 56`
  - Fill: `#ffffff`
  - Stroke: `#e0e0e0`
  - Radius: `12`
  - Text color: `#4f4f4f`
- Right button: `시작하기`
  - Size: `169 x 56`
  - Fill: `#343434`
  - Radius: `12`
  - Text color: `#ffffff`
- Button gap: `12`

MCP text scan 결과:

- `가입 완료`
- `참참참에 오신 걸 환영합니다!`
- `재배지 추가하기`
- `시작하기`

## Flow Semantics

사용자 확인 기준:

- `재배지 추가하기`를 누르면 Step 2 `대표 재배지 설정하기`로 이동한다.
- 추가한 재배지도 위치 및 농지명 입력 Step 2와 작물 선택 Step 3을 한 쌍으로 가진다.
- `시작하기`를 누르면 회원가입과 로그인까지 완료된 상태로 Home으로 이동한다.

따라서 이 화면은 단순 성공 메시지가 아니라 마지막 분기 화면이다.

권장 해석:

1. Step 1 기본 정보, Step 2 대표 재배지, Step 3 작물 선택을 로컬 draft에 저장한다.
2. `가입 완료` 화면에 진입한다.
3. 사용자가 `재배지 추가하기`를 누르면 새 재배지 draft를 추가하고 Step 2로 돌아간다.
4. 새 재배지의 Step 2/Step 3을 완료하면 다시 `가입 완료` 화면으로 돌아온다.
5. 사용자가 `시작하기`를 누를 때 서버 제출을 수행한다.
6. 제출 성공 후 member profile/onboarding cache를 갱신하고 Home으로 이동한다.

이 해석이 가장 자연스러운 이유는 `재배지 추가하기`가 Home 전 온보딩 루프 안에 있기
때문이다. 만약 서버 제출을 이 화면 진입 시점에 이미 수행한다면, `재배지 추가하기`는
온보딩이 아니라 가입 후 프로필/재배지 수정 플로우가 된다. 그러면 API 호출 순서,
에러 복구, Home 전 blocking 상태가 더 복잡해진다.

## Runtime Behavior

### `재배지 추가하기`

- 현재까지 입력한 기본 정보와 기존 재배지/작물 선택 draft를 보존한다.
- 새 재배지 draft를 생성하고 Step 2로 이동한다.
- Step 2에서 주소, 좌표, 필지, 농지명을 입력한다.
- Step 3에서 해당 재배지의 작물을 최대 5개 선택한다.
- 완료 후 다시 `가입 완료` 화면으로 돌아온다.
- 뒤로가기 시 기존 재배지 draft가 유실되지 않아야 한다.

### `시작하기`

- 아직 서버 제출 전이라면 `POST /api/v1/auth/onboarding/complete` 또는 확정된 최신
  완료 API를 호출한다.
- 제출 중에는 중복 tap을 막고 loading 상태를 제공한다.
- 성공 시 token/member/onboarding cache를 갱신하고 Home으로 이동한다.
- 실패 시 Home으로 보내지 않고 같은 화면에서 retry를 제공한다.
- 선택 프로필 사진 업로드 실패는 기존 정책처럼 `다시 시도` 또는 `사진 없이 계속하기`
  경로를 제공할 수 있다.

## API and Data Implications

최신 deployed Swagger snapshot SHA-256:
`3cc2a1870dbc6006a9dd3591e7e1c1aee5bb188c4ac836c15d58657babdf2541`

2026-07-12 backend/front 합의 기준:

- 온보딩 완료 API 자체를 `farms[]`로 확장하지 않는다.
- `POST /api/v1/auth/onboarding/complete`는 현재처럼 대표 재배지 1개를 저장한다.
- 온보딩 중 추가로 입력한 두 번째 이후 재배지는 frontend draft에 보존하고, backend가 새 standalone farm 추가/수정 endpoint를 제공하면 그 endpoint에 연결한다.
- 이 방향은 마이페이지의 농지 추가/수정에도 같은 endpoint를 재사용하기 위한 선택이다.

현재 onboarding complete 계약은 단일 재배지만 직접 받는다.

- `CompleteOnboardingRequest.farm`: single `FarmRequest`
- `CompleteOnboardingRequest.cropIds`: top-level crop id array
- backend `OnboardingService.complete`: farm 하나를 저장하고, 그 farm에 crop들을 연결한다.
- frontend `OnboardingDraft`: Figma 흐름 대응을 위해 `farms[]`와 `activeFarmIndex`를 갖되, complete DTO 변환 시 첫 번째/대표 재배지만 현재 contract로 매핑한다.

반면 Figma completion flow는 `재배지 추가하기`를 통해 Step 2/Step 3 쌍을 여러 번 반복할
수 있는 구조다. 따라서 frontend draft는 배열 기반으로 간다. 다만 서버 저장은 다음 두
단계로 분리한다.

1. `시작하기` tap 시 `POST /api/v1/auth/onboarding/complete`로 첫 번째/대표 재배지 1개를 저장한다.
2. standalone farm 추가 endpoint가 확정되면 두 번째 이후 재배지를 별도 저장한다. 네트워크 실패 대비를 위해 draft 유지 또는 outbox 저장 정책을 함께 설계한다.

상세 backend 계약 메모는
[가입 완료 / 추가 재배지 저장 계약 메모](2026-07-11-onboarding-completion-multi-farm-contract-candidate.md)에
분리했다.

## Implementation Status (2026-07-12)

- `OnboardingCompleteView`는 화면 진입 즉시 자동 제출하지 않는다.
- `가입 완료`, `참참참에 오신 걸 환영합니다!`, `재배지 추가하기`, `시작하기` UI를 구현했다.
- `재배지 추가하기`는 새 `OnboardingFarmDraft`를 만들고 Step 2(`farmLocation`)로 이동한다.
- `시작하기`를 누를 때만 사진 업로드와 `POST /api/v1/auth/onboarding/complete` 제출을 실행한다.
- 제출 중, 사진 업로드 실패, 제출 실패 retry 상태는 기존 정책을 유지한다.
- `OnboardingDraft`는 `farms[]`, `activeFarmIndex`, active farm compatibility accessor를 가진다.
- `OnboardingCompleteRequestDTO`는 현재 backend contract에 맞춰 첫 번째/대표 재배지만 보낸다.

## Missing Figma States

- `재배지 추가하기` 후 되돌아왔을 때 여러 재배지를 요약해서 보여주는 상태
- 네트워크 단절 상태
- 작은 화면에서 하단 버튼과 중앙 success content가 함께 보이는 상태
- standalone farm 추가 endpoint 연결 후 두 번째 이후 재배지 저장 실패/재시도 상태

## Implementation Notes

- 이 화면에서 Home 이동 조건은 서버 완료 성공이어야 한다. 로컬 draft만 완료된 상태로 Home을 열면
  인증/온보딩 상태와 실제 서버 상태가 어긋난다.
- 농가 인터넷 환경을 고려해 제출 실패 시 draft를 유지하고, 재진입 시 같은 completion 화면에서
  다시 시도할 수 있어야 한다.
- `재배지 추가하기`로 Step 2에 돌아갈 때 기존 대표 재배지 draft를 덮어쓰지 않도록 active farm index
  또는 editing farm id 개념이 필요하다.
- 추가 재배지가 여러 개인 경우 Step 3의 선택 chip은 현재 active farm에만 연결되어야 한다.
