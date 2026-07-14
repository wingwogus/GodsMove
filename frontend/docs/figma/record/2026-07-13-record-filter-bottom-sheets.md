# 기록 (필터 사용, 바텀시트)

- Captured: 2026-07-13
- Source MCP calls: `mcp__TalkToFigma__get_selection` (3개 선택) → `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__export_node_as_image` × 3
- Figma node IDs: `1247:23970` (진행중인 작물) / `1247:23587` (영농 활동) / `1247:23616` (작성 기간)
- Frame name: 모두 `bottom-sheet`
- State: [기록 메인 / default](2026-07-13-record-main-default.md)의 필터 칩 3개(작물/영농 활동/기간)를 탭했을 때 올라오는 필터 바텀시트. 3개가 서로 다른 필터 축에 대응.

## PNG

파일로 저장하지 않음 (인라인 이미지만 반환하는 클라이언트 한계 — [HANDOFF.md](HANDOFF.md) Part 2).
구조·색상·타이포는 `read_my_design` 값으로 정밀 기록. 인라인 캡처 3장 육안 검증 완료.

---

## ⭐️ 딤드(dim) 처리 규칙 — 사용자 지정 스펙 (2026-07-13)

사용자가 채팅에 붙여넣은 이미지 스펙을 텍스트로 전사(파일 저장 불가):

> 바텀시트 내 값의 경우, **복수 선택 가능**합니다.
> 필터 사용 → 바텀시트 제공 시, 다음과 같이 배경 딤드처리하고자 합니다.
> **딤드 값: `#1a1a1a`, opacity 64%** (모든 바텀시트 사용 시 딤드값 동일합니다.)

- 이 딤드 규칙은 **이 화면 전용이 아니라 앱 전체 바텀시트 공통 규칙**이다. 배경
  스크림은 `#1a1a1a` @ 64% opacity로 통일.
- 칩 기반 필터(작물/영농 활동)는 **복수 선택** 가능.
- 시사점: 디자인 시스템에 공통 바텀시트 스크림(scrim) 토큰/모디파이어가 있는지
  확인하고, 없으면 `#1a1a1a` @ 0.64를 공통값으로 도입 검토 (기존
  `Color.Text.default`가 #1a1a1a 이므로 `Color.Text.default.opacity(0.64)`로 표현
  가능성 — 단 시맨틱상 scrim 별도 토큰이 더 적절한지 팀 판단 필요).

---

## 공통 바텀시트 구조 (3개 공유)

세로 스택(top → bottom), 배경 `#ffffff`:

1. **grabber** — 상단 핸들. 48×4 pill, fill `#e0e0e0`(`Color.Border.default`),
   cornerRadius 1000. 컨테이너 프레임 높이 20 (상단 여백 포함).
2. **filter** — 제목 + 콘텐츠 영역. 좌우 padding 20 (350 내부 폭). 제목과 콘텐츠 사이 간격 16.
   - 제목 텍스트: Pretendard **SemiBold 20**, letterSpacing -0.2, lineHeight 26, color `#1a1a1a`(`Color.Text.default`)
3. **bottom-button** — 배경 `#ffffff`, 높이 100. 내부 `완료` 버튼:
   - 350×56, cornerRadius **12**, fill `#343434`(`Color.Object.bold`)
   - 라벨 "완료": Pretendard Medium 18, letterSpacing -0.36, lineHeight 27, color `#ffffff`(`Color.Text.inverse`)
   - 버튼 위 상단 padding 12 (bottom-button 프레임 y+12 위치)

바텀시트 전체 높이는 콘텐츠에 따라 가변: 작물/활동 274, 기간 258.

---

## 1. 진행중인 작물 필터 (`1247:23970`) — "작물" 칩 대응

