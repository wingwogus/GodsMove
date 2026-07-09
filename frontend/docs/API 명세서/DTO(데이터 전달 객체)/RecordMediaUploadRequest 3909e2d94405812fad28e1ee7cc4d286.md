# RecordMediaUploadRequest

API 분류: API Request
태그: [영농일지] 기록

## Fields

- mediaType: enum, required. IMAGE.
- fileUrl: string, required. 업로드 완료된 파일 URL 또는 스토리지 키.

## Rule

사진은 선택 입력이며 사진만 존재하는 영농일지는 저장할 수 없다. 이미지 업로드 실패는 영농일지 저장을 막지 않는다.