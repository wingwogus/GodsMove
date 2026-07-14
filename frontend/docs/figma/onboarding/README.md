# Onboarding Figma Captures

온보딩과 인증 진입 흐름의 Figma 캡처 및 구현 준비 검토 문서를 모은다.

시각 디자인은 Figma를 기준으로 적용하되, 다음 동작은 화면 디자인과 독립적으로
유지·검토한다.

- 인증 전 / 온보딩 미완료 / 온보딩 완료 회원의 앱 시작 분기
- 인터넷이 없거나 불안정한 농가 환경에서의 로컬 상태 복구와 재시도
- 토큰 만료·로그아웃·계정 전환 시 초안과 화면 상태의 정리
- 온보딩 완료 전 Home 접근 제한 (`BR-USER-001`)
- 한 번의 완료 요청으로 member, farm, member_crop를 만드는 규칙 (`BR-USER-002`)

## 현재 기준 문서

- [온보딩 흐름·오프라인 검토](2026-07-11-onboarding-flow-review.md)

## 캡처

- [Step 1 / 소셜 로그인 데이터 수신](2026-07-11-onboarding-step-1-social-prefill-default.md)
- [Step 1 / 입력 상태 3종](2026-07-11-onboarding-step-1-input-states.md)
- [Step 1 / Figma-계약 충돌](2026-07-11-onboarding-step-1-figma-contract-conflict.md)
- [Step 2 / 대표 재배지 설정](2026-07-11-onboarding-step-2-farm-location.md) — 기본, 필수 입력 완료, 필수값 누락 조합
- [Step 3 / 대표 재배지의 작물 설정](2026-07-11-onboarding-step-3-crop-selection.md) — sticky scroll, 가나다순, 최대 5개 선택, bottom tray
- [Step 3 / Figma-계약 후보](2026-07-11-onboarding-step-3-figma-contract-candidate.md)
- [가입 완료](2026-07-11-onboarding-completion.md) — `재배지 추가하기` Step 2 루프, `시작하기` Home 이동
- [가입 완료 / 추가 재배지 저장 계약 메모](2026-07-11-onboarding-completion-multi-farm-contract-candidate.md)

## 캡처 흐름

1. Figma에서 온보딩 프레임 또는 상태를 하나 선택한다.
2. `캡쳐: 온보딩 / <화면명> / <상태>` 형식으로 전달한다.
3. 화면 구조, 텍스트, 컴포넌트 상태, 전환 조건을 이 폴더에 캡처 문서로 기록한다.
4. 모든 화면의 캡처가 끝나면 `온보딩 디자인 수집 끝`이라고 알린다.
5. 캡처와 흐름 검토를 함께 기준으로 최종 구현 계획을 확정한다.

## 권장 캡처 범위

- 랜딩: 기본, 로그인 중, 로그인 실패
- 기본 정보·프로필: 빈 값, 필수값 입력 완료, 사진 선택, 키보드 노출
- 작물 선택: 로딩, 기본, 검색/카테고리, 선택 완료, 오류·재시도
- 농지 위치: 주소 검색, 검색 결과, 좌표·필지 성공, 필지 없음과 수동 면적, 조회 오류
- 완료: 사진 업로드 중, 제출 중, 사진 업로드 실패, 제출 실패·재시도
- 온보딩 완료 후 Home으로 전환되는 첫 화면

Figma에 없는 런타임 상태는 기존 디자인 시스템의 loading, empty, error, retry
패턴을 사용하되 해당 사실을 구현 계획에 명시한다.
