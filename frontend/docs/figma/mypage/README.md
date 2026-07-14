# My Page Figma Captures

이 폴더는 TalkToFigma MCP로 수집한 마이페이지·프로필 화면의 Figma 캡처와
구현 준비 메모를 저장한다. 화면을 모두 수집한 뒤 기존 SwiftUI 화면, 디자인
시스템, 제품 규칙, 배포 Swagger를 함께 대조해 구현 계획을 확정한다.

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

- [프로필 메인 / default](2026-07-13-mypage-profile-main-default.md) — 구조와
  텍스트 캡처 완료 (요약 모드: 작물 3개 + `외 n종`)
- [프로필 메인 / 작물 뱃지 전체 공개](2026-07-13-mypage-profile-crops-expanded.md)
  — `1247:17757`, 작물 뱃지 전체(8개) 2줄 노출, 카드 높이 311로 가변
- [바텀시트 / 게시판 선택 시](2026-07-13-mypage-board-select-bottom-sheet.md)
  — `1247:18048`, 작물 선택 칩(진행중/기타) + `완료` 버튼
- [프로필 수정 / 기본 정보](2026-07-13-mypage-profile-edit-basic.md)
  — `1247:18133`, 폼 필드(text-input/date/자격 세그먼트) + `저장` 버튼
- [프로필 수정 / 농업 정보](2026-07-13-mypage-profile-edit-farm.md)
  — `1247:17987`, 등록한 밭(농장) 카드 목록 + 추가/삭제
- [DS 컴포넌트 / setting-card (밭 카드)](2026-07-13-mypage-setting-card-component.md)
  — `1088:16697`, `selected` variant(true/false), 디자인 시스템 승격 대상

구현 계획은 [구현 계획서](2026-07-13-mypage-implementation-plan.md) 참조.

Claude 또는 다른 AI 세션에서 캡처와 구현 작업을 이어갈 때는
[HANDOFF.md](HANDOFF.md)를 인수인계 문서로 사용한다.

## Capture Flow

1. Figma에서 마이페이지 프레임 또는 상태를 하나 선택한다.
2. `캡쳐: 마이페이지 / <화면명> / <상태>` 형식으로 전달한다.
3. 노드 ID, 화면 크기, 상태, 구조, 텍스트 스타일, 색상, 주요 치수와 PNG를
   이 폴더에 기록한다.
4. Figma와 제품 규칙 또는 디자인 시스템이 충돌하면 구현 전에 문서에 분리해
   기록한다.
5. 모든 화면 수집이 끝나면 `마이페이지 디자인 수집 끝`이라고 알린다.
6. 전체 상태 행렬과 현재 코드/API 준비 상태를 기준으로 구현 계획을 작성한다.

## Capture Checklist

- 프로필 메인: 기본, 좋아요 누른 글 탭, 게시판 필터 선택
- 콘텐츠: 로딩, 빈 목록, 오류·재시도, 페이지 추가 로딩
- 프로필 정보: 기본 이미지, 사용자 이미지, 작물 없음/다수, 긴 닉네임·지역
- 설정·알림 진입 상태와 실제 라우팅 대상
- 작은 기기: iPhone SE 2/3에서 프로필 카드, 탭, 필터, 목록, 하단 내비게이션

Figma에 없는 런타임 상태는 기존 디자인 시스템 패턴을 사용하되 구현 계획에
명시한다.
