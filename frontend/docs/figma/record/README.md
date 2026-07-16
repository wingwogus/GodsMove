# Record (영농 기록) Figma Captures

이 폴더는 TalkToFigma MCP로 수집한 영농기록(Record) 탭 화면의 Figma 캡처와
구현 준비 메모를 저장한다. 화면을 모두 수집한 뒤 기존 SwiftUI 화면, 디자인
시스템, `docs/Business Rule.md`의 BR-RECORD-*/BR-VOICE-*, 배포 Swagger를
함께 대조해 구현 계획을 확정한다.

## MCP Connection

- TalkToFigma channel: `chamchamcham`
- Figma에서 화면 또는 상태 프레임 하나만 선택한다.
- 캡처 순서: `get_selection` → `read_my_design` → `scan_text_nodes` →
  `export_node_as_image`
- 문서 전체 조회는 사용하지 않는다.
- PNG 참고: Claude Code에서 `export_node_as_image`는 결과를 인라인 이미지로만
  반환하고 디스크 파일로 저장하지 않는다. 구조·텍스트·색상은 `read_my_design`
  값으로 정밀 기록하고, PNG 파일이 필요하면 Figma에서 수동 Export(2x)한다.

## Captures

- [기록 메인 / default](2026-07-13-record-main-default.md) — `1247:23900`,
  필터 칩 3개(작물/영농 활동/기간) + list row(대형, `AppListItem(size: .large)`와
  거의 일치) + FAB + 하단 nav-bar
- [기록 (필터 사용, 바텀시트)](2026-07-13-record-filter-bottom-sheets.md) —
  `1247:23970`(진행중인 작물) / `1247:23587`(영농 활동 9종) / `1247:23616`(작성 기간
  date-input 2개). 복수 선택 + 공통 딤드 규칙 `#1a1a1a`@64% 기록
- [기록 메인 - 기록 버튼 탭 시](2026-07-13-record-main-record-button-tapped.md) —
  `1247:23930`, FAB 탭 시 딤 + 음성/텍스트/닫기 스피드다이얼. 활성 필터 칩(초록
  아웃라인 + 카운트) 및 딤 `#1a1a1a`@64% 실프레임 확인
- [텍스트로 기록하기 / default](2026-07-13-record-text-compose-default.md) —
  `1247:23161`, 영농일지 텍스트 작성 폼(날짜/날씨 자동/농지·작물 드롭다운/작업 유형/
  메모 0-500/사진 0-5/완료). POST `SaveRecordRequest` 대응
- [작업 유형(workType) 확정 워딩](2026-07-13-record-work-type-labels.md) —
  심기/물주기/비료 주기/병해충 관리/잡초 관리/가지·순 정리/수확/기타 (8종, API enum 1:1)
- [텍스트로 기록하기 / 심기 - 1(씨앗 심기)](2026-07-13-record-text-compose-planting-1-seed.md)
  — `1247:23381`, 작업="심기" 선택 시 상세 추가(심은 방법 씨앗/모종 2택 + 심은 씨앗량 g).
  `PlantingDetailRequest` 대응. workType별 동적 상세 폼의 첫 사례
- [텍스트로 기록하기 / 심기 - 2(모종 심기)](2026-07-13-record-text-compose-planting-2-seedling.md)
  — `1247:23417`, 모종 심기 선택 시 심은 갯수(주) + **모종 번식법** 드롭다운. 재배법-번식법
  2단 구조 스펙(씨앗/모종 → 꺾꽂이·접붙이기·휘묻이·포기나누기·조직 배양·시판 구매) 전사 포함
- [텍스트로 기록하기 / 물주기](2026-07-13-record-text-compose-watering.md) —
  `1247:23454`, 물주기 선택 시 진행 방식 + 물의 양 드롭다운 (`WateringDetailRequest`)
- [텍스트로 기록하기 / 비료 주기](2026-07-13-record-text-compose-fertilizing.md) —
  `1247:23481`, 비료 주기 선택 시 사용 비료 + 사용량/단위 + 진행 방식 (`FertilizingDetailRequest`)
- [텍스트로 기록하기 / 병해충 관리](2026-07-13-record-text-compose-pest-control.md) —
  `1247:23511`, 사용 농약 + 농약 사용량/단위(g·ml) + 총 살포량 + 대상 병해충 (`PestControlDetailRequest`)
