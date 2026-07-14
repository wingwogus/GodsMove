# 홈 -> 정책 리스트

- Captured: 2026-07-14
- Source: `mcp__TalkToFigma__get_selection` → `read_my_design` → `export_node_as_image` (PNG, scale 2)
- Figma node: `1172:18031`, frame name `홈 -> 정책 리스트`
- Frame size: 390 × 844
- Background: `#ffffff`
- 진입 지점: 홈 default의 "오늘의 추천 정책" 섹션 title row `icon/arrow_forward_ios` 탭으로 추정 (전체 목록)
- 사용자 요청으로 정책 상세 대신 먼저 캡처된 화면(선택된 프레임이 리스트였음). 정책 상세는 별도 캡처 예정
- PNG: 인라인 이미지로만 확인, 디스크 미저장

## 구조 개요

```
홈 -> 정책 리스트 (390×844, #ffffff)
├─ Status Bar
├─ top-app-bar (390×60) — back 아이콘 + "정책 리스트" 타이틀 (trailing 빈 슬롯, 날씨 상세와 동일 variant)
├─ chip-list (390×60, 배경 #fafafa) — 카테고리 칩 11개(전체 + 카테고리 10종), 가로 스크롤
├─ sort (390×48) — 정렬 드롭다운 "추천순" + icon/keyboard_arrow_down
└─ list (390×736, 세로 스크롤) — list row × N (각 390×169, 구분선 #e0e0e0)
```

## Section 1 — top-app-bar

- 날씨 상세와 동일한 서브페이지 top-app-bar variant: `icon/arrow_back_ios_new` + 중앙 타이틀(Pretendard SemiBold 28 / `#242428`) + trailing 빈 48×48
- 타이틀: "정책 리스트"

## Section 2 — chip-list (카테고리 필터, 390×60, 배경 `#fafafa`)

가로 스크롤 칩 11개, 순서대로:

1. **전체** — 선택 상태(배경 `#343434` 다크, 텍스트 흰색 SemiBold 15)
2. 지원금
3. 융자·금융
4. 시설·장비
5. 교육
6. 복지
7. 인증
8. 판로
9. 창업
10. 환경·인프라
11. 기타

- 미선택 칩: 배경 흰색, border `#f3f3f3`, 텍스트 `#4f4f4f` Medium 15
- 모두 pill 형태(radius 1000)
- **사용자 확인 카테고리 10종과 Figma 칩 순서 100% 일치**: 지원금/융자·금융/시설·장비/교육/복지/인증/판로/창업/환경·인프라/기타

## Section 3 — sort (정렬 드롭다운, 390×48)

- 우측 정렬, 텍스트 "추천순" (Pretendard Medium 15 / `#4f4f4f`) + `icon/keyboard_arrow_down` (24×24)
- ⚠️ **Figma 노드 이름은 "최신순"인데 실제 표시 텍스트(`characters`)는 "추천순"** — 레이어 이름이 갱신 안 된 것으로 추정, 실제 값은 "추천순" 기준으로 구현. 정렬 옵션 종류(추천순/최신순/마감임박순 등)는 드롭다운 오픈 상태 미캡처로 확정 안 됨

## Section 4 — list (list row, 각 390×169)

리스트 아이템 반복 구조 (전부 placeholder 텍스트, N개 확인 — 스크롤 영역이라 실제 개수 미확정):

- `title` 영역: "타이틀"(Pretendard SemiBold 24 / `#1a1a1a`) + "기관"(Medium 16 / `#4f4f4f`)
- `info` 영역: 좌측 라벨 컬럼(대상자/지원내용/접수기간, 각 Medium 16 / `#878787`) + 우측 값 컬럼(캡션×3, 각 Medium 16 / `#1a1a1a`)
- 구분선 `#e0e0e0` (list 인스턴스의 stroke)
- 홈 default의 "오늘의 추천 정책" 카드(D-day 뱃지 + 원형 화살표 버튼)와는 다른 레이아웃 — 리스트 row는 뱃지/버튼 없이 텍스트 정보 위주

## 디자인 시스템 대조 후보

- `AppChip` — 카테고리 필터 11개, 선택(dark)/미선택(outline) 2-state. Record 탭 필터 칩과 동일 패턴(선택 시 색만 다름 — Record는 초록 아웃라인, 여기는 dark 배경 선택)일지 코드 대조 필요
- `AppTopAppBar` (back variant) — 날씨 상세와 동일
- list row는 `AppListItem`과 다른 전용 레이아웃(라벨-값 3행 그리드) — 신규 컴포넌트 후보, Record/Community의 `AppListItem`과 구분되는 "정책 카드형 리스트"

## 미해결 질문 / 백엔드 협의 필요

1. **카테고리 API enum**: 지원금/융자·금융/시설·장비/교육/복지/인증/판로/창업/환경·인프라/기타 10종이 백엔드 정책 API의 카테고리 enum과 1:1 일치하는지 확인 필요 (별도 정책 API 존재 여부 자체도 확인 필요 — Swagger에 없으면 미비)
2. **정렬 옵션**: "추천순" 외 다른 정렬 옵션(최신순/마감임박순 등) 목록 미확정 — 드롭다운 오픈 상태 캡처 필요
3. **list row 필드 매핑**: 타이틀/기관/대상자/지원내용/접수기간이 정책 API 응답 필드와 어떻게 매핑되는지
4. **정책 상세 화면**: 아직 미캡처 — 이 리스트 row 탭 시 이동하는 상세 화면 별도 캡처 필요
5. **빈 상태**: 카테고리 필터링 결과 0건일 때 UI 미캡처
6. **페이지네이션**: 리스트가 커서 기반 무한 스크롤인지(Record/Community 패턴 재사용 가능성) 확인 필요

## 정책 상세 (사용자 확정, 2026-07-14)

**정책 상세는 자체 UI가 없다.** 정책 리스트 row 탭 시 외부 웹 링크(정책 공고 원문)로
이동하는 것이 전부 — 네이티브 상세 화면을 구현하지 않는다. list row 탭 액션은
`SFSafariViewController` 또는 `openURL`로 외부 브라우저/인앱 브라우저 이동만
구현하면 된다. 상세 캡처는 진행하지 않음.

## 다음 캡처 후보

- 정렬 드롭다운 오픈 상태
- 카테고리 필터 적용된 상태(칩 선택 변경)
- 빈 목록 / 로딩 / 오류 상태

(위 상태들은 필수는 아니며, 기존 디자인 시스템 패턴으로 구현 계획에서 정의 가능)
