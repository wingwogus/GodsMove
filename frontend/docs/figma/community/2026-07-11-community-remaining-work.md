# Community Remaining Work

- 작성일: 2026-07-11
- 기준 브랜치: `feat/presentation-skeleton-front`
- 범위: 커뮤니티 메인, 게시물 작성, 영농 기록 첨부 picker의 Figma 캡처 기반 presentation 적용 이후 남은 작업

## 현재 반영된 범위

이번 구현에서 반영한 Figma 캡처 범위는 다음과 같다.

- 커뮤니티 메인: 일반/Q&A 탭, 작물 칩 선택, 게시글 리스트, 글쓰기 플로팅 버튼
- 게시물 작성: default, 필수값 입력 완료, 전체값 입력 완료, 제목/내용 글자수 초과 상태
- 게시물 작성: 이미지 첨부 슬롯, 질문으로 올리기 토글, 영농 기록 첨부 영역
- 영농 기록 첨부 picker: default, 작물 카테고리 선택, 검색 진행, 기록 선택 상태의 UI shell
- iPhone SE 2/3 최소 사용성을 고려한 스크롤, safe area bottom action, validation text 배치

## 남은 작업 요약

| 우선순위 | 항목 | 상태 | 메모 |
| --- | --- | --- | --- |
| P0 | 커뮤니티 상세 Figma 캡처 | 남음 | 현재 상세 화면은 기존 코드 기반 보정만 가능하며, 최신 Figma parity는 주장할 수 없음 |
| P0 | 커뮤니티 수정 Figma 캡처 | 남음 | 수정 화면 default/validation/submitting 상태가 필요함 |
| P0 | 영농 기록 첨부 실제 데이터 연결 | 남음 | 현재 picker는 presentation shell이며 `createPost` 요청에는 `farmingRecordId`가 아직 연결되지 않음 |
| P1 | 메인 feed runtime state | 남음 | loading, empty, error, retry, pagination, pull-to-refresh 상태의 디자인 확정 필요 |
| P1 | 작성 화면 runtime state | 남음 | keyboard focus, submitting, image upload failure, record no-result 상태 필요 |
| P1 | SE 2/3 실기기 또는 simulator QA | 남음 | 하단 탭, 플로팅 버튼, keyboard, bottom action 가림 여부 확인 필요 |
| P2 | 검색/알림/정렬 opened state | 남음 | top app bar icon의 진입 화면 또는 sheet/menu 상태가 아직 Figma 캡처에 없음 |
| P2 | 이미지 asset 정책 | 남음 | Figma image fill은 local asset이 아니므로 placeholder, remote image, upload preview 정책 정리 필요 |

## 구현상 주의할 점

### 영농 기록 첨부

현재 작성 화면의 영농 기록 picker는 Figma 시각 구조를 확인하기 위한 shell이다.

- 샘플 record row로 선택 UI를 보여준다.
- 선택한 record는 작성 화면에 표시된다.
- 아직 `CommunityComposeViewModel.createPost()`의 `farmingRecordId`에는 연결하지 않는다.

다음 단계에서 먼저 결정해야 한다.

- 영농 기록 목록을 어떤 repository/local store에서 가져올지
- crop filter와 search를 클라이언트에서 처리할지 API에 위임할지
- 오프라인 상태에서 최근 record를 어떻게 보여줄지
- record 선택을 게시글 작성 API의 `farmingRecordId`로 보낼 수 있는지

### 커뮤니티 상세/수정

현재 캡처 폴더에는 상세와 수정 화면의 최신 Figma 문서가 없다.

상세/수정 작업을 계속하려면 다음 캡처를 먼저 추가한다.

- `커뮤니티 상세 / default`
- `커뮤니티 상세 / 댓글 입력 focus`
- `커뮤니티 상세 / 댓글 empty`
- `커뮤니티 수정 / default`
- `커뮤니티 수정 / validation`
- `커뮤니티 수정 / submitting`

캡처 전에는 기존 SwiftUI 화면을 유지하되, Figma 최종 디자인 반영 완료로 기록하지 않는다.

## QA 체크리스트

다음 작업자는 구현 후 아래를 확인한다.

- [ ] iPhone SE 2/3에서 bottom tab과 글쓰기 버튼이 리스트 마지막 row를 가리지 않는다.
- [ ] iPhone SE 2/3에서 작성 화면의 `완료` 버튼이 keyboard에 가려지지 않는다.
- [ ] 제목/내용 validation 문구와 counter가 겹치지 않는다.
- [ ] 기록 picker에서 마지막 record가 bottom action에 가려지지 않는다.
- [ ] 이미지 10개 상태에서 horizontal scroll과 삭제 버튼 tap target이 유지된다.
- [ ] feed loading, empty, error 상태가 기존 앱 패턴과 맞는다.
- [ ] Dynamic Type을 크게 했을 때 주요 액션과 필수 정보가 잘리지 않는다.

## 다음 추천 순서

1. 상세/수정 Figma 프레임을 추가 캡처한다.
2. 영농 기록 첨부 데이터 출처와 `farmingRecordId` 연결 방식을 확정한다.
3. 커뮤니티 메인/작성 화면을 SE 2/3, iPhone 13 이상 simulator에서 QA한다.
4. runtime state 디자인이 없는 부분은 기존 앱 패턴으로 임시 구현하고 문서에 명시한다.
5. 상세/수정 화면을 Figma 캡처 기준으로 별도 PR 또는 커밋에서 반영한다.
