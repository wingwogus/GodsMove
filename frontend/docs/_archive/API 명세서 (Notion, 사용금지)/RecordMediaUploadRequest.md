<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# RecordMediaUploadRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- mediaType: enum, required. IMAGE.
- fileUrl: string, required. 업로드 완료된 파일 URL 또는 스토리지 키.
### Rule
사진은 선택 입력이며 사진만 존재하는 영농일지는 저장할 수 없다. 이미지 업로드 실패는 영농일지 저장을 막지 않는다.
