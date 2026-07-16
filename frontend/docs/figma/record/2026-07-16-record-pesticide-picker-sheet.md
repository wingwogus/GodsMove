# 병해충 관리 / 사용 농약 검색 시트

- Captured: 2026-07-16
- Source MCP calls: `get_selection` → `read_my_design`
- Figma node ID: `1500:7963`
- Frame name: `bottom-sheet`
- Frame size: 390 × 528
- 풀시트(large detent)로 구현 — 컴포넌트/레이어 명이 `bottom-sheet`라고 해서
  하프시트로 바꾸지 않는다. 기존에도 `.sheet` 기본 detent(large) 그대로였고
  유지한다.

## 구조

1. **grabber** (`1500:7964`) — 48×4 캡슐, `#e0e0e0`. 구현: `.sheet`에
   `.presentationDragIndicator(.visible)` 추가 (기존엔 없었음).
2. **filter** (`1500:7966`, height 118, top 20 inset) — 제목 "사용 농약"
   (20/SemiBold, `#1a1a1a` = `Text.default`, `titleMediumEmphasized`) + 검색바
   16pt 아래. 검색바는 `AppSearchBar` 그대로 재사용, placeholder만 교체.
   - ⚠️ Figma 원본 placeholder 텍스트가 "사용한 **비료명**을 검색해보세요."로
     되어 있음 — 비료(fertilizer) 시트에서 복붙한 흔적으로 보이는 오타.
     농약 시트 문맥에 맞게 "사용한 **농약**을 검색해보세요."로 교체함.
3. **list** (`1500:7969`, height 290 = 58×5행) — 각 행 `타이틀` 텍스트
   (20/SemiBold, unselected `#4f4f4f` = `Text.subtle`) + 하단 stroke `#e0e0e0`
   = `Border.default` (기존 코드는 `Divider().overlay(Color.Border.subtle)`로
   틀린 토큰을 쓰고 있었음 — 수정).
   - **선택된 행** (`1500:7972`)만 컴포넌트 인스턴스가 아니라 오버라이드된
     FRAME: 배경 `#e4f8e3` = `Object.primarySubtle`, 텍스트 `#27865c` =
     `Text.primary`, trailing `icon/check_circle`. Figma 컴포넌트 자체엔
     select 배리언트가 없고 이 프레임에서만 임시로 오버라이드한 것 — 디자인
     시스템 `AppListItem`을 확장하지 않고, 이미 존재하는 동일 패턴
     (`CropSelectionBody.cropRow`, 온보딩 작물 선택)을 그대로 재사용해 로컬
     구현함 (`Text.primary`/`Object.primarySubtle`/`Object.primary` 아이콘
     틴트 — crop row와 정확히 동일한 조합).
4. **bottom-button** (`1500:7977`, height 100, 흰 배경) — "완료" 버튼, 배경
   `#343434` = `Object.bold`, radius 12, height 56, full width(20 inset).
   `AppButton(variant: .secondary, size: .medium, fullWidth: true)`와 1:1
   매칭. 하단 여백(top 12 / bottom 32)은 기기별 세이프에어리어 차이가 있어
   고정 픽셀 대신 `.safeAreaInset(edge: .bottom)`으로 구현 — Small Device
   Layout Rule(iPhone SE) 대응.

## 인터랙션 변경 (기존 구현과 다른 지점)

기존 `PesticidePickerSheet`는 행 탭 → 즉시 `onSelect` + `dismiss`였다. 이
캡처는 행 탭 = 로컬 체크 표시만, 실제 확정은 "완료" 버튼으로 분리되어 있음
(라디오 선택 + 확정 버튼 패턴). `RecordComposeViewModel.selectPesticide`가
비동기 네트워크 호출(`fetchPests`)을 겸하고 있어, 탭마다 반복 호출하지 않고
확정 시 1회만 호출하도록 하는 것과도 부합해서 이 패턴대로 구현했다.

재오픈 시 기존 선택값을 유지하도록 `current: Pesticide?` 파라미터를 추가하고
호출부에서 `vm.selectedPesticide`를 넘긴다.

## 구현 위치

`Features/Record/Presentation/Views/RecordComposeView.swift`의 private
`PesticidePickerSheet` (2026-07-16 갱신).

## 미결

- 비료(fertilizer) 쪽에 동일한 검색 시트가 생기면(현재는 자유 텍스트 입력,
  C-8) placeholder 오타의 출처인 그 프레임도 같이 확인해서 일관되게 고칠 것.