- 프레임 274 높이. 제목 "진행중인 작물".
- `selected-chip` 프레임(350×72): chip-list-1(5개) + chip-list-2(2개), 2줄, 총 7개 칩.
- 칩 라벨은 전부 placeholder "레이블" (실데이터는 member_crop 작물명).
- 선택/비선택 칩 혼재 — chip variant 아래 [칩 상태 규칙](#칩-상태-규칙) 참고.
- 칩 width 63 고정(placeholder), 실데이터에선 텍스트 hug 예상.

## 2. 영농 활동 필터 (`1247:23587`) — "영농 활동" 칩 대응

- 프레임 274 높이. 제목 "영농 활동".
- `selected-chip` 프레임(350×72): 2줄.
  - chip-list-1: 심기 / 물주기 / 거름·비료 / 병해충 방제
  - chip-list-2: 제초 / 가지·순 정리 / 수확 / 가공 / 기타
- 이 프레임의 칩 워딩(심기/물주기/거름·비료/병해충 방제/제초/가지·순 정리/수확/가공/기타,
  9개)은 **2026-07-13 확정 워딩으로 대체됨** → [작업 유형 확정 워딩](2026-07-13-record-work-type-labels.md)
  참조. 확정: **8종** (심기·물주기·**비료 주기**·**병해충 관리**·**잡초 관리**·가지·순 정리·
  수확·기타), `가공` 미채택, API enum과 1:1.
- 캡처 상태의 선택 칩: 심기 / 거름·비료 / 병해충 방제 (초록). 물주기/제초/가지·순
  정리/수확/가공/기타는 비선택(회색).

## 3. 작성 기간 필터 (`1247:23616`) — "기간" 칩 대응

- 프레임 258 높이. 제목 "작성 기간".
- `date-input` 2개 나란히 (시작/종료), 각 171×56, 사이 간격 8 (350 = 171+8+171).
  - field: cornerRadius 8, border `#e0e0e0`(`Color.Border.default`), 배경 `#ffffff`
  - 값 텍스트: Pretendard Medium 18, color `#1a1a1a` — 예시 "2026. 12. 12" / "2026. 12. 14"
  - trailing 아이콘: `icon/calendar_month` 24×24
  - 각 필드 하단 헬퍼 텍스트 슬롯 "메시지를 전달합니다." Pretendard Medium 15 color `#878787`
    (`Color.Text.muted`) — 실제로는 유효성/안내 메시지 자리, 기본은 숨김 추정
- 기존 `AppDateField`(date-input 컴포넌트, node 290:7089 계열)와 매칭.

---

## 칩 상태 규칙 (selected chip)

바텀시트 칩은 [기록 메인의 필터 칩](2026-07-13-record-main-default.md)과 **다른 스타일**:

| 상태 | fill | border | 텍스트 color | 텍스트 weight |
|---|---|---|---|---|
| **선택** | `#e4f8e3` | `#38c284` (1px) | `#27865c` | Pretendard **SemiBold** 15 |
| 비선택 | `#f3f3f3` | 없음 | `#4f4f4f` | Pretendard Medium 15 |

- 선택 칩 = `AppChip(style: .solidPastel, isSelected: true)`와 매핑되나 **fill 값 차이 주의**:
  - Figma 선택 배경 `#e4f8e3` = `Color.Object.primarySubtle` (DESIGN_SYSTEM_HANDOFF 기준)
  - 현재 `AppChip.fillColor(.solidPastel, true)`도 `Color.Object.primarySubtle` → **일치** ✅
  - border `#38c284` = `Color.Border.primary` → `AppChip.borderColor(.solidPastel, true)`와 **일치** ✅
  - 텍스트 `#27865c` = `Color.Text.primary` → `AppChip` 선택 텍스트도 `Color.Text.primary` → **일치** ✅
  - **단 weight 차이**: Figma 선택 칩은 SemiBold(600)인데 `AppChip`은 `.labelMedium`(Medium). 선택 시 emphasized 처리 필요 여부 확인 (마이페이지 bottom-sheet 캡처와 동일 이슈일 수 있음).
- 비선택 칩 = fill `#f3f3f3`(`Color.Object.muted`) + border 없음 → `AppChip.fillColor(.solidPastel, false)`는
  `Color.Object.default`(#fff) + border `Color.Border.subtle`. **불일치** — 바텀시트 비선택 칩은
  `.solid` 스타일(muted 배경, border 없음)에 가까움. 즉 바텀시트 칩은
  `AppChip(style: .solid)` 계열(비선택 muted / 선택은 pastel)로 조합이 섞여 보임 →
  **구현 시 칩 스타일 재확인 필요** (아래 충돌 항목).

---

## 기존 디자인 시스템 컴포넌트 매핑 후보

- **바텀시트 컨테이너 + grabber** → 앱 공통 바텀시트 래퍼 필요. 마이페이지
  [board-select-bottom-sheet](../mypage/2026-07-13-mypage-board-select-bottom-sheet.md)와
  동일 grabber/완료버튼 구조 — 공통 컴포넌트 승격 후보(반복 확인됨).
- **완료 버튼** → `AppButton` (fill `#343434`=Object.bold, cornerRadius 12, 350×56).
  기존 버튼 variant 중 neutral/bold 계열과 매핑 확인 필요.
- **칩** → `AppChip` (위 상태 규칙의 불일치 해소 후).
- **date-input** → `AppDateField` (`.sheet` + graphical picker, 기존 구현 재사용).

## Figma ↔ 디자인 시스템 / 제품 충돌·확인 필요

1. **바텀시트 스크림 토큰**: `#1a1a1a` @ 64% 공통값을 디자인 시스템에 도입할지
   (scrim 전용 시맨틱 vs `Color.Text.default.opacity(0.64)`).
2. **바텀시트 칩 비선택 스타일**: 비선택 배경이 `#f3f3f3`(muted)+border없음이라
   현재 `AppChip(.solidPastel, false)`(흰 배경+연한 border)와 다름. 바텀시트용
   칩은 `.solid`↔`.solidPastel` 혼합이거나 신규 스타일 필요 — 구현 전 결정.
3. **선택 칩 weight**: Figma는 선택 시 SemiBold, 코드 `AppChip`은 Medium 고정.
4. **영농 활동 9개 카테고리**가 BR-RECORD-004 및 백엔드 enum과 일치하는지 대조.
5. **복수 선택**: 작물/영농 활동 필터는 다중 선택. 기간은 시작~종료 range.
   필터 상태 모델(선택 집합)과 "완료" 탭 시 목록 재조회 흐름 정의 필요.
6. **date-input 헬퍼 "메시지를 전달합니다."**: 기본 노출 여부(placeholder인지 실제
   검증 메시지 자리인지) — 시작>종료 등 유효성 규칙 확인.
