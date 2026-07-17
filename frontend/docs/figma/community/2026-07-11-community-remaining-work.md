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
| P0 | 커뮤니티 상세 Figma 캡처 | 완료 (2026-07-17) | `커뮤니티 / 게시물 내 영농일지 포함 시` 캡처로 상세 레이아웃 확정. 상세 문서: [2026-07-17-community-detail-with-farming-record.md](2026-07-17-community-detail-with-farming-record.md) |
| P0 | 커뮤니티 수정 Figma 캡처 | 남음 | 수정 화면 default/validation/submitting 상태가 필요함 |
| P0 | 영농 기록 첨부 실제 데이터 연결 | 완료 (2026-07-17) | picker가 `RecordRepository` 실데이터로 연결됨, `createPost`/`updatePost`에 `farmingRecordId` 전송, 상세 화면에 영농일지 카드 렌더링 |
| P1 | 메인 feed runtime state | 남음 | loading, empty, error, retry, pagination, pull-to-refresh 상태의 디자인 확정 필요 |
| P1 | 작성 화면 runtime state | 남음 | keyboard focus, submitting, image upload failure, record no-result 상태 필요 |
| P1 | SE 2/3 실기기 또는 simulator QA | 남음 | 하단 탭, 플로팅 버튼, keyboard, bottom action 가림 여부 확인 필요 |
| P2 | 검색/알림/정렬 opened state | 남음 | top app bar icon의 진입 화면 또는 sheet/menu 상태가 아직 Figma 캡처에 없음 |
| P2 | 이미지 asset 정책 | 남음 | Figma image fill은 local asset이 아니므로 placeholder, remote image, upload preview 정책 정리 필요 |

## 구현상 주의할 점

### 영농 기록 첨부 (2026-07-17 완료)

작성 화면의 영농 기록 picker가 실제 데이터에 연결되었다.

- `FarmingRecordPickerView`/`FarmingRecordPickerState`가 `RecordRepository`
  (`fetchRecords`/`fetchActiveCrops`)로 실제 영농일지를 불러온다.
- crop 필터는 서버 재조회(`RecordFilter.cropIds`), 검색어는 이미 로드된
  페이지 위 클라이언트 필터.
- 영농일지에는 `title` 필드가 없어 카드 제목은 `workType.label`(활동 유형)을
  사용, 캡션은 `memoPreview`/`memo`.
- `CommunityComposeViewModel.submit()`이 선택된 record의 `id`를
  `farmingRecordId`로 전송한다.
- `CommunityDetailViewModel.load()`가 `detail.farmingRecordId`가 있을 때
  `RecordRepository.fetchDetail(id:)`를 추가 호출해 상세 화면에 영농일지
  카드(`CommunityFarmingRecordCard`)를 렌더링한다 — `PostDetailResponse`는
  id만 주고 요약을 embed하지 않기 때문.

남은 것: 영농일지 카드 탭 시 해당 기록 상세로 이동하는 동작(현재 Figma
캡처에 없어 비인터랙티브로 유지), SE 2/3 실기기 QA.

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