- [텍스트로 기록하기 / 잡초 관리](2026-07-13-record-text-compose-weeding.md) —
  `1247:23542`, 진행 방식 1개(손으로 뽑기/예초기/멀칭/제초제 = `WeedingDetailRequest`, enum 1:1)
- [텍스트로 기록하기 / 가지·순 정리](2026-07-13-record-text-compose-pruning.md) —
  `1247:23186`, **상세 필드 없음**(default와 동일). API에 `pruning` 상세 객체 없음 — 일치
- [텍스트로 기록하기 / 수확](2026-07-13-record-text-compose-harvest.md) —
  `1247:23264`, 재배 기간 + 수확량(잘 모르겠음) + 수확 부위 + 최종 수확 완료 토글
  (`HarvestDetailRequest`). 충돌 C-14~C-16(harvestSource 누락 등)
- [텍스트로 기록하기 / 기타](2026-07-13-record-text-compose-etc.md) —
  상세 필드 없음(가지·순 정리와 동일, default 폼). 캡처 없이 구두 확정. **workType 8종 분기 완료**
- [텍스트로 기록하기 / 입력 검증(에러) 케이스](2026-07-13-record-text-compose-error-cases.md) —
  참고 이미지 기반. 2필드 1영역(진행 작물/작업 내용)의 조합 에러 문구 + red border 변형 규칙,
  500자 초과 문구. 확정 워딩 목록 포함
- [음성으로 기록하기 / default](2026-07-16-record-voice-compose-default.md) —
  프롬프트+사용자 말풍선(`AppChatBubble`) + 96pt 원형 마이크 버튼(idle) + 완료(disabled).
  색/폰트 전부 DS 토큰과 일치, 충돌 없음. 정적 목업 화면(`RecordVoiceComposeView`) 구현 완료,
  녹음 상태 머신/STT/AI 구조화는 계획 단계에서 정의
- ⚠️ [Figma/스펙 ↔ 백엔드 충돌 트래킹](2026-07-13-record-backend-conflicts.md) —
  C-1~C-6 (필터 복수선택/시판 구매 enum/필수 여부/날씨 온도 등 백엔드 협의 항목)

**수집 완료 (2026-07-13):** 기록 메인/필터/FAB 스피드다이얼/텍스트 작성 default + workType
8종 분기 + 에러 케이스. 종합 계획은
[구현 계획서](2026-07-13-record-implementation-plan.md), 백엔드 협의는
[충돌 트래킹](2026-07-13-record-backend-conflicts.md). (음성/리포트/상세·수정·삭제는 미수집.)

## Capture Flow

1. Figma에서 영농기록 프레임 또는 상태를 하나 선택한다.
2. `캡쳐: 기록 / <화면명> / <상태>` 형식으로 전달한다.
3. 노드 ID, 화면 크기, 상태, 구조, 텍스트 스타일, 색상, 주요 치수를 이 폴더에
   기록한다.
4. Figma와 제품 규칙(BR-RECORD-*, BR-VOICE-*) 또는 디자인 시스템이 충돌하면
   구현 전에 문서에 분리해 기록한다.
5. 모든 화면 수집이 끝나면 `기록 디자인 수집 끝`이라고 알린다.
6. 전체 상태 행렬과 현재 코드/API 준비 상태를 기준으로 구현 계획을 작성한다.

## Capture Checklist

- 기록 메인: default(완료), 필터 적용 상태(작물/영농 활동/기간 선택됨), 필터
  드롭다운 오픈 상태
- 콘텐츠: 로딩, 빈 목록(기록 0건), 오류·재시도, 페이지 추가 로딩
- list row: 사진 없음 상태, 다년생 작물(BR-RECORD-008) 관련 배지 변형
- 리포트 탭 (현재 폴더는 "기록" 탭만 대상, 리포트는 별도 캡처 필요)
- 기록 작성/수정/삭제 플로우 (FAB 진입 지점, Voice/Text 모드 선택 — BR-VOICE-*)
- 작은 기기: iPhone SE 2/3에서 필터 칩, list row, FAB, 하단 내비게이션 겹침 여부

Figma에 없는 런타임 상태는 기존 디자인 시스템 패턴을 사용하되 구현 계획에
명시한다.

Claude 또는 다른 AI 세션에서 캡처와 구현 작업을 이어갈 때는
[HANDOFF.md](HANDOFF.md)를 인수인계 문서로 사용한다.
