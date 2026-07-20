<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# KakaoLoginRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "idToken": "kakao-oidc-id-token",
  "nonce": "client-generated-raw-nonce",
  "kakaoAccessToken": "kakao-access-token"
}
```
### Field
- `idToken`: string, required. Kakao OIDC ID token.
- `nonce`: string, required. 앱에서 생성한 raw nonce.
- `kakaoAccessToken`: string, optional. Kakao OIDC userinfo 조회에 사용한다. 값이 없으면 로그인 검증은 가능하지만 이름, 전화번호, 생년월일 prefill은 제한된다.
### Parameter
None
### Header
None
### Query
None
